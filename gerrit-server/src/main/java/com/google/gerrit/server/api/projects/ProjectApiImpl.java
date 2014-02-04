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

import com.google.gerrit.common.errors.ProjectCreationFailedException;
import com.google.gerrit.extensions.api.projects.BranchApi;
import com.google.gerrit.extensions.api.projects.ProjectApi;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.project.CreateProject;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectsCollection;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import java.io.IOException;

public class ProjectApiImpl implements ProjectApi {
  interface Factory {
    ProjectApiImpl create(ProjectResource project);
    ProjectApiImpl create(String name);
  }

  private final Provider<CreateProject.Factory> createProjectFactory;
  private final ProjectApiImpl.Factory projectApi;
  private final ProjectsCollection projects;
  private final ProjectResource project;
  private final String name;
  private final BranchApiImpl.Factory branchApi;

  @AssistedInject
  ProjectApiImpl(Provider<CreateProject.Factory> createProjectFactory,
      ProjectApiImpl.Factory projectApi,
      ProjectsCollection projects,
      BranchApiImpl.Factory branchApiFactory,
      @Assisted ProjectResource project) {
    this(createProjectFactory, projectApi, projects, branchApiFactory, project,
        null);
  }

  @AssistedInject
  ProjectApiImpl(Provider<CreateProject.Factory> createProjectFactory,
      ProjectApiImpl.Factory projectApi,
      ProjectsCollection projects,
      BranchApiImpl.Factory branchApiFactory,
      @Assisted String name) {
    this(createProjectFactory, projectApi, projects, branchApiFactory, null,
        name);
  }

  private ProjectApiImpl(Provider<CreateProject.Factory> createProjectFactory,
      ProjectApiImpl.Factory projectApi,
      ProjectsCollection projects,
      BranchApiImpl.Factory branchApiFactory,
      ProjectResource project,
      String name) {
    this.createProjectFactory = createProjectFactory;
    this.projectApi = projectApi;
    this.projects = projects;
    this.project = project;
    this.name = name;
    this.branchApi = branchApiFactory;
  }

  @Override
  public ProjectApi create() throws RestApiException {
    return create(new ProjectInput());
  }

  @Override
  public ProjectApi create(ProjectInput in) throws RestApiException {
    try {
      if (in.name != null && !name.equals(in.name)) {
        throw new RestApiException("name must match input.name");
      }
      createProjectFactory.get().create(name)
          .apply(TopLevelResource.INSTANCE, in);
      return projectApi.create(projects.parse(name));
    } catch (BadRequestException | UnprocessableEntityException
        | ResourceConflictException | ResourceNotFoundException
        | ProjectCreationFailedException | IOException e) {
      throw new RestApiException("Cannot create project", e);
    }
  }

  @Override
  public BranchApi branch(String ref) {
    return branchApi.create(project, ref);
  }
}
