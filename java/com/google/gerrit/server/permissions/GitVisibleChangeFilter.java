// Copyright (C) 2022 The Android Open Source Project
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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.git.SearchingChangeCacheImpl;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeNotes.Factory.ChangeNotesResult;
import com.google.gerrit.server.query.change.ChangeData;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Repository;

/**
 * This class can tell efficiently if changes are visible to a user. It is intended to be used when
 * serving Git traffic on the Git wire protocol and in similar use cases when we need to know
 * efficiently if a (potentially large number) of changes are visible to a user.
 *
 * <p>The efficiency of this class comes from heuristical optimization:
 *
 * <ul>
 *   <li>For a low number of expected checks, we check visibility one-by-one.
 *   <li>For a high number of expected checks and settings where the change index is available, we
 *       load the N most recent changes from the index and filter them by visibility. This is fast,
 *       but comes with the caveat that older changes are pretended to be invisible.
 *   <li>For a high number of expected checks and settings where the change index is unavailable, we
 *       scan the repo and determine visibility one-by-one. This is *very* expensive.
 * </ul>
 */
interface GitVisibleChangeFilter {
  /**
   * Returns {@code true} if the {@code changeId} in repository {@code repo} is visible to the user.
   * Might pretend that old changes are not visible (even if they are).
   */
  boolean isVisible(Change.Id changeId) throws PermissionBackendException;

  /**
   * Returns the {@code BranchNameKey} for {@code changeId}. If not cached, computes *all* visible
   * changes and caches them before returning this specific change. If not visible or not found,
   * returns {@link Optional#empty}.
   */
  Optional<BranchNameKey> getDestination(Change.Id changeId) throws PermissionBackendException;

  int CHANGE_LIMIT_FOR_DIRECT_FILTERING = 5;

  /**
   * Creates a new {@link GitVisibleChangeFilter} based on the availability of the change index and
   * the amount of changes we expect to filter.
   */
  static GitVisibleChangeFilter create(
      @Nullable SearchingChangeCacheImpl changeCache,
      ChangeNotes.Factory changeNotesFactory,
      Project.NameKey projectName,
      PermissionBackend.ForProject forProject,
      Repository repository,
      long hintNumberOfChangesToBeFiltered) {
    if (hintNumberOfChangesToBeFiltered < CHANGE_LIMIT_FOR_DIRECT_FILTERING) {
      return new IndividualPermissionFilter(changeNotesFactory, forProject, projectName);
    } else if (changeCache != null) {
      return new SearchingPermissionFilter(changeCache, forProject, projectName);
    } else {
      return new ScanningPermissionFilter(changeNotesFactory, repository, forProject, projectName);
    }
  }

  /** Check for change visibility individually by checking the user's permissions. */
  class IndividualPermissionFilter implements GitVisibleChangeFilter {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private final ChangeNotes.Factory changeNotesFactory;
    private final PermissionBackend.ForProject permissionBackendForProject;
    private final Project.NameKey projectName;

    IndividualPermissionFilter(
        ChangeNotes.Factory changeNotesFactory,
        PermissionBackend.ForProject permissionBackendForProject,
        Project.NameKey projectName) {
      this.changeNotesFactory = changeNotesFactory;
      this.permissionBackendForProject = permissionBackendForProject;
      this.projectName = projectName;
    }

    @Override
    public boolean isVisible(Change.Id changeId) throws PermissionBackendException {
      Optional<ChangeNotes> notes = notes(changeId);
      if (!notes.isPresent()) {
        return false;
      }
      return permissionBackendForProject.change(notes.get()).test(ChangePermission.READ);
    }

    @Override
    public Optional<BranchNameKey> getDestination(Change.Id changeId)
        throws PermissionBackendException {
      return notes(changeId).map(ChangeNotes::getChange).map(Change::getDest);
    }

    private Optional<ChangeNotes> notes(Change.Id cId) {
      try {
        return Optional.of(changeNotesFactory.createChecked(projectName, cId));
      } catch (Exception e) {
        logger.atFinest().withCause(e).log("Can't load Change notes for %s", cId);
      }
      return Optional.empty();
    }
  }

  /**
   * Search the change index for the most recent changes that the user can see. Pretend older
   * changes are invisible.
   */
  class SearchingPermissionFilter implements GitVisibleChangeFilter {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    @Nullable private final SearchingChangeCacheImpl changeCache;
    private final PermissionBackend.ForProject permissionBackendForProject;
    private final Project.NameKey projectName;
    private Map<Change.Id, BranchNameKey> visibleChanges;

    SearchingPermissionFilter(
        @Nullable SearchingChangeCacheImpl changeCache,
        PermissionBackend.ForProject permissionBackendForProject,
        Project.NameKey projectName) {
      this.changeCache = changeCache;
      this.projectName = projectName;
      this.permissionBackendForProject = permissionBackendForProject;
    }

    @Override
    public boolean isVisible(Change.Id changeId) throws PermissionBackendException {
      if (visibleChanges == null) {
        cacheVisibleChanges();
      }
      return visibleChanges.containsKey(changeId);
    }

    @Override
    public Optional<BranchNameKey> getDestination(Change.Id changeId)
        throws PermissionBackendException {
      if (visibleChanges == null) {
        cacheVisibleChanges();
      }
      return Optional.ofNullable(visibleChanges.get(changeId));
    }

    /**
     * Performs a search on the secondary change index. The number of results is sorted by updated
     * timestamp and is capped. This means this method will populate a list of the N most recent
     * changes visible by the user. It pretends older changes are not visible without checking them.
     *
     * <p>This is necessary because for repositories with >10k changes we can't efficiently filter
     * and advertise all refs. Using Git Protocol V2 with refs-in-wants is the proper way to not run
     * into this issue. If refs-in-wants is used, Gerrit only has to filter refs/changes that are of
     * interest to the user.
     */
    private void cacheVisibleChanges() throws PermissionBackendException {
      visibleChanges = new HashMap<>();
      try {
        for (ChangeData cd : changeCache.getChangeData(projectName)) {
          if (permissionBackendForProject.change(cd).test(ChangePermission.READ)) {
            visibleChanges.put(cd.getId(), cd.change().getDest());
          }
        }
      } catch (StorageException e) {
        logger.atSevere().withCause(e).log(
            "Cannot load changes for project %s, assuming no changes are visible", projectName);
        logger.atFinest().log("Visible changes: %s", visibleChanges.keySet());
        visibleChanges = null;
      }
    }
  }

  /** Scan NoteDb for changes visible to the user. This is very expensive. */
  class ScanningPermissionFilter implements GitVisibleChangeFilter {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private final ChangeNotes.Factory changeNotesFactory;
    private final Repository repository;

    private final PermissionBackend.ForProject permissionBackendForProject;
    private final Project.NameKey projectName;

    private Map<Change.Id, BranchNameKey> visibleChanges;

    ScanningPermissionFilter(
        ChangeNotes.Factory changeNotesFactory,
        Repository repository,
        PermissionBackend.ForProject permissionBackendForProject,
        Project.NameKey projectName) {
      this.changeNotesFactory = changeNotesFactory;
      this.repository = repository;
      this.projectName = projectName;
      this.permissionBackendForProject = permissionBackendForProject;
    }

    @Override
    public boolean isVisible(Change.Id changeId) throws PermissionBackendException {
      if (visibleChanges == null) {
        cacheVisibleChanges();
      }
      return visibleChanges.containsKey(changeId);
    }

    @Override
    public Optional<BranchNameKey> getDestination(Change.Id changeId)
        throws PermissionBackendException {
      if (visibleChanges == null) {
        cacheVisibleChanges();
      }
      return Optional.ofNullable(visibleChanges.get(changeId));
    }

    /** Get a list of all changes by scanning the repo. This is extremely slow. */
    private void cacheVisibleChanges() throws PermissionBackendException {
      visibleChanges = new HashMap<>();
      Stream<ChangeNotesResult> changes;
      try {
        changes = changeNotesFactory.scan(repository, projectName);
      } catch (IOException e) {
        logger.atSevere().withCause(e).log(
            "Cannot load changes for project %s, assuming no changes are visible", projectName);
        return;
      }

      for (Iterator<ChangeNotesResult> it = changes.iterator(); it.hasNext(); ) {
        ChangeNotesResult notesResult = it.next();
        if (notesResult.error().isPresent()) {
          logger.atWarning().withCause(notesResult.error().get()).log(
              "Failed to load change %s in %s", notesResult.id(), projectName);
          continue;
        }

        if (permissionBackendForProject.change(notesResult.notes()).test(ChangePermission.READ)) {
          visibleChanges.put(
              notesResult.notes().getChangeId(), notesResult.notes().getChange().getDest());
        }
      }
    }
  }
}
