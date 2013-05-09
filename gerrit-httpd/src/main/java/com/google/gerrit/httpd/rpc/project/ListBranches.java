// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.project;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.ListBranchesResult;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.project.ListBranches.BranchInfo;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;
import java.util.List;

class ListBranches extends Handler<ListBranchesResult> {
  interface Factory {
    ListBranches create(@Assisted Project.NameKey name);
  }

  private final ProjectControl.Factory projectControlFactory;
  private final Provider<com.google.gerrit.server.project.ListBranches> listBranchesProvider;

  private final Project.NameKey projectName;

  @Inject
  ListBranches(final ProjectControl.Factory projectControlFactory,
      final Provider<com.google.gerrit.server.project.ListBranches> listBranchesProvider,
      @Assisted final Project.NameKey name) {
    this.projectControlFactory = projectControlFactory;
    this.listBranchesProvider = listBranchesProvider;

    this.projectName = name;
  }

  @Override
  public ListBranchesResult call() throws NoSuchProjectException, IOException {
    ProjectControl pctl =
        projectControlFactory.validateFor(projectName, ProjectControl.OWNER
            | ProjectControl.VISIBLE);
    try {
      List<Branch> branches = Lists.newArrayList();
      List<BranchInfo> branchInfos = listBranchesProvider.get().apply(new ProjectResource(pctl));
      for (BranchInfo info : branchInfos) {
        Branch b = new Branch(new Branch.NameKey(projectName, info.ref));
        b.setRevision(new RevId(info.revision));
        b.setCanDelete(Objects.firstNonNull(info.canDelete, false));
        branches.add(b);
      }
      return new ListBranchesResult(branches, pctl.canAddRefs(), false);
    } catch (ResourceNotFoundException e) {
      throw new NoSuchProjectException(projectName);
    }
  }
}
