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

package com.google.gerrit.server.project;

import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsCreate;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.lib.Constants;

@Singleton
public class BranchesCollection
    implements ChildCollection<ProjectResource, BranchResource>, AcceptsCreate<ProjectResource> {
  private final DynamicMap<RestView<BranchResource>> views;
  private final Provider<ListBranches> list;
  private final CreateBranch.Factory createBranchFactory;

  @Inject
  BranchesCollection(
      DynamicMap<RestView<BranchResource>> views,
      Provider<ListBranches> list,
      CreateBranch.Factory createBranchFactory) {
    this.views = views;
    this.list = list;
    this.createBranchFactory = createBranchFactory;
  }

  @Override
  public RestView<ProjectResource> list() {
    return list.get();
  }

  @Override
  public BranchResource parse(ProjectResource parent, IdString id)
      throws ResourceNotFoundException, IOException, BadRequestException {
    String branchName = id.get();
    if (!branchName.equals(Constants.HEAD)) {
      branchName = RefNames.fullName(branchName);
    }
    List<BranchInfo> branches = list.get().apply(parent);
    for (BranchInfo b : branches) {
      if (branchName.equals(b.ref)) {
        return new BranchResource(parent.getControl(), b);
      }
    }
    throw new ResourceNotFoundException();
  }

  @Override
  public DynamicMap<RestView<BranchResource>> views() {
    return views;
  }

  @SuppressWarnings("unchecked")
  @Override
  public CreateBranch create(ProjectResource parent, IdString name) {
    return createBranchFactory.create(name.get());
  }
}
