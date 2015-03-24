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

package com.google.gerrit.server.api.projects;

import static com.google.gerrit.server.account.CapabilityUtils.checkRequiresCapability;

import com.google.gerrit.common.errors.ProjectCreationFailedException;
import com.google.gerrit.extensions.api.projects.BranchApi;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.ChildProjectApi;
import com.google.gerrit.extensions.api.projects.ProjectApi;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.api.projects.PutDescriptionInput;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.ChildProjectsCollection;
import com.google.gerrit.server.project.CreateProject;
import com.google.gerrit.server.project.GetDescription;
import com.google.gerrit.server.project.ListBranches;
import com.google.gerrit.server.project.ListChildProjects;
import com.google.gerrit.server.project.ProjectJson;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectsCollection;
import com.google.gerrit.server.project.PutDescription;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import java.io.IOException;
import java.util.List;

public class ProjectApiImpl implements ProjectApi {
  interface Factory {
    ProjectApiImpl create(ProjectResource project);
    ProjectApiImpl create(String name);
  }

  private final Provider<CurrentUser> user;
  private final Provider<CreateProject.Factory> createProjectFactory;
  private final ProjectApiImpl.Factory projectApi;
  private final ProjectsCollection projects;
  private final GetDescription getDescription;
  private final PutDescription putDescription;
  private final ChildProjectApiImpl.Factory childApi;
  private final ChildProjectsCollection children;
  private final ProjectResource project;
  private final ProjectJson projectJson;
  private final String name;
  private final BranchApiImpl.Factory branchApi;
  private final Provider<ListBranches> listBranchesProvider;

  @AssistedInject
  ProjectApiImpl(Provider<CurrentUser> user,
      Provider<CreateProject.Factory> createProjectFactory,
      ProjectApiImpl.Factory projectApi,
      ProjectsCollection projects,
      GetDescription getDescription,
      PutDescription putDescription,
      ChildProjectApiImpl.Factory childApi,
      ChildProjectsCollection children,
      ProjectJson projectJson,
      BranchApiImpl.Factory branchApiFactory,
      Provider<ListBranches> listBranchesProvider,
      @Assisted ProjectResource project) {
    this(user, createProjectFactory, projectApi, projects, getDescription,
        putDescription, childApi, children, projectJson, branchApiFactory,
        listBranchesProvider, project, null);
  }

  @AssistedInject
  ProjectApiImpl(Provider<CurrentUser> user,
      Provider<CreateProject.Factory> createProjectFactory,
      ProjectApiImpl.Factory projectApi,
      ProjectsCollection projects,
      GetDescription getDescription,
      PutDescription putDescription,
      ChildProjectApiImpl.Factory childApi,
      ChildProjectsCollection children,
      ProjectJson projectJson,
      BranchApiImpl.Factory branchApiFactory,
      Provider<ListBranches> listBranchesProvider,
      @Assisted String name) {
    this(user, createProjectFactory, projectApi, projects, getDescription,
        putDescription, childApi, children, projectJson, branchApiFactory,
        listBranchesProvider, null, name);
  }

  private ProjectApiImpl(Provider<CurrentUser> user,
      Provider<CreateProject.Factory> createProjectFactory,
      ProjectApiImpl.Factory projectApi,
      ProjectsCollection projects,
      GetDescription getDescription,
      PutDescription putDescription,
      ChildProjectApiImpl.Factory childApi,
      ChildProjectsCollection children,
      ProjectJson projectJson,
      BranchApiImpl.Factory branchApiFactory,
      Provider<ListBranches> listBranchesProvider,
      ProjectResource project,
      String name) {
    this.user = user;
    this.createProjectFactory = createProjectFactory;
    this.projectApi = projectApi;
    this.projects = projects;
    this.getDescription = getDescription;
    this.putDescription = putDescription;
    this.childApi = childApi;
    this.children = children;
    this.projectJson = projectJson;
    this.project = project;
    this.name = name;
    this.branchApi = branchApiFactory;
    this.listBranchesProvider = listBranchesProvider;
  }

  @Override
  public ProjectApi create() throws RestApiException {
    return create(new ProjectInput());
  }

  @Override
  public ProjectApi create(ProjectInput in) throws RestApiException {
    try {
      if (name == null) {
        throw new ResourceConflictException("Project already exists");
      }
      if (in.name != null && !name.equals(in.name)) {
        throw new BadRequestException("name must match input.name");
      }
      checkRequiresCapability(user, null, CreateProject.class);
      createProjectFactory.get().create(name)
          .apply(TopLevelResource.INSTANCE, in);
      return projectApi.create(projects.parse(name));
    } catch (ProjectCreationFailedException | IOException e) {
      throw new RestApiException("Cannot create project: " + e.getMessage(), e);
    }
  }

  @Override
  public ProjectInfo get() throws RestApiException {
    if (project == null) {
      throw new ResourceNotFoundException(name);
    }
    return projectJson.format(project);
  }

  @Override
  public String description() throws RestApiException {
    return getDescription.apply(checkExists());
  }

  @Override
  public void description(PutDescriptionInput in)
      throws RestApiException {
    try {
      putDescription.apply(checkExists(), in);
    } catch (IOException e) {
      throw new RestApiException("Cannot put project description", e);
    }
  }

  @Override
  public ListBranchesRequest branches() {
    return new ListBranchesRequest() {
      @Override
      public List<BranchInfo> get() throws RestApiException {
        return listBranches(this);
      }
    };
  }

  private List<BranchInfo> listBranches(ListBranchesRequest request)
      throws RestApiException {
    ListBranches list = listBranchesProvider.get();
    list.setLimit(request.getLimit());
    list.setStart(request.getStart());
    list.setMatchSubstring(request.getSubstring());
    list.setMatchRegex(request.getRegex());
    try {
      return list.apply(checkExists());
    } catch (IOException e) {
      throw new RestApiException("Cannot list branches", e);
    }
  }

  @Override
  public List<ProjectInfo> children() throws RestApiException {
    return children(false);
  }

  @Override
  public List<ProjectInfo> children(boolean recursive) throws RestApiException {
    ListChildProjects list = children.list();
    list.setRecursive(recursive);
    return list.apply(checkExists());
  }

  @Override
  public ChildProjectApi child(String name) throws RestApiException {
    try {
      return childApi.create(
          children.parse(checkExists(), IdString.fromDecoded(name)));
    } catch (IOException e) {
      throw new RestApiException("Cannot parse child project", e);
    }
  }

  @Override
  public BranchApi branch(String ref) throws ResourceNotFoundException {
    return branchApi.create(checkExists(), ref);
  }

  private ProjectResource checkExists() throws ResourceNotFoundException {
    if (project == null) {
      throw new ResourceNotFoundException(name);
    }
    return project;
  }
}
