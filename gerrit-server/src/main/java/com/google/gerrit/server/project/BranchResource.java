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

import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.server.project.ListBranches.BranchInfo;
import com.google.inject.TypeLiteral;

public class BranchResource extends ProjectResource {
  public static final TypeLiteral<RestView<BranchResource>> BRANCH_KIND =
      new TypeLiteral<RestView<BranchResource>>() {};

  private final BranchInfo branchInfo;

  public BranchResource(ProjectControl control, BranchInfo branchInfo) {
    super(control);
    this.branchInfo = branchInfo;
  }

  public BranchInfo getBranchInfo() {
    return branchInfo;
  }

  public Branch.NameKey getBranchKey() {
    return new Branch.NameKey(getNameKey(), branchInfo.ref);
  }

  public String getRef() {
    return branchInfo.ref;
  }
}
