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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.ProjectUtil;
import com.google.gerrit.extensions.registration.DynamicMap;
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
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Optional;

public class ProjectsCollection
    implements RestCollection<TopLevelResource, ProjectResource>, NeedsParams {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DynamicMap<RestView<ProjectResource>> views;
  private final Provider<ListProjects> list;
  private final Provider<QueryProjects> queryProjects;
  private final ProjectCache projectCache;
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> user;

  private boolean hasQuery;

  @Inject
  public ProjectsCollection(
      DynamicMap<RestView<ProjectResource>> views,
      Provider<ListProjects> list,
      Provider<QueryProjects> queryProjects,
      ProjectCache projectCache,
      PermissionBackend permissionBackend,
      Provider<CurrentUser> user) {
    this.views = views;
    this.list = list;
    this.queryProjects = queryProjects;
    this.projectCache = projectCache;
    this.permissionBackend = permissionBackend;
    this.user = user;
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
   * @throws RestApiException thrown if the project ID cannot be resolved or if the project is not
   *     visible to the calling user
   * @throws IOException thrown when there is an error.
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
   */
  public ProjectResource parse(String id, boolean checkAccess)
      throws RestApiException, IOException, PermissionBackendException {
    ProjectResource rsrc = _parse(id, checkAccess);
    if (rsrc == null) {
      throw new UnprocessableEntityException(String.format("Project Not Found: %s", id));
    }
    return rsrc;
  }

  @Nullable
  private ProjectResource _parse(String id, boolean checkAccess)
      throws PermissionBackendException, ResourceConflictException {
    id = ProjectUtil.sanitizeProjectName(id);

    Project.NameKey nameKey = Project.nameKey(id);
    Optional<ProjectState> state = projectCache.get(nameKey);
    if (!state.isPresent()) {
      return null;
    }

    logger.atFine().log("Project %s has state %s", nameKey, state.get().getProject().getState());

    if (checkAccess) {
      // Hidden projects(permitsRead = false) should only be accessible by the project owners.
      // WRITE_CONFIG is checked here because it's only allowed to project owners (ACCESS may also
      // be allowed for other users). Allowing project owners to access here will help them to view
      // and update the config of hidden projects easily.
      if (state.get().statePermitsRead()) {
        try {
          permissionBackend.currentUser().project(nameKey).check(ProjectPermission.ACCESS);
        } catch (AuthException e) {
          return null;
        }
      } else {
        try {
          permissionBackend.currentUser().project(nameKey).check(ProjectPermission.WRITE_CONFIG);
        } catch (AuthException e) {
          state.get().checkStatePermitsRead();
        }
      }
    }
    return new ProjectResource(state.get(), user.get());
  }

  @Override
  public DynamicMap<RestView<ProjectResource>> views() {
    return views;
  }
}
