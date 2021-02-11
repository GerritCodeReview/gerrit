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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.index.RefState;
import com.google.gerrit.server.git.SearchingChangeCacheImpl;
import com.google.gerrit.server.index.change.StalenessChecker;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeNotes.Factory.ChangeNotesResult;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

/**
 * Gets all of the visible by current user changes in the repository that are available in the
 * change index and cache.
 */
class VisibleChangesCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  interface Factory {
    VisibleChangesCache create(ProjectControl projectControl, Repository repository);
  }

  @Nullable private final SearchingChangeCacheImpl changeCache;
  private final ProjectState projectState;
  private final ChangeNotes.Factory changeNotesFactory;
  private final PermissionBackend.ForProject permissionBackendForProject;
  private final StalenessChecker stalenessChecker;

  private final Repository repository;
  private Map<Change.Id, Change> visibleChanges = new HashMap<>();
  private Map<Change.Id, ObjectId> metaIds = new HashMap<>();
  private Map<Change.Id, ObjectId> robotCommentsMetaIds = new HashMap<>();
  private Multimap<Change.Id, PatchSet> patchSets = HashMultimap.create();

  @Inject
  VisibleChangesCache(
      @Nullable SearchingChangeCacheImpl changeCache,
      PermissionBackend permissionBackend,
      ChangeNotes.Factory changeNotesFactory,
      StalenessChecker stalenessChecker,
      @Assisted ProjectControl projectControl,
      @Assisted Repository repository) {
    this.changeCache = changeCache;
    this.projectState = projectControl.getProjectState();
    this.permissionBackendForProject =
        permissionBackend.user(projectControl.getUser()).project(projectState.getNameKey());
    this.changeNotesFactory = changeNotesFactory;
    this.repository = repository;
    this.stalenessChecker = stalenessChecker;
  }

  /**
   * Returns {@code true} if the {@code changeId} in repository {@code repo} is visible to the user,
   * by looking at the cached visible changes.
   */
  public boolean isVisible(Change.Id changeId) throws PermissionBackendException {
    cachedVisibleChanges();
    return visibleChanges.containsKey(changeId);
  }

  /**
   * Returns the visible changes in the repository {@code repo}. If not cached, computes the visible
   * changes and caches them.
   */
  public Set<Change.Id> cachedVisibleChanges() throws PermissionBackendException {
    if (visibleChanges != null) {
      if (changeCache == null) {
        visibleChangesByScan();
      } else {
        visibleChangesBySearch();
      }
      logger.atFinest().log("Visible changes: %s", visibleChanges.keySet());
    }
    return visibleChanges.keySet();
  }

  public Change getChange(Change.Id changeId) throws PermissionBackendException {
    cachedVisibleChanges();
    return visibleChanges.get(changeId);
  }

  public ObjectId getMetaId(Change.Id changeId) throws PermissionBackendException {
    cachedVisibleChanges();
    return metaIds.get(changeId);
  }

  public ObjectId getRobotCommentsMetaId(Change.Id changeId) throws PermissionBackendException {
    cachedVisibleChanges();
    return robotCommentsMetaIds.get(changeId);
  }

  public Collection<PatchSet> getPatchSets(Change.Id changeId) throws PermissionBackendException {
    cachedVisibleChanges();
    return patchSets.get(changeId);
  }

  private void visibleChangesBySearch() throws PermissionBackendException {
    Project.NameKey project = projectState.getNameKey();
    try {
      for (ChangeData cd : changeCache.getChangeData(project)) {
        if (!projectState.statePermitsRead()) {
          continue;
        }
        try {
          permissionBackendForProject.change(cd).check(ChangePermission.READ);
          visibleChanges.put(cd.getId(), cd.change());
          Collection<RefState> refStates = stalenessChecker.parseStates(cd).values();
          for (RefState refState : refStates) {
            if (refState.ref().endsWith("robot-comments")) {
              if (!refState.id().equals(ObjectId.zeroId())) {
                robotCommentsMetaIds.put(cd.getId(), refState.id());
              }
            }
            if (refState.ref().endsWith("meta")) {
              metaIds.put(cd.getId(), refState.id());
            }
          }
          cd.patchSets().stream().forEach(ps -> patchSets.put(cd.getId(), ps));

        } catch (AuthException e) {
          // Do nothing.
        }
      }
    } catch (StorageException e) {
      logger.atSevere().withCause(e).log(
          "Cannot load changes for project %s, assuming no changes are visible", project);
    }
  }

  private void visibleChangesByScan() throws PermissionBackendException {
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
        visibleChanges.put(notes.getChangeId(), notes.getChange());
        metaIds.put(notes.getChangeId(), notes.getMetaId());
        if (notes.getRobotCommentNotes() != null
            && notes.getRobotCommentNotes().getMetaId() != null) {
          robotCommentsMetaIds.put(notes.getChangeId(), notes.getRobotCommentNotes().getMetaId());
        }
        notes.getPatchSets().values().stream()
            .forEach(ps -> patchSets.put(notes.getChangeId(), ps));
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

    try {
      permissionBackendForProject.change(r.notes()).check(ChangePermission.READ);
      return r.notes();
    } catch (AuthException e) {
      // Skip.
    }
    return null;
  }
}
