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

import com.google.gerrit.extensions.api.projects.BranchApi;
import com.google.gerrit.extensions.api.projects.ProjectApi;
import com.google.gerrit.server.project.CreateProject;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

public class ProjectApiImpl implements ProjectApi {
  interface Factory {
    ProjectApiImpl create(ProjectResource project);
  }

  private final ProjectResource project;
  private final BranchApiImpl.Factory branchApi;

  @Inject
  ProjectApiImpl(Provider<CreateProject.Factory> createProjectFactory,
      BranchApiImpl.Factory branchApiFactory,
      @Assisted ProjectResource project) {
    this.project = project;
    this.branchApi = branchApiFactory;
  }

  @Override
  public BranchApi branch(String ref) {
    return branchApi.create(project, ref);
  }
}
