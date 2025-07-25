// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project;

import static com.google.gerrit.server.project.BranchResource.BRANCH_KIND;
import static com.google.gerrit.server.project.ChildProjectResource.CHILD_PROJECT_KIND;
import static com.google.gerrit.server.project.CommitResource.COMMIT_KIND;
import static com.google.gerrit.server.project.DashboardResource.DASHBOARD_KIND;
import static com.google.gerrit.server.project.FileResource.FILE_KIND;
import static com.google.gerrit.server.project.LabelResource.LABEL_KIND;
import static com.google.gerrit.server.project.ProjectResource.PROJECT_KIND;
import static com.google.gerrit.server.project.SubmitRequirementResource.SUBMIT_REQUIREMENT_KIND;
import static com.google.gerrit.server.project.TagResource.TAG_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.restapi.change.CherryPickCommit;

public class ProjectRestApiModule extends RestApiModule {

  @Override
  protected void configure() {
    bind(ProjectsCollection.class);
    bind(ListProjects.class).to(ListProjectsImpl.class);
    bind(DashboardsCollection.class);

    DynamicMap.mapOf(binder(), BRANCH_KIND);
    DynamicMap.mapOf(binder(), CHILD_PROJECT_KIND);
    DynamicMap.mapOf(binder(), COMMIT_KIND);
    DynamicMap.mapOf(binder(), DASHBOARD_KIND);
    DynamicMap.mapOf(binder(), FILE_KIND);
    DynamicMap.mapOf(binder(), LABEL_KIND);
    DynamicMap.mapOf(binder(), PROJECT_KIND);
    DynamicMap.mapOf(binder(), SUBMIT_REQUIREMENT_KIND);
    DynamicMap.mapOf(binder(), TAG_KIND);

    create(PROJECT_KIND).to(CreateProject.class);
    get(PROJECT_KIND).to(GetProject.class);
    put(PROJECT_KIND).to(PutProject.class);
    get(PROJECT_KIND, "access").to(GetAccess.class);
    post(PROJECT_KIND, "access").to(SetAccess.class);
    put(PROJECT_KIND, "access:review").to(CreateAccessChange.class);
    put(PROJECT_KIND, "ban").to(BanCommit.class);

    child(PROJECT_KIND, "branches").to(BranchesCollection.class);
    create(BRANCH_KIND).to(CreateBranch.class);
    put(BRANCH_KIND).to(PutBranch.class);
    get(BRANCH_KIND).to(GetBranch.class);
    delete(BRANCH_KIND).to(DeleteBranch.class);

    child(BRANCH_KIND, "files").to(FilesCollection.class);
    get(FILE_KIND, "content").to(GetContent.class);

    get(BRANCH_KIND, "mergeable").to(CheckMergeability.class);
    get(BRANCH_KIND, "reflog").to(GetReflog.class);
    get(BRANCH_KIND, "suggest_reviewers").to(SuggestBranchReviewers.class);
    get(BRANCH_KIND, "validation-options").to(GetBranchValidationOptions.class);

    post(PROJECT_KIND, "branches:delete").to(DeleteBranches.class);
    post(PROJECT_KIND, "check").to(Check.class);
    get(PROJECT_KIND, "check.access").to(CheckAccess.class);

    child(PROJECT_KIND, "children").to(ChildProjectsCollection.class);
    get(CHILD_PROJECT_KIND).to(GetChildProject.class);

    child(PROJECT_KIND, "commits").to(CommitsCollection.class);
    get(COMMIT_KIND).to(GetCommit.class);
    post(COMMIT_KIND, "cherrypick").to(CherryPickCommit.class);
    child(COMMIT_KIND, "files").to(FilesInCommitCollection.class);
    get(COMMIT_KIND, "in").to(CommitIncludedIn.class);

    get(PROJECT_KIND, "commits:in").to(CommitsIncludedInRefs.class);

    get(PROJECT_KIND, "config").to(GetConfig.class);
    put(PROJECT_KIND, "config").to(PutConfig.class);
    put(PROJECT_KIND, "config:review").to(PutConfigReview.class);

    post(PROJECT_KIND, "create.change").to(CreateChange.class);

    child(PROJECT_KIND, "dashboards").to(DashboardsCollection.class);
    create(DASHBOARD_KIND).to(CreateDashboard.class);
    delete(DASHBOARD_KIND).to(DeleteDashboard.class);
    get(DASHBOARD_KIND).to(GetDashboard.class);
    put(DASHBOARD_KIND).to(SetDashboard.class);

    get(PROJECT_KIND, "description").to(GetDescription.class);
    put(PROJECT_KIND, "description").to(PutDescription.class);
    delete(PROJECT_KIND, "description").to(PutDescription.class);
    get(PROJECT_KIND, "HEAD").to(GetHead.class);
    put(PROJECT_KIND, "HEAD").to(SetHead.class);
    post(PROJECT_KIND, "index").to(Index.class);

    child(PROJECT_KIND, "labels").to(LabelsCollection.class);
    create(LABEL_KIND).to(CreateLabel.class);
    get(LABEL_KIND).to(GetLabel.class);
    put(LABEL_KIND).to(SetLabel.class);
    delete(LABEL_KIND).to(DeleteLabel.class);
    postOnCollection(LABEL_KIND).to(PostLabels.class);
    post(PROJECT_KIND, "labels:review").to(PostLabelsReview.class);

    get(PROJECT_KIND, "parent").to(GetParent.class);
    put(PROJECT_KIND, "parent").to(SetParent.class);

    child(PROJECT_KIND, "submit_requirements").to(SubmitRequirementsCollection.class);
    create(SUBMIT_REQUIREMENT_KIND).to(CreateSubmitRequirement.class);
    put(SUBMIT_REQUIREMENT_KIND).to(UpdateSubmitRequirement.class);
    get(SUBMIT_REQUIREMENT_KIND).to(GetSubmitRequirement.class);
    delete(SUBMIT_REQUIREMENT_KIND).to(DeleteSubmitRequirement.class);
    postOnCollection(SUBMIT_REQUIREMENT_KIND).to(PostSubmitRequirements.class);
    post(PROJECT_KIND, "submit_requirements:review").to(PostSubmitRequirementsReview.class);

    child(PROJECT_KIND, "tags").to(TagsCollection.class);
    create(TAG_KIND).to(CreateTag.class);
    get(TAG_KIND).to(GetTag.class);
    put(TAG_KIND).to(PutTag.class);
    delete(TAG_KIND).to(DeleteTag.class);

    post(PROJECT_KIND, "tags:delete").to(DeleteTags.class);

    post(PROJECT_KIND, "changes:delete").to(DeleteChanges.class);
  }

  /** Separately bind batch functionality. */
  public static class BatchModule extends RestApiModule {
    @Override
    protected void configure() {
      post(PROJECT_KIND, "gc").to(GarbageCollect.class);
      post(PROJECT_KIND, "index.changes").to(IndexChanges.class);
      get(PROJECT_KIND, "statistics.git").to(GetStatistics.class);
    }
  }
}
