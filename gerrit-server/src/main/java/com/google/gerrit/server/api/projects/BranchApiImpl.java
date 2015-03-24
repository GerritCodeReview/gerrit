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
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.project.BranchResource;
import com.google.gerrit.server.project.BranchesCollection;
import com.google.gerrit.server.project.CreateBranch;
import com.google.gerrit.server.project.DeleteBranch;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;

public class BranchApiImpl implements BranchApi {
  interface Factory {
    BranchApiImpl create(ProjectResource project, String ref);
  }

  private final BranchesCollection branches;
  private final CreateBranch.Factory createBranchFactory;
  private final DeleteBranch deleteBranch;
  private final String ref;
  private final ProjectResource project;

  @Inject
  BranchApiImpl(BranchesCollection branches,
      CreateBranch.Factory createBranchFactory,
      DeleteBranch deleteBranch,
      @Assisted ProjectResource project,
      @Assisted String ref) {
    this.branches = branches;
    this.createBranchFactory = createBranchFactory;
    this.deleteBranch = deleteBranch;
    this.project = project;
    this.ref = ref;
  }

  @Override
  public BranchApi create(BranchInput in) throws RestApiException {
    try {
      CreateBranch.Input input = new CreateBranch.Input();
      input.ref = ref;
      input.revision = in.revision;
      createBranchFactory.create(ref).apply(project, input);
      return this;
    } catch (IOException e) {
      throw new RestApiException("Cannot create branch", e);
    }
  }

  @Override
  public BranchInfo get() throws RestApiException {
    try {
      return resource().getBranchInfo();
    } catch (IOException e) {
      throw new RestApiException("Cannot read branch", e);
    }
  }

  @Override
  public void delete() throws RestApiException {
    try {
      deleteBranch.apply(resource(), new DeleteBranch.Input());
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot delete branch", e);
    }
  }

  private BranchResource resource() throws RestApiException, IOException {
    return branches.parse(project, IdString.fromDecoded(ref));
  }
}
