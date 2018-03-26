// Copyright (C) 2011 The Android Open Source Project
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
// limitations under the License

package com.google.gerrit.server.project;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Singleton
public class SuggestParentCandidates {
  private final ProjectCache projectCache;
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> user;
  private final AllProjectsName allProjects;

  @Inject
  SuggestParentCandidates(
      ProjectCache projectCache,
      PermissionBackend permissionBackend,
      Provider<CurrentUser> user,
      AllProjectsName allProjects) {
    this.projectCache = projectCache;
    this.permissionBackend = permissionBackend;
    this.user = user;
    this.allProjects = allProjects;
  }

  public List<Project.NameKey> getNameKeys() throws PermissionBackendException {
    List<Project.NameKey> canSee = new ArrayList<>();
    for (Project.NameKey parentName : parents()) {
      ProjectState state = projectCache.get(parentName);
      checkNotNull(state, "Failed to load project %s", parentName);

      ProjectPermission permissionToCheck =
          state.statePermitsRead() ? ProjectPermission.ACCESS : ProjectPermission.READ_CONFIG;
      try {
        permissionBackend.user(user).project(parentName).check(permissionToCheck);
        canSee.add(parentName);
      } catch (AuthException e) {
        // Do not include this project.
      }
    }
    Collections.sort(canSee, Comparator.naturalOrder());

    return canSee;
  }

  private Set<Project.NameKey> parents() {
    Set<Project.NameKey> parents = new HashSet<>();
    for (Project.NameKey p : projectCache.all()) {
      ProjectState ps = projectCache.get(p);
      if (ps != null) {
        Project.NameKey parent = ps.getProject().getParent();
        if (parent != null) {
          parents.add(parent);
        }
      }
    }
    parents.add(allProjects);
    return parents;
  }
}
