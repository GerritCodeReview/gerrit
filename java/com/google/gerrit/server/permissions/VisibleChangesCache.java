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
import com.google.common.collect.Maps;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.git.SearchingChangeCacheImpl;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeNotes.Factory.ChangeNotesResult;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.lib.Repository;

/**
 * Gets and stores all of the visible by current user changes in the repository that are available
 * in the change index and cache.
 */
public class VisibleChangesCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  interface Factory {
    VisibleChangesCache create(ProjectControl projectControl);
  }

  @Nullable private final SearchingChangeCacheImpl changeCache;
  private final ProjectState projectState;
  private final ChangeNotes.Factory changeNotesFactory;
  private final PermissionBackend.ForProject permissionBackendForProject;

  private Map<Change.Id, ChangeNotes> visibleChanges;

  @Inject
  VisibleChangesCache(
      @Nullable SearchingChangeCacheImpl changeCache,
      PermissionBackend permissionBackend,
      ChangeNotes.Factory changeNotesFactory,
      @Assisted ProjectControl projectControl) {
    this.changeCache = changeCache;
    this.projectState = projectControl.getProjectState();
    this.permissionBackendForProject =
        permissionBackend.user(projectControl.getUser()).project(projectState.getNameKey());
    this.changeNotesFactory = changeNotesFactory;
  }

  /**
   * Returns {@code true} if the {@code changeId} in repository {@code repo} is visible to the user,
   * by looking at the cached visible changes.
   */
  public boolean isVisible(Repository repo, Change.Id changeId) throws PermissionBackendException {
    return cachedVisibleChanges(repo).containsKey(changeId);
  }

  /**
   * Returns the visible changes in the repository {@code repo}. If not cached, computes the visible
   * changes and caches them.
   */
  public Map<Change.Id, ChangeNotes> cachedVisibleChanges(Repository repo)
      throws PermissionBackendException {
    if (visibleChanges == null) {
      if (changeCache == null) {
        visibleChanges = visibleChangesByScan(repo);
      } else {
        visibleChanges = visibleChangesBySearch();
      }
      logger.atFinest().log("Visible changes: %s", visibleChanges.keySet());
    }
    return visibleChanges;
  }

  private Map<Change.Id, ChangeNotes> visibleChangesBySearch() throws PermissionBackendException {
    Project.NameKey project = projectState.getNameKey();
    try {
      Map<Change.Id, ChangeNotes> visibleChanges = new HashMap<>();
      for (ChangeData cd : changeCache.getChangeData(project)) {
        if (!projectState.statePermitsRead()) {
          continue;
        }
        try {
          permissionBackendForProject.change(cd).check(ChangePermission.READ);
          visibleChanges.put(cd.getId(), cd.notes());
        } catch (AuthException e) {
          // Do nothing.
        }
      }
      return visibleChanges;
    } catch (StorageException e) {
      logger.atSevere().withCause(e).log(
          "Cannot load changes for project %s, assuming no changes are visible", project);
      return Collections.emptyMap();
    }
  }

  private Map<Change.Id, ChangeNotes> visibleChangesByScan(Repository repo)
      throws PermissionBackendException {
    Project.NameKey p = projectState.getNameKey();
    ImmutableList<ChangeNotesResult> changes;
    try {
      changes = changeNotesFactory.scan(repo, p).collect(toImmutableList());
    } catch (IOException e) {
      logger.atSevere().withCause(e).log(
          "Cannot load changes for project %s, assuming no changes are visible", p);
      return Collections.emptyMap();
    }

    Map<Change.Id, ChangeNotes> result = Maps.newHashMapWithExpectedSize(changes.size());
    for (ChangeNotesResult notesResult : changes) {
      ChangeNotes notes = toNotes(notesResult);
      if (notes != null) {
        result.put(notes.getChangeId(), notes);
      }
    }
    return result;
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
