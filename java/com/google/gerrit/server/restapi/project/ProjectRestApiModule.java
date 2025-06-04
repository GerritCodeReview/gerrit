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

import static com.google.gerrit.server.project.ProjectResource.PROJECT_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.restapi.project.branches.ProjectBranchesRestApiModule;
import com.google.gerrit.server.restapi.project.children.ProjectChildrenRestApiModule;
import com.google.gerrit.server.restapi.project.commits.ProjectCommitsRestApiModule;
import com.google.gerrit.server.restapi.project.dashboards.ProjectDashboardsRestApiModule;
import com.google.gerrit.server.restapi.project.labels.ProjectLabelsRestApiModule;
import com.google.gerrit.server.restapi.project.submitrequirements.ProjectSubmitRequirementsRestApiModule;
import com.google.gerrit.server.restapi.project.tags.ProjectTagsRestApiModule;

/** Guice module that binds all REST endpoints for {@code /projects/}. */
public class ProjectRestApiModule extends RestApiModule {

  @Override
  protected void configure() {
    bind(ProjectsCollection.class);
    bind(ListProjects.class).to(ListProjectsImpl.class);

    DynamicMap.mapOf(binder(), PROJECT_KIND);

    /** Create project {@code PUT /projects/<project-name>}. */
    create(PROJECT_KIND).to(CreateProject.class);
    /** Get project {@code GET /projects/<project-id>}. */
    get(PROJECT_KIND).to(GetProject.class);
    /** Update project {@code PUT /projects/<project-id>}. */
    put(PROJECT_KIND).to(PutProject.class);

    /** Get project access rules {@code GET /projects/<project-id>/access}. */
    get(PROJECT_KIND, "access").to(GetAccess.class);
    /** Set project access rules {@code POST /projects/<project-id>/access}. */
    post(PROJECT_KIND, "access").to(SetAccess.class);
    /** Create change adapting the access rules {@code PUT /projects/<project-id>/access:review}. */
    put(PROJECT_KIND, "access:review").to(CreateAccessChange.class);

    /** Ban commit {@code PUT /projects/<project-id>/ban}. */
    put(PROJECT_KIND, "ban").to(BanCommit.class);

    /** Delete branches in project {@code POST /projects/<project-id>/branches:delete}. */
    post(PROJECT_KIND, "branches:delete").to(DeleteBranches.class);

    /** Check project consistency {@code POST /projects/<project-id>/check}. */
    post(PROJECT_KIND, "check").to(Check.class);

    /** Check access for account {@code GET /projects/<project-id>/check.access}. */
    get(PROJECT_KIND, "check.access").to(CheckAccess.class);

    /** Get commits included in refs {@code GET /projects/<project-id>/commits:in}. */
    get(PROJECT_KIND, "commits:in").to(CommitsIncludedInRefs.class);

    /** Get project config {@code GET /projects/<project-id>/config}. */
    get(PROJECT_KIND, "config").to(GetConfig.class);
    /** Update project config {@code PUT /projects/<project-id>/config}. */
    put(PROJECT_KIND, "config").to(PutConfig.class);
    /**
     * Create change that updates project config {@code PUT /projects/<project-id>/config:review}.
     */
    put(PROJECT_KIND, "config:review").to(PutConfigReview.class);

    /** Create change in project {@code POST /projects/<project-id>/create.change}. */
    post(PROJECT_KIND, "create.change").to(CreateChange.class);

    /** Get project description {@code GET /projects/<project-id>/description}. */
    get(PROJECT_KIND, "description").to(GetDescription.class);
    /** Update project description {@code PUT /projects/<project-id>/description}. */
    put(PROJECT_KIND, "description").to(PutDescription.class);
    /** Delete project description {@code PUT /projects/<project-id>/description}. */
    delete(PROJECT_KIND, "description").to(PutDescription.class);

    /** Get project HEAD {@code GET /projects/<project-id>/HEAD}. */
    get(PROJECT_KIND, "HEAD").to(GetHead.class);
    /** Set project HEAD {@code PUT /projects/<project-id>/HEAD}. */
    put(PROJECT_KIND, "HEAD").to(SetHead.class);

    /** Index project {@code POST /projects/<project-id>/index}. */
    post(PROJECT_KIND, "index").to(Index.class);

    /** Get project parent {@code GET /projects/<project-id>/parent}. */
    get(PROJECT_KIND, "parent").to(GetParent.class);
    /** Set project parent {@code PUT /projects/<project-id>/parent}. */
    put(PROJECT_KIND, "parent").to(SetParent.class);

    /** Delete tags in project {@code POST /projects/<project-id>/tags:delete}. */
    post(PROJECT_KIND, "tags:delete").to(DeleteTags.class);

    install(new ProjectBranchesRestApiModule());
    install(new ProjectChildrenRestApiModule());
    install(new ProjectCommitsRestApiModule());
    install(new ProjectDashboardsRestApiModule());
    install(new ProjectLabelsRestApiModule());
    install(new ProjectSubmitRequirementsRestApiModule());
    install(new ProjectTagsRestApiModule());
  }

  /** Separately bind batch functionality. */
  public static class BatchModule extends RestApiModule {
    @Override
    protected void configure() {
      /** Gc project {@code POST /projects/<project-id>/gc}. */
      post(PROJECT_KIND, "gc").to(GarbageCollect.class);
      /** Index changes in project {@code POST /projects/<project-id>/index.changes}. */
      post(PROJECT_KIND, "index.changes").to(IndexChanges.class);
      /** Get statistics for project {@code GET /projects/<project-id>/statistics.git}. */
      get(PROJECT_KIND, "statistics.git").to(GetStatistics.class);
    }
  }
}
