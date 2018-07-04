// Copyright (C) 2018 The Android Open Source Project
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

import com.google.gerrit.extensions.api.projects.IndexProjectInput;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.index.project.ProjectIndexer;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class Index extends RetryingRestModifyView<ProjectResource, IndexProjectInput, Response<?>> {
  private final PermissionBackend permissionBackend;
  private final ProjectIndexer indexer;
  private final Provider<ListChildProjects> listChildProjectsProvider;

  @Inject
  Index(
      RetryHelper retryHelper,
      PermissionBackend permissionBackend,
      ProjectIndexer indexer,
      Provider<ListChildProjects> listChildProjectsProvider) {
    super(retryHelper);
    this.permissionBackend = permissionBackend;
    this.indexer = indexer;
    this.listChildProjectsProvider = listChildProjectsProvider;
  }

  @Override
  protected Response<?> applyImpl(
      BatchUpdate.Factory updateFactory, ProjectResource rsrc, IndexProjectInput input)
      throws IOException, AuthException, OrmException, PermissionBackendException,
          ResourceConflictException {
    permissionBackend.currentUser().check(GlobalPermission.MAINTAIN_SERVER);

    indexer.index(rsrc.getNameKey());

    if (Boolean.TRUE.equals(input.indexChildren)) {
      ListChildProjects listChildProjects = listChildProjectsProvider.get();
      listChildProjects.setRecursive(true);
      for (ProjectInfo child : listChildProjects.apply(rsrc)) {
        indexer.index(new Project.NameKey(child.name));
      }
    }

    return Response.none();
  }
}
