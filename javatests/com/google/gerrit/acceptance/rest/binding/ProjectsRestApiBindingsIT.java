// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.binding;

import static com.google.gerrit.acceptance.GitUtil.assertPushOk;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.acceptance.rest.util.RestCall.Method.GET;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.entities.RefNames.REFS_DASHBOARDS;
import static com.google.gerrit.server.restapi.project.DashboardsCollection.DEFAULT_DASHBOARD_NAME;
import static org.apache.http.HttpStatus.SC_METHOD_NOT_ALLOWED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.rest.util.RestApiCallHelper;
import com.google.gerrit.acceptance.rest.util.RestCall;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.TagInput;
import com.google.inject.Inject;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.junit.Test;

/**
 * Tests for checking the bindings of the projects REST API.
 *
 * <p>These tests only verify that the project REST endpoints are correctly bound, they do no test
 * the functionality of the project REST endpoints.
 */
public class ProjectsRestApiBindingsIT extends AbstractDaemonTest {
  private static final ImmutableList<RestCall> PROJECT_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/projects/%s"),
          RestCall.put("/projects/%s"),
          RestCall.get("/projects/%s/description"),
          RestCall.put("/projects/%s/description"),
          RestCall.delete("/projects/%s/description"),
          RestCall.get("/projects/%s/parent"),
          RestCall.put("/projects/%s/parent"),
          RestCall.get("/projects/%s/config"),
          RestCall.put("/projects/%s/config"),
          RestCall.get("/projects/%s/HEAD"),
          RestCall.put("/projects/%s/HEAD"),
          RestCall.get("/projects/%s/access"),
          RestCall.post("/projects/%s/access"),
          RestCall.put("/projects/%s/access:review"),
          RestCall.get("/projects/%s/check.access"),
          RestCall.put("/projects/%s/ban"),
          RestCall.get("/projects/%s/statistics.git"),
          RestCall.post("/projects/%s/index"),
          RestCall.post("/projects/%s/gc"),
          RestCall.post("/projects/%s/create.change"),
          RestCall.get("/projects/%s/children"),
          RestCall.get("/projects/%s/branches"),
          RestCall.post("/projects/%s/branches:delete"),
          RestCall.put("/projects/%s/branches/new-branch"),
          RestCall.get("/projects/%s/labels"),
          RestCall.get("/projects/%s/tags"),
          RestCall.post("/projects/%s/tags:delete"),
          RestCall.put("/projects/%s/tags/new-tag"),
          RestCall.builder(GET, "/projects/%s/commits")
              // GET /projects/<project>/branches/<branch>/commits is not implemented
              .expectedResponseCode(SC_NOT_FOUND)
              .build(),
          RestCall.get("/projects/%s/dashboards"),
          RestCall.put("/projects/%s/labels/new-label"),
          RestCall.post("/projects/%s/labels/"));

  /**
   * Child project REST endpoints to be tested, each URL contains placeholders for the parent
   * project identifier and the child project identifier.
   */
  private static final ImmutableList<RestCall> CHILD_PROJECT_ENDPOINTS =
      ImmutableList.of(RestCall.get("/projects/%s/children/%s"));

  /**
   * Branch REST endpoints to be tested, each URL contains placeholders for the project identifier
   * and the branch identifier.
   */
  private static final ImmutableList<RestCall> BRANCH_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/projects/%s/branches/%s"),
          RestCall.put("/projects/%s/branches/%s"),
          RestCall.get("/projects/%s/branches/%s/mergeable"),
          RestCall.builder(GET, "/projects/%s/branches/%s/reflog")
              // The tests use DfsRepository which does not support getting the reflog.
              .expectedResponseCode(SC_METHOD_NOT_ALLOWED)
              .expectedMessage("reflog not supported on")
              .build(),
          RestCall.builder(GET, "/projects/%s/branches/%s/files")
              // GET /projects/<project>/branches/<branch>/files is not implemented
              .expectedResponseCode(SC_NOT_FOUND)
              .build(),

          // Branch deletion must be tested last
          RestCall.delete("/projects/%s/branches/%s"));

  /**
   * Branch file REST endpoints to be tested, each URL contains placeholders for the project
   * identifier, the branch identifier and the file identifier.
   */
  private static final ImmutableList<RestCall> BRANCH_FILE_ENDPOINTS =
      ImmutableList.of(RestCall.get("/projects/%s/branches/%s/files/%s/content"));

  /**
   * Dashboard REST endpoints to be tested, each URL contains placeholders for the project
   * identifier and the dashboard identifier.
   */
  private static final ImmutableList<RestCall> DASHBOARD_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/projects/%s/dashboards/%s"),
          RestCall.put("/projects/%s/dashboards/%s"),

          // Dashboard deletion must be tested last
          RestCall.delete("/projects/%s/dashboards/%s"));

  /**
   * Tag REST endpoints to be tested, each URL contains placeholders for the project identifier and
   * the tag identifier.
   */
  private static final ImmutableList<RestCall> TAG_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/projects/%s/tags/%s"),
          RestCall.put("/projects/%s/tags/%s"),
          RestCall.delete("/projects/%s/tags/%s"));

  /**
   * Commit REST endpoints to be tested, each URL contains placeholders for the project identifier
   * and the commit identifier.
   */
  private static final ImmutableList<RestCall> COMMIT_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/projects/%s/commits/%s"),
          RestCall.get("/projects/%s/commits/%s/in"),
          RestCall.get("/projects/%s/commits/%s/files"),
          RestCall.post("/projects/%s/commits/%s/cherrypick"));

  /**
   * Commit file REST endpoints to be tested, each URL contains placeholders for the project
   * identifier, the commit identifier and the file identifier.
   */
  private static final ImmutableList<RestCall> COMMIT_FILE_ENDPOINTS =
      ImmutableList.of(RestCall.get("/projects/%s/commits/%s/files/%s/content"));

  /**
   * Label REST endpoints to be tested, each URL contains placeholders for the project identifier
   * and the label name.
   */
  private static final ImmutableList<RestCall> LABEL_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/projects/%s/labels/%s"),
          RestCall.put("/projects/%s/labels/%s"),

          // Label deletion must be tested last
          RestCall.delete("/projects/%s/labels/%s"));

  private static final String FILENAME = "test.txt";
  @Inject private ProjectOperations projectOperations;

  @Test
  public void projectEndpoints() throws Exception {
    RestApiCallHelper.execute(adminRestSession, PROJECT_ENDPOINTS, project.get());
  }

  @Test
  public void childProjectEndpoints() throws Exception {
    Project.NameKey childProject = projectOperations.newProject().parent(project).create();
    RestApiCallHelper.execute(
        adminRestSession, CHILD_PROJECT_ENDPOINTS, project.get(), childProject.get());
  }

  @Test
  public void branchEndpoints() throws Exception {
    RestApiCallHelper.execute(adminRestSession, BRANCH_ENDPOINTS, project.get(), "master");
  }

  @Test
  public void branchFileEndpoints() throws Exception {
    createAndSubmitChange(FILENAME);
    RestApiCallHelper.execute(
        adminRestSession, BRANCH_FILE_ENDPOINTS, project.get(), "master", FILENAME);
  }

  @Test
  public void dashboardEndpoints() throws Exception {
    createDefaultDashboard();
    RestApiCallHelper.execute(
        adminRestSession, DASHBOARD_ENDPOINTS, project.get(), DEFAULT_DASHBOARD_NAME);
  }

  @Test
  public void tagEndpoints() throws Exception {
    String tag = "test-tag";
    gApi.projects().name(project.get()).tag(tag).create(new TagInput());
    RestApiCallHelper.execute(adminRestSession, TAG_ENDPOINTS, project.get(), tag);
  }

  @Test
  public void commitEndpoints() throws Exception {
    String commit = createAndSubmitChange(FILENAME);
    RestApiCallHelper.execute(adminRestSession, COMMIT_ENDPOINTS, project.get(), commit);
  }

  @Test
  public void commitFileEndpoints() throws Exception {
    String commit = createAndSubmitChange(FILENAME);
    RestApiCallHelper.execute(
        adminRestSession, COMMIT_FILE_ENDPOINTS, project.get(), commit, FILENAME);
  }

  @Test
  public void labelEndpoints() throws Exception {
    String label = "Foo-Review";
    configLabel(label, LabelFunction.NO_OP);
    RestApiCallHelper.execute(adminRestSession, LABEL_ENDPOINTS, project.get(), label);
  }

  private String createAndSubmitChange(String filename) throws Exception {
    RevCommit c =
        testRepo
            .commit()
            .message("A change")
            .parent(projectOperations.project(project).getHead("master"))
            .add(filename, "content")
            .insertChangeId()
            .create();
    String id = GitUtil.getChangeId(testRepo, c).get();
    testRepo.reset(c);

    String r = "refs/for/master";
    PushResult pr = pushHead(testRepo, r, false);
    assertPushOk(pr, r);

    gApi.changes().id(id).current().review(ReviewInput.approve());
    gApi.changes().id(id).current().submit();
    return c.name();
  }

  private void createDefaultDashboard() throws Exception {
    String dashboardRef = REFS_DASHBOARDS + "team";
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.CREATE).ref("refs/meta/*").group(adminGroupUuid()))
        .update();
    gApi.projects().name(project.get()).branch(dashboardRef).create(new BranchInput());

    try (Repository r = repoManager.openRepository(project);
        TestRepository<Repository> tr = new TestRepository<>(r)) {
      TestRepository<Repository>.CommitBuilder cb = tr.branch(dashboardRef).commit();
      StringBuilder content = new StringBuilder("[dashboard]\n");
      content.append("title = ").append("Open Changes").append("\n");
      content.append("[section \"").append("open").append("\"]\n");
      content.append("query = ").append("is:open").append("\n");
      cb.add("overview", content.toString());
      cb.create();
    }

    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateProject(p -> p.setLocalDefaultDashboard(dashboardRef + ":overview"));
      u.save();
    }
  }
}
