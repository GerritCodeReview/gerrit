// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project.branches;

import static com.google.gerrit.server.project.BranchResource.BRANCH_KIND;
import static com.google.gerrit.server.project.ProjectResource.PROJECT_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.restapi.project.branches.files.ProjectBranchesFilesRestApiModule;

public class ProjectBranchesRestApiModule extends RestApiModule {

  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), BRANCH_KIND);

    child(PROJECT_KIND, "branches").to(BranchesCollection.class);
    create(BRANCH_KIND).to(CreateBranch.class);
    put(BRANCH_KIND).to(PutBranch.class);
    get(BRANCH_KIND).to(GetBranch.class);
    delete(BRANCH_KIND).to(DeleteBranch.class);

    get(BRANCH_KIND, "mergeable").to(CheckMergeability.class);
    get(BRANCH_KIND, "reflog").to(GetReflog.class);
    get(BRANCH_KIND, "suggest_reviewers").to(SuggestBranchReviewers.class);
    get(BRANCH_KIND, "validation-options").to(GetBranchValidationOptions.class);

    install(new ProjectBranchesFilesRestApiModule());
  }
}
