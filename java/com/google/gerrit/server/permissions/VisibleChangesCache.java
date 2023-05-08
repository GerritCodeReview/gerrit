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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.ChangesByProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.lib.Repository;

/**
 * Gets all the changes in a repository visible by the current user, potentially limited by index
 * search limits.
 */
class VisibleChangesCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  interface Factory {
    VisibleChangesCache create(ProjectControl projectControl, Repository repository);
  }

  private final ProjectState projectState;
  private final ChangesByProjectCache changesByProjectCache;
  private final PermissionBackend.ForProject permissionBackendForProject;

  private final Repository repository;
  private Map<Change.Id, BranchNameKey> visibleChanges;

  @Inject
  VisibleChangesCache(
      PermissionBackend permissionBackend,
      ChangesByProjectCache changesByProjectCache,
      @Assisted ProjectControl projectControl,
      @Assisted Repository repository) {
    this.projectState = projectControl.getProjectState();
    this.permissionBackendForProject =
        permissionBackend.user(projectControl.getUser()).project(projectState.getNameKey());
    this.changesByProjectCache = changesByProjectCache;
    this.repository = repository;
  }

  /**
   * Returns {@code true} if the {@code changeId} in repository {@code repo} is visible to the user,
   * by looking at the cached visible changes.
   */
  public boolean isVisible(Change.Id changeId) {
    return cachedVisibleChanges().containsKey(changeId);
  }

  /**
   * Returns the visible changes in the repository {@code repo}. If not cached, computes the visible
   * changes and caches them.
   */
  public Map<Change.Id, BranchNameKey> cachedVisibleChanges() {
    if (visibleChanges == null) {
      getVisibleChanges();
      logger.atFinest().log("Visible changes: %s", visibleChanges.keySet());
    }
    return visibleChanges;
  }

  /**
   * Returns the {@code BranchNameKey} for {@code changeId}. If not cached, computes *all* visible
   * changes and caches them before returning this specific change. If not visible or not found,
   * returns {@code null}.
   */
  @Nullable
  public BranchNameKey getBranchNameKey(Change.Id changeId) throws PermissionBackendException {
    return cachedVisibleChanges().get(changeId);
  }

  private void getVisibleChanges() {
    visibleChanges = new HashMap<>();
    if (!projectState.statePermitsRead()) {
      return;
    }
    Project.NameKey project = projectState.getNameKey();
    Map<Change.Id, ChangeData> changeDataById;
    try {
      changeDataById = changesByProjectCache.getChangeDataByChange(project, repository);
    } catch (IOException e) {
      logger.atSevere().withCause(e).log(
          "Cannot load changes for project %s, assuming no changes are visible", project);
      return;
    }

    for (Map.Entry<Change.Id, ChangeData> e : changeDataById.entrySet()) {
      ChangeData changeData = e.getValue();
      if (permissionBackendForProject.change(changeData).testOrFalse(ChangePermission.READ)) {
        visibleChanges.put(e.getKey(), changeData.branchOrThrow());
      }
    }
  }
}
