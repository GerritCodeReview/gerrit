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

import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ChildProjects;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.List;
import org.kohsuke.args4j.Option;

public class ListChildProjects implements RestReadView<ProjectResource> {

  @Option(name = "--recursive", usage = "to list child projects recursively")
  private boolean recursive;

  @Option(name = "--limit", usage = "maximum number of parents projects to list")
  private int limit;

  private final PermissionBackend permissionBackend;
  private final ChildProjects childProjects;
  private final Provider<QueryProjects> queryProvider;

  @Inject
  ListChildProjects(
      PermissionBackend permissionBackend,
      ChildProjects childProjects,
      Provider<QueryProjects> queryProvider) {
    this.permissionBackend = permissionBackend;
    this.childProjects = childProjects;
    this.queryProvider = queryProvider;
  }

  public ListChildProjects withRecursive(boolean recursive) {
    this.recursive = recursive;
    return this;
  }

  public ListChildProjects withLimit(int limit) {
    this.limit = limit;
    return this;
  }

  @Override
  public List<ProjectInfo> apply(ProjectResource rsrc)
      throws PermissionBackendException, RestApiException {
    if (limit < 0) {
      throw new BadRequestException("limit must be a positive number");
    }
    if (recursive && limit != 0) {
      throw new ResourceConflictException("recursive and limit options are mutually exclusive");
    }
    rsrc.getProjectState().checkStatePermitsRead();
    if (recursive) {
      return childProjects.list(rsrc.getNameKey());
    }

    return directChildProjects(rsrc.getNameKey());
  }

  private List<ProjectInfo> directChildProjects(Project.NameKey parent) throws RestApiException {
    PermissionBackend.WithUser currentUser = permissionBackend.currentUser();
    return queryProvider.get().withQuery("parent:" + parent.get()).withLimit(limit).apply().stream()
        .filter(
            p -> currentUser.project(Project.nameKey(p.name)).testOrFalse(ProjectPermission.ACCESS))
        .collect(toList());
  }
}
