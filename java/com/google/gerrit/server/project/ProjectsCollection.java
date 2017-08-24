// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsCreate;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.lib.Constants;

@Singleton
public class ProjectsCollection
    implements RestCollection<TopLevelResource, ProjectResource>, AcceptsCreate<TopLevelResource> {
  private final DynamicMap<RestView<ProjectResource>> views;
  private final Provider<ListProjects> list;
  private final ProjectControl.GenericFactory controlFactory;
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> user;
  private final CreateProject.Factory createProjectFactory;

  @Inject
  ProjectsCollection(
      DynamicMap<RestView<ProjectResource>> views,
      Provider<ListProjects> list,
      ProjectControl.GenericFactory controlFactory,
      PermissionBackend permissionBackend,
      CreateProject.Factory factory,
      Provider<CurrentUser> user) {
    this.views = views;
    this.list = list;
    this.controlFactory = controlFactory;
    this.permissionBackend = permissionBackend;
    this.user = user;
    this.createProjectFactory = factory;
  }

  @Override
  public RestView<TopLevelResource> list() {
    return list.get().setFormat(OutputFormat.JSON);
  }

  @Override
  public ProjectResource parse(TopLevelResource parent, IdString id)
      throws ResourceNotFoundException, IOException, PermissionBackendException {
    ProjectResource rsrc = _parse(id.get(), true);
    if (rsrc == null) {
      throw new ResourceNotFoundException(id);
    }
    return rsrc;
  }

  /**
   * Parses a project ID from a request body and returns the project.
   *
   * @param id ID of the project, can be a project name
   * @return the project
   * @throws UnprocessableEntityException thrown if the project ID cannot be resolved or if the
   *     project is not visible to the calling user
   * @throws IOException thrown when there is an error.
   * @throws PermissionBackendException
   */
  public ProjectResource parse(String id)
      throws UnprocessableEntityException, IOException, PermissionBackendException {
    return parse(id, true);
  }

  /**
   * Parses a project ID from a request body and returns the project.
   *
   * @param id ID of the project, can be a project name
   * @param checkAccess if true, check the project is accessible by the current user
   * @return the project
   * @throws UnprocessableEntityException thrown if the project ID cannot be resolved or if the
   *     project is not visible to the calling user and checkVisibility is true.
   * @throws IOException thrown when there is an error.
   * @throws PermissionBackendException
   */
  public ProjectResource parse(String id, boolean checkAccess)
      throws UnprocessableEntityException, IOException, PermissionBackendException {
    ProjectResource rsrc = _parse(id, checkAccess);
    if (rsrc == null) {
      throw new UnprocessableEntityException(String.format("Project Not Found: %s", id));
    }
    return rsrc;
  }

  @Nullable
  private ProjectResource _parse(String id, boolean checkAccess)
      throws IOException, PermissionBackendException {
    if (id.endsWith(Constants.DOT_GIT_EXT)) {
      id = id.substring(0, id.length() - Constants.DOT_GIT_EXT.length());
    }

    Project.NameKey nameKey = new Project.NameKey(id);
    ProjectControl ctl;
    try {
      ctl = controlFactory.controlFor(nameKey, user.get());
    } catch (NoSuchProjectException e) {
      return null;
    }

    if (checkAccess) {
      try {
        permissionBackend.user(user).project(nameKey).check(ProjectPermission.ACCESS);
      } catch (AuthException e) {
        return null; // Pretend like not found on access denied.
      }
    }
    return new ProjectResource(ctl);
  }

  @Override
  public DynamicMap<RestView<ProjectResource>> views() {
    return views;
  }

  @SuppressWarnings("unchecked")
  @Override
  public CreateProject create(TopLevelResource parent, IdString name) {
    return createProjectFactory.create(name.get());
  }
}
