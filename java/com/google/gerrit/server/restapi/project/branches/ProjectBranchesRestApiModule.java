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

    /** List branches in project {@code GET /projects/<project-id>/branches}. */
    child(PROJECT_KIND, "branches").to(BranchesCollection.class);

    /** Create branch in project {@code PUT /projects/<project-id>/branches/<branch-id>}. */
    create(BRANCH_KIND).to(CreateBranch.class);
    /** Update branch in project {@code PUT /projects/<project-id>/branches/<branch-id>}. */
    put(BRANCH_KIND).to(PutBranch.class);
    /** Get branch in project {@code GET /projects/<project-id>/branches/<branch-id>}. */
    get(BRANCH_KIND).to(GetBranch.class);
    /** Delete branch in project {@code DELETE /projects/<project-id>/branches/<branch-id>}. */
    delete(BRANCH_KIND).to(DeleteBranch.class);

    /**
     * Check mergeability of source into branch in project {@code GET
     * /projects/<project-id>/branches/<branch-id>/mergeable}.
     */
    get(BRANCH_KIND, "mergeable").to(CheckMergeability.class);
    /**
     * Get reflog of branch in project {@code GET
     * /projects/<project-id>/branches/<branch-id>/reflog}.
     */
    get(BRANCH_KIND, "reflog").to(GetReflog.class);
    /**
     * Suggest reviewers for branch in project {@code GET
     * /projects/<project-id>/branches/<branch-id>/suggest_reviewers}.
     */
    get(BRANCH_KIND, "suggest_reviewers").to(SuggestBranchReviewers.class);
    /**
     * Get validation options of branch in project {@code GET
     * /projects/<project-id>/branches/<branch-id>/validation-options}.
     */
    get(BRANCH_KIND, "validation-options").to(GetBranchValidationOptions.class);

    /** Module for {@code /projects/<project-id>/branches/<branch-id>/files}. */
    install(new ProjectBranchesFilesRestApiModule());
  }
}
