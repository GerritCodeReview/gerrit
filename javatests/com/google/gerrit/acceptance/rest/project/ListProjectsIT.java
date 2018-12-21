// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.rest.project.ProjectAssert.assertThatNameList;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.ConfigInfo;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.api.projects.Projects.ListRequest;
import com.google.gerrit.extensions.api.projects.Projects.ListRequest.FilterType;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.testing.Util;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.Test;

@NoHttpd
@Sandboxed
public class ListProjectsIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;

  @Test
  public void listProjects() throws Exception {
    Project.NameKey someProject = projectOperations.newProject().create();
    assertThatNameList(gApi.projects().list().get())
        .containsExactly(allProjects, allUsers, project, someProject);
    assertThatNameList(gApi.projects().list().get()).isOrdered();
  }

  @Test
  public void listProjectsWithQuery() throws Exception {
    Project.NameKey someProject = createProject("some-project");
    assertThatNameList(filter(gApi.projects().list().get()))
        .containsExactly(allProjects, allUsers, project, someProject)
        .inOrder();
  }

  @Test
  public void listProjectsFiltersInvisibleProjects() throws Exception {
    setApiUser(user);
    assertThatNameList(gApi.projects().list().get()).contains(project);

    try (ProjectConfigUpdate u = updateProject(project)) {
      Util.block(u.getConfig(), Permission.READ, REGISTERED_USERS, "refs/*");
      u.save();
    }

    assertThatNameList(gApi.projects().list().get()).doesNotContain(project);
  }

  @Test
  public void listProjectsWithBranch() throws Exception {
    Map<String, ProjectInfo> result = gApi.projects().list().addShowBranch("master").getAsMap();
    assertThat(result).containsKey(project.get());
    ProjectInfo info = result.get(project.get());
    assertThat(info.branches).isNotNull();
    assertThat(info.branches).hasSize(1);
    assertThat(info.branches.get("master")).isNotNull();
  }

  @Test
  @TestProjectInput(description = "Description of some-project")
  public void listProjectWithDescription() throws Exception {
    // description not be included in the results by default.
    Map<String, ProjectInfo> result = gApi.projects().list().getAsMap();
    assertThat(result).containsKey(project.get());
    assertThat(result.get(project.get()).description).isNull();

    result = gApi.projects().list().withDescription(true).getAsMap();
    assertThat(result).containsKey(project.get());
    assertThat(result.get(project.get()).description).isEqualTo("Description of some-project");
  }

  @Test
  public void listProjectsWithLimit() throws Exception {
    String pre = "lpwl-someProject";
    int n = 6;
    for (int i = 0; i < n; i++) {
      projectOperations.newProject().name(pre + i).create();
    }

    for (int i = 1; i <= n + 2; i++) {
      assertThatNameList(gApi.projects().list().withPrefix(pre).withLimit(i).get())
          .hasSize(Math.min(i, n));
    }
  }

  @Test
  public void listProjectsWithPrefix() throws Exception {
    Project.NameKey someProject = projectOperations.newProject().name("listtest-p1").create();
    Project.NameKey someOtherProject = projectOperations.newProject().name("listtest-p2").create();
    projectOperations.newProject().name("other-prefix-project").create();

    String p = "listtest";
    assertBadRequest(gApi.projects().list().withPrefix(p).withRegex(".*"));
    assertBadRequest(gApi.projects().list().withPrefix(p).withSubstring(p));
    assertThatNameList(gApi.projects().list().withPrefix(p).get())
        .containsExactly(someOtherProject, someProject);
    p = "notlisttest";
    assertThatNameList(gApi.projects().list().withPrefix(p).get()).isEmpty();
  }

  @Test
  public void listProjectsWithRegex() throws Exception {
    Project.NameKey someProject = projectOperations.newProject().name("lpwr-some-project").create();
    Project.NameKey someOtherProject =
        projectOperations.newProject().name("lpwr-some-other-project").create();
    Project.NameKey projectAwesome =
        projectOperations.newProject().name("lpwr-project-awesome").create();

    assertBadRequest(gApi.projects().list().withRegex("[.*"));
    assertBadRequest(gApi.projects().list().withRegex(".*").withPrefix("p"));
    assertBadRequest(gApi.projects().list().withRegex(".*").withSubstring("p"));

    assertThatNameList(gApi.projects().list().withRegex(".*some").get())
        .containsExactly(projectAwesome);
    String r = ("lpwr-some-project$").replace(".", "\\.");
    assertThatNameList(gApi.projects().list().withRegex(r).get()).containsExactly(someProject);
    assertThatNameList(gApi.projects().list().withRegex(".*").get())
        .containsExactly(
            allProjects, allUsers, project, projectAwesome, someOtherProject, someProject);
  }

  @Test
  public void listProjectsWithStart() throws Exception {
    String pre = "lpws-";
    for (int i = 0; i < 5; i++) {
      projectOperations.newProject().name(pre + i).create();
    }

    List<ProjectInfo> all = gApi.projects().list().withPrefix(pre).get();
    int n = 5;
    assertThat(all).hasSize(n);
    assertThatNameList(gApi.projects().list().withPrefix(pre).withStart(n - 1).get())
        .containsExactly(new Project.NameKey(Iterables.getLast(all).name));
  }

  @Test
  public void listProjectsWithSubstring() throws Exception {
    Project.NameKey someProject = projectOperations.newProject().name("some-project").create();
    Project.NameKey someOtherProject =
        projectOperations.newProject().name("some-other-project").create();
    Project.NameKey projectAwesome =
        projectOperations.newProject().name("project-awesome").create();

    assertBadRequest(gApi.projects().list().withSubstring("some").withRegex(".*"));
    assertBadRequest(gApi.projects().list().withSubstring("some").withPrefix("some"));
    assertThatNameList(gApi.projects().list().withSubstring("some").get())
        .containsExactly(projectAwesome, someOtherProject, someProject);
    assertThatNameList(gApi.projects().list().withSubstring("SOME").get())
        .containsExactly(projectAwesome, someOtherProject, someProject);
  }

  @Test
  public void listProjectsWithTree() throws Exception {
    Project.NameKey someParentProject =
        projectOperations.newProject().name("some-parent-project").create();
    Project.NameKey someChildProject =
        projectOperations
            .newProject()
            .name("some-child-project")
            .parent(someParentProject)
            .create();

    Map<String, ProjectInfo> result = gApi.projects().list().withTree(true).getAsMap();
    assertThat(result).containsKey(someChildProject.get());
    assertThat(result.get(someChildProject.get()).parent).isEqualTo(someParentProject.get());
  }

  @Test
  public void listProjectWithType() throws Exception {
    Map<String, ProjectInfo> result =
        gApi.projects().list().withType(FilterType.PERMISSIONS).getAsMap();
    assertThat(result.keySet()).containsExactly(allProjects.get(), allUsers.get());

    assertThatNameList(gApi.projects().list().withType(FilterType.ALL).get())
        .containsExactly(allProjects, allUsers, project);
  }

  @Test
  public void listWithHiddenAndReadonlyProjects() throws Exception {
    Project.NameKey hidden = projectOperations.newProject().create();
    Project.NameKey readonly = projectOperations.newProject().create();

    // Set project read-only
    ConfigInput input = new ConfigInput();
    input.state = ProjectState.READ_ONLY;
    ConfigInfo info = gApi.projects().name(readonly.get()).config(input);
    assertThat(info.state).isEqualTo(input.state);

    // The hidden project is included because it was not hidden yet.
    // The read-only project is included.
    assertThatNameList(gApi.projects().list().get())
        .containsExactly(allProjects, allUsers, project, hidden, readonly);

    // Hide the project
    input.state = ProjectState.HIDDEN;
    info = gApi.projects().name(hidden.get()).config(input);
    assertThat(info.state).isEqualTo(input.state);

    // Project is still accessible directly
    gApi.projects().name(hidden.get()).get();

    // Hidden project is not included in the list
    assertThatNameList(gApi.projects().list().get())
        .containsExactly(allProjects, allUsers, project, readonly);

    // ALL filter applies to type, and doesn't include hidden state
    assertThatNameList(gApi.projects().list().withType(FilterType.ALL).get())
        .containsExactly(allProjects, allUsers, project, readonly);

    // "All" boolean option causes hidden projects to be included
    assertThatNameList(gApi.projects().list().withAll(true).get())
        .containsExactly(allProjects, allUsers, project, hidden, readonly);

    // "State" option causes only the projects in that state to be included
    assertThatNameList(gApi.projects().list().withState(ProjectState.HIDDEN).get())
        .containsExactly(hidden);
    assertThatNameList(gApi.projects().list().withState(ProjectState.READ_ONLY).get())
        .containsExactly(readonly);
    assertThatNameList(gApi.projects().list().withState(ProjectState.ACTIVE).get())
        .containsExactly(allProjects, allUsers, project);

    // Cannot use "all" and "state" together
    assertBadRequest(gApi.projects().list().withAll(true).withState(ProjectState.ACTIVE));
  }

  private void assertBadRequest(ListRequest req) throws Exception {
    try {
      req.get();
      fail("Expected BadRequestException");
    } catch (BadRequestException expected) {
      // Expected.
    }
  }
}
