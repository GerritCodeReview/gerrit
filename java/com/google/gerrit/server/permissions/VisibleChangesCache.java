// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.permissions;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.git.SearchingChangeCacheImpl;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeNotes.Factory.ChangeNotesResult;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.Repository;

/**
 * Gets all of the visible by current user changes in the repository that are available in the
 * change index and cache.
 */
class VisibleChangesCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // If asked for this number of changes or less, we filter visibility by loading ChangeNotes.
  private static final int CHANGE_LIMIT_FOR_DIRECT_FILTERING = 5;

  interface Factory {
    VisibleChangesCache create(
        ProjectControl projectControl, Repository repository, long hintNumberOfChangesToBeFiltered);
  }

  /**
   * Depending on the expected amount of visibility checks, this class will decide how visibility is
   * checked.
   */
  enum FilteringStrategy {
    /**
     * Query the change index for the N most recent changes. This is fast, but the change needs to
     * be recent to be declared visible or the repo must have less changes than the index query
     * limit.
     */
    INDEX_BACKED_FILTERING,
    /**
     * Fallback in case the index is not available and we need to filter many changes. This is slow.
     */
    SCAN_ENTIRE_REPOSITORY,
    /**
     * In case we do not need to filter many changes, just check visibility for each change. This
     * means we can also check visibility for old changes.
     */
    PER_CHANGE_EVALUATION
  }

  @Nullable private final SearchingChangeCacheImpl changeCache;
  private final ProjectState projectState;
  private final ChangeNotes.Factory changeNotesFactory;
  private final PermissionBackend.ForProject permissionBackendForProject;
  private final FilteringStrategy filteringStrategy;

  private final Repository repository;
  private Map<Change.Id, BranchNameKey> visibleChanges;

  @Inject
  VisibleChangesCache(
      @Nullable SearchingChangeCacheImpl changeCache,
      PermissionBackend permissionBackend,
      ChangeNotes.Factory changeNotesFactory,
      @Assisted ProjectControl projectControl,
      @Assisted Repository repository,
      @Assisted long hintNumberOfChangesToBeFiltered) {
    this.changeCache = changeCache;
    this.projectState = projectControl.getProjectState();
    this.permissionBackendForProject =
        permissionBackend.user(projectControl.getUser()).project(projectState.getNameKey());
    this.changeNotesFactory = changeNotesFactory;
    this.repository = repository;

    if (hintNumberOfChangesToBeFiltered < CHANGE_LIMIT_FOR_DIRECT_FILTERING) {
      this.filteringStrategy = FilteringStrategy.PER_CHANGE_EVALUATION;
    } else if (changeCache != null) {
      this.filteringStrategy = FilteringStrategy.INDEX_BACKED_FILTERING;
    } else {
      this.filteringStrategy = FilteringStrategy.SCAN_ENTIRE_REPOSITORY;
    }
  }

  /**
   * Returns {@code true} if the {@code changeId} in repository {@code repo} is visible to the user.
   */
  public boolean isVisible(Change.Id changeId) throws PermissionBackendException {
    if (filteringStrategy == FilteringStrategy.PER_CHANGE_EVALUATION) {
      Optional<ChangeNotes> notes = notes(changeId);
      if (!notes.isPresent()) {
        return false;
      }
      return permissionBackendForProject.change(notes.get()).test(ChangePermission.READ);
    }

    cacheVisibleChanges();
    return visibleChanges.containsKey(changeId);
  }

  /**
   * Returns the {@code BranchNameKey} for {@code changeId}. If not cached, computes *all* visible
   * changes and caches them before returning this specific change. If not visible or not found,
   * returns {@code null}.
   */
  @Nullable
  public BranchNameKey getDestination(Change.Id changeId) throws PermissionBackendException {
    if (filteringStrategy == FilteringStrategy.PER_CHANGE_EVALUATION) {
      Optional<ChangeNotes> notes = notes(changeId);
      return notes.isPresent() ? notes.get().getChange().getDest() : null;
    }

    cacheVisibleChanges();
    return visibleChanges.get(changeId);
  }

  /**
   * Caches visible changes in the repository {@code repo}. If not cached yet, computes the visible
   * changes and caches them.
   */
  private void cacheVisibleChanges() throws PermissionBackendException {
    if (visibleChanges != null) {
      return;
    }
    if (filteringStrategy == FilteringStrategy.INDEX_BACKED_FILTERING) {
      visibleChangesBySearch();
    } else if (filteringStrategy == FilteringStrategy.SCAN_ENTIRE_REPOSITORY) {
      visibleChangesByFullScan();
    }
    logger.atFinest().log("Visible changes: %s", visibleChanges.keySet());
  }

  /**
   * Performs a search on the secondary change index. The number of results is sorted by updated
   * timestamp and is capped. This means this method will populate a list of the N most recent
   * changes visible by the user. It pretends older changes are not visible without checking them.
   *
   * <p>This is necessary because for repositories with >10k changes we can't efficiently filter and
   * advertise all refs. Using Git Protocol V2 with refs-in-wants is the proper way to not run into
   * this issue. If refs-in-wants is used, Gerrit only has to filter refs/changes that are of
   * interest to the user.
   */
  private void visibleChangesBySearch() throws PermissionBackendException {
    visibleChanges = new HashMap<>();
    Project.NameKey project = projectState.getNameKey();
    try {
      for (ChangeData cd : changeCache.getChangeData(project)) {
        if (!projectState.statePermitsRead()) {
          continue;
        }
        if (permissionBackendForProject.change(cd).test(ChangePermission.READ)) {
          visibleChanges.put(cd.getId(), cd.change().getDest());
        }
      }
    } catch (StorageException e) {
      logger.atSevere().withCause(e).log(
          "Cannot load changes for project %s, assuming no changes are visible", project);
    }
  }

  /** Get a list of all changes by scanning the repo. This is extremely slow. */
  private void visibleChangesByFullScan() throws PermissionBackendException {
    visibleChanges = new HashMap<>();
    Project.NameKey p = projectState.getNameKey();
    ImmutableList<ChangeNotesResult> changes;
    try {
      changes = changeNotesFactory.scan(repository, p).collect(toImmutableList());
    } catch (IOException e) {
      logger.atSevere().withCause(e).log(
          "Cannot load changes for project %s, assuming no changes are visible", p);
      return;
    }

    for (ChangeNotesResult notesResult : changes) {
      ChangeNotes notes = toNotes(notesResult);
      if (notes != null) {
        visibleChanges.put(notes.getChangeId(), notes.getChange().getDest());
      }
    }
  }

  @Nullable
  private ChangeNotes toNotes(ChangeNotesResult r) throws PermissionBackendException {
    if (r.error().isPresent()) {
      logger.atWarning().withCause(r.error().get()).log(
          "Failed to load change %s in %s", r.id(), projectState.getName());
      return null;
    }

    if (!projectState.statePermitsRead()) {
      return null;
    }

    if (permissionBackendForProject.change(r.notes()).test(ChangePermission.READ)) {
      return r.notes();
    }
    return null;
  }

  private Optional<ChangeNotes> notes(Change.Id cId) {
    try {
      return Optional.of(changeNotesFactory.createChecked(projectState.getNameKey(), cId));
    } catch (Exception e) {
      logger.atFinest().withCause(e).log("Can't load Change notes for %s", cId);
    }
    return Optional.empty();
  }
}
