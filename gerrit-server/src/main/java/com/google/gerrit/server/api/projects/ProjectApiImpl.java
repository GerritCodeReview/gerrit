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

import com.google.common.base.Preconditions;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.ProjectApi;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.project.CreateBranch;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;

public class ProjectApiImpl implements ProjectApi {
  interface Factory {
    ProjectApiImpl create(ProjectResource change);
  }

  private final CreateBranch.Factory createBranchFactory;
  private final ProjectResource project;
  private String branch;

  @Inject
  ProjectApiImpl(
      CreateBranch.Factory createBranchFactory,
      @Assisted ProjectResource project) {
    this.createBranchFactory = createBranchFactory;
    this.project = project;
  }

  @Override
  public ProjectApi branch(String ref) {
    branch = ref;
    return this;
  }

  @Override
  public void create(BranchInput in) throws RestApiException {
    try {
      Preconditions.checkNotNull(branch, "branch was not set: " +
          "forgot to call branch()");
      CreateBranch.Input input = new CreateBranch.Input();
      input.ref = branch;
      input.revision = in.revision;
      createBranchFactory.create(branch).apply(project, input);
      branch = null;
    } catch (IOException e) {
      throw new RestApiException("Cannot create branch", e);
    }
  }
}
