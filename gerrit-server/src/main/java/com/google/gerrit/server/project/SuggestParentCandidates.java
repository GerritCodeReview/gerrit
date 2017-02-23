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

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SuggestParentCandidates {
  private static final Logger log = LoggerFactory.getLogger(SuggestParentCandidates.class);

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
    PermissionBackend.WithUser perm = permissionBackend.user(user);
    List<Project.NameKey> r = new ArrayList<>(perm.filter(ProjectPermission.ACCESS, parents()));
    Collections.sort(r, (a, b) -> a.get().compareTo(b.get()));
    return r;
  }

  private Set<Project.NameKey> parents() {
    Set<Project.NameKey> parents = new HashSet<>();
    for (Project.NameKey p : projectCache.all()) {
      try {
        ProjectState ps = projectCache.checkedGet(p);
        if (ps != null) {
          Project.NameKey parent = ps.getProject().getParent();
          if (parent != null) {
            parents.add(parent);
          }
        }
      } catch (IOException err) {
        log.warn("Cannot read project " + p, err);
      }
    }
    parents.add(allProjects);
    return parents;
  }
}
