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

package com.google.gerrit.server.project;

import static com.google.gerrit.server.project.BranchResource.BRANCH_KIND;
import static com.google.gerrit.server.project.ChildProjectResource.CHILD_PROJECT_KIND;
import static com.google.gerrit.server.project.CommitResource.COMMIT_KIND;
import static com.google.gerrit.server.project.DashboardResource.DASHBOARD_KIND;
import static com.google.gerrit.server.project.FileResource.FILE_KIND;
import static com.google.gerrit.server.project.ProjectResource.PROJECT_KIND;
import static com.google.gerrit.server.project.TagResource.TAG_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;

public class Module extends RestApiModule {
  @Override
  protected void configure() {
    bind(ProjectsCollection.class);
    bind(DashboardsCollection.class);

    DynamicMap.mapOf(binder(), PROJECT_KIND);
    DynamicMap.mapOf(binder(), CHILD_PROJECT_KIND);
    DynamicMap.mapOf(binder(), BRANCH_KIND);
    DynamicMap.mapOf(binder(), DASHBOARD_KIND);
    DynamicMap.mapOf(binder(), FILE_KIND);
    DynamicMap.mapOf(binder(), COMMIT_KIND);
    DynamicMap.mapOf(binder(), TAG_KIND);

    put(PROJECT_KIND).to(PutProject.class);
    get(PROJECT_KIND).to(GetProject.class);
    get(PROJECT_KIND, "description").to(GetDescription.class);
    put(PROJECT_KIND, "description").to(PutDescription.class);
    delete(PROJECT_KIND, "description").to(PutDescription.class);

    get(PROJECT_KIND, "parent").to(GetParent.class);
    put(PROJECT_KIND, "parent").to(SetParent.class);

    child(PROJECT_KIND, "children").to(ChildProjectsCollection.class);
    get(CHILD_PROJECT_KIND).to(GetChildProject.class);

    get(PROJECT_KIND, "HEAD").to(GetHead.class);
    put(PROJECT_KIND, "HEAD").to(SetHead.class);

    put(PROJECT_KIND, "ban").to(BanCommit.class);

    get(PROJECT_KIND, "statistics.git").to(GetStatistics.class);
    post(PROJECT_KIND, "gc").to(GarbageCollect.class);

    child(PROJECT_KIND, "branches").to(BranchesCollection.class);
    put(BRANCH_KIND).to(PutBranch.class);
    get(BRANCH_KIND).to(GetBranch.class);
    delete(BRANCH_KIND).to(DeleteBranch.class);
    post(PROJECT_KIND, "branches:delete").to(DeleteBranches.class);
    install(new FactoryModuleBuilder().build(CreateBranch.Factory.class));
    get(BRANCH_KIND, "reflog").to(GetReflog.class);
    child(BRANCH_KIND, "files").to(FilesCollection.class);
    get(FILE_KIND, "content").to(GetContent.class);

    child(PROJECT_KIND, "commits").to(CommitsCollection.class);
    get(COMMIT_KIND).to(GetCommit.class);
    child(COMMIT_KIND, "files").to(FilesInCommitCollection.class);

    child(PROJECT_KIND, "tags").to(TagsCollection.class);
    get(TAG_KIND).to(GetTag.class);

    child(PROJECT_KIND, "dashboards").to(DashboardsCollection.class);
    get(DASHBOARD_KIND).to(GetDashboard.class);
    put(DASHBOARD_KIND).to(SetDashboard.class);
    delete(DASHBOARD_KIND).to(DeleteDashboard.class);
    install(new FactoryModuleBuilder().build(CreateProject.Factory.class));

    get(PROJECT_KIND, "config").to(GetConfig.class);
    put(PROJECT_KIND, "config").to(PutConfig.class);
  }
}
