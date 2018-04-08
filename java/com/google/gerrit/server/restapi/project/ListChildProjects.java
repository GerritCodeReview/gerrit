// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project;

import static java.util.stream.Collectors.toList;

import com.google.gerrit.config.AllProjectsName;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ChildProjects;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectJson;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.kohsuke.args4j.Option;

public class ListChildProjects implements RestReadView<ProjectResource> {

  @Option(name = "--recursive", usage = "to list child projects recursively")
  private boolean recursive;

  private final ProjectCache projectCache;
  private final PermissionBackend permissionBackend;
  private final AllProjectsName allProjects;
  private final ProjectJson json;
  private final ChildProjects childProjects;

  @Inject
  ListChildProjects(
      ProjectCache projectCache,
      PermissionBackend permissionBackend,
      AllProjectsName allProjectsName,
      ProjectJson json,
      ChildProjects childProjects) {
    this.projectCache = projectCache;
    this.permissionBackend = permissionBackend;
    this.allProjects = allProjectsName;
    this.json = json;
    this.childProjects = childProjects;
  }

  public void setRecursive(boolean recursive) {
    this.recursive = recursive;
  }

  @Override
  public List<ProjectInfo> apply(ProjectResource rsrc)
      throws PermissionBackendException, ResourceConflictException {
    rsrc.getProjectState().checkStatePermitsRead();
    if (recursive) {
      return childProjects.list(rsrc.getNameKey());
    }

    return directChildProjects(rsrc.getNameKey());
  }

  private List<ProjectInfo> directChildProjects(Project.NameKey parent)
      throws PermissionBackendException {
    Map<Project.NameKey, Project> children = new HashMap<>();
    for (Project.NameKey name : projectCache.all()) {
      ProjectState c = projectCache.get(name);
      if (c != null
          && parent.equals(c.getProject().getParent(allProjects))
          && c.statePermitsRead()) {
        children.put(c.getNameKey(), c.getProject());
      }
    }
    return permissionBackend
        .currentUser()
        .filter(ProjectPermission.ACCESS, children.keySet())
        .stream()
        .sorted()
        .map((p) -> json.format(children.get(p)))
        .collect(toList());
  }
}
