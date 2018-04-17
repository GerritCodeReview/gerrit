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

package com.google.gerrit.server.restapi.project;

import com.google.common.collect.ListMultimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsCreate;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.NeedsParams;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
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
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectAccessor;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.lib.Constants;

@Singleton
public class ProjectsCollection
    implements RestCollection<TopLevelResource, ProjectResource>,
        AcceptsCreate<TopLevelResource>,
        NeedsParams {
  private final DynamicMap<RestView<ProjectResource>> views;
  private final Provider<ListProjects> list;
  private final Provider<QueryProjects> queryProjects;
  private final ProjectAccessor.Factory projectAccessorFactory;
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> user;
  private final CreateProject.Factory createProjectFactory;

  private boolean hasQuery;

  @Inject
  ProjectsCollection(
      DynamicMap<RestView<ProjectResource>> views,
      Provider<ListProjects> list,
      Provider<QueryProjects> queryProjects,
      ProjectAccessor.Factory projectAccessorFactory,
      PermissionBackend permissionBackend,
      CreateProject.Factory factory,
      Provider<CurrentUser> user) {
    this.views = views;
    this.list = list;
    this.queryProjects = queryProjects;
    this.projectAccessorFactory = projectAccessorFactory;
    this.permissionBackend = permissionBackend;
    this.user = user;
    this.createProjectFactory = factory;
  }

  @Override
  public void setParams(ListMultimap<String, String> params) throws BadRequestException {
    // The --query option is defined in QueryProjects
    this.hasQuery = params.containsKey("query");
  }

  @Override
  public RestView<TopLevelResource> list() {
    if (hasQuery) {
      return queryProjects.get();
    }
    return list.get().setFormat(OutputFormat.JSON);
  }

  @Override
  public ProjectResource parse(TopLevelResource parent, IdString id)
      throws RestApiException, IOException, PermissionBackendException {
    try {
      return _parse(id.get(), true);
    } catch (NoSuchProjectException e) {
      throw new ResourceNotFoundException(id);
    }
  }

  /**
   * Parses a project ID from a request body and returns the project.
   *
   * @param id ID of the project, can be a project name
   * @return the project
   * @throws RestApiException thrown if the project ID cannot be resolved or if the project is not
   *     visible to the calling user
   * @throws IOException thrown when there is an error.
   * @throws PermissionBackendException
   */
  public ProjectResource parse(String id)
      throws RestApiException, IOException, PermissionBackendException {
    return parse(id, true);
  }

  /**
   * Parses a project ID from a request body and returns the project.
   *
   * @param id ID of the project, can be a project name
   * @param checkAccess if true, check the project is accessible by the current user
   * @return the project
   * @throws RestApiException thrown if the project ID cannot be resolved or if the project is not
   *     visible to the calling user and checkVisibility is true.
   * @throws IOException thrown when there is an error.
   * @throws PermissionBackendException
   */
  public ProjectResource parse(String id, boolean checkAccess)
      throws RestApiException, IOException, PermissionBackendException {
    try {
      return _parse(id, checkAccess);
    } catch (NoSuchProjectException e) {
      throw new UnprocessableEntityException(String.format("Project Not Found: %s", id));
    }
  }

  @Nullable
  private ProjectResource _parse(String id, boolean checkAccess)
      throws IOException, PermissionBackendException, ResourceConflictException,
          NoSuchProjectException {
    if (id.endsWith(Constants.DOT_GIT_EXT)) {
      id = id.substring(0, id.length() - Constants.DOT_GIT_EXT.length());
    }

    Project.NameKey nameKey = new Project.NameKey(id);
    ProjectAccessor accessor = projectAccessorFactory.create(nameKey);
    ProjectState state = accessor.getProjectState();

    if (checkAccess) {
      // Hidden projects(permitsRead = false) should only be accessible by the project owners.
      // READ_CONFIG is checked here because it's only allowed to project owners(ACCESS may also
      // be allowed for other users). Allowing project owners to access here will help them to view
      // and update the config of hidden projects easily.
      ProjectPermission permissionToCheck =
          state.statePermitsRead() ? ProjectPermission.ACCESS : ProjectPermission.READ_CONFIG;
      try {
        permissionBackend.currentUser().project(nameKey).check(permissionToCheck);
      } catch (AuthException e) {
        return null; // Pretend like not found on access denied.
      }
      // If the project's state does not permit reading, we want to hide it from all callers. The
      // only exception to that are users who are allowed to mutate the project's configuration.
      // This enables these users to still mutate the project's state (e.g. set a HIDDEN project to
      // ACTIVE). Individual views should still check for checkStatePermitsRead() and this should
      // just serve as a safety net in case the individual check is forgotten.
      try {
        permissionBackend.currentUser().project(nameKey).check(ProjectPermission.WRITE_CONFIG);
      } catch (AuthException e) {
        state.checkStatePermitsRead();
      }
    }
    return new ProjectResource(accessor, user.get());
  }

  @Override
  public DynamicMap<RestView<ProjectResource>> views() {
    return views;
  }

  @Override
  public CreateProject create(TopLevelResource parent, IdString name) {
    return createProjectFactory.create(name.get());
  }
}
