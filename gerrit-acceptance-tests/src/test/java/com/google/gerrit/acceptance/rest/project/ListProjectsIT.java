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
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.api.projects.Projects.ListRequest;
import com.google.gerrit.extensions.api.projects.Projects.ListRequest.FilterType;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.Util;
import com.google.inject.Inject;

import org.junit.Test;

import java.util.List;
import java.util.Map;

@NoHttpd
public class ListProjectsIT extends AbstractDaemonTest {

  @Inject
  private AllUsersName allUsers;

  @Test
  public void listProjects() throws Exception {
    Project.NameKey someProject = new Project.NameKey("some-project");
    createProject(someProject.get());
    assertThatNameList(gApi.projects().list().get())
        .containsExactly(allProjects, allUsers, project, someProject).inOrder();
  }

  @Test
  public void listProjectsFiltersInvisibleProjects() throws Exception {
    setApiUser(user);
    assertThatNameList(gApi.projects().list().get()).contains(project);

    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    Util.block(cfg, Permission.READ, REGISTERED_USERS, "refs/*");
    saveProjectConfig(project, cfg);

    assertThatNameList(gApi.projects().list().get()).doesNotContain(project);
  }

  @Test
  public void listProjectsWithBranch() throws Exception {
    Map<String, ProjectInfo> result = gApi.projects().list()
        .addShowBranch("master").getAsMap();
    assertThat(result).containsKey(project.get());
    ProjectInfo info = result.get(project.get());
    assertThat(info.branches).isNotNull();
    assertThat(info.branches).hasSize(1);
    assertThat(info.branches.get("master")).isNotNull();
  }

  @Test
  public void listProjectWithDescription() throws Exception {
    ProjectInput projectInput = new ProjectInput();
    projectInput.name = "some-project";
    projectInput.description = "Description of some-project";
    gApi.projects().create(projectInput);

    // description not be included in the results by default.
    Map<String, ProjectInfo> result = gApi.projects().list().getAsMap();
    assertThat(result).containsKey(projectInput.name);
    assertThat(result.get(projectInput.name).description).isNull();

    result = gApi.projects().list().withDescription(true).getAsMap();
    assertThat(result).containsKey(projectInput.name);
    assertThat(result.get(projectInput.name).description).isEqualTo(
        projectInput.description);
  }

  @Test
  public void listProjectsWithLimit() throws Exception {
    for (int i = 0; i < 5; i++) {
      createProject(new Project.NameKey("someProject" + i).get());
    }

    // 5 plus All-Projects, All-Users, and p.
    int n = 8;
    for (int i = 1; i <= n + 2; i++) {
      assertThat(gApi.projects().list().withLimit(i).get())
          .hasSize(Math.min(i, n));
    }
  }

  @Test
  public void listProjectsWithPrefix() throws Exception {
    Project.NameKey someProject = new Project.NameKey("some-project");
    createProject(someProject.get());
    Project.NameKey someOtherProject =
        new Project.NameKey("some-other-project");
    createProject(someOtherProject.get());
    Project.NameKey projectAwesome = new Project.NameKey("project-awesome");
    createProject(projectAwesome.get());

    assertBadRequest(gApi.projects().list().withPrefix("some").withRegex(".*"));
    assertBadRequest(gApi.projects().list().withPrefix("some")
        .withSubstring("some"));
    assertThatNameList(gApi.projects().list().withPrefix("some").get())
        .containsExactly(someOtherProject, someProject).inOrder();
  }

  @Test
  public void listProjectsWithRegex() throws Exception {
    Project.NameKey someProject = new Project.NameKey("some-project");
    createProject(someProject.get());
    Project.NameKey someOtherProject =
        new Project.NameKey("some-other-project");
    createProject(someOtherProject.get());
    Project.NameKey projectAwesome = new Project.NameKey("project-awesome");
    createProject(projectAwesome.get());

    assertBadRequest(gApi.projects().list().withRegex("[.*"));
    assertBadRequest(gApi.projects().list().withRegex(".*").withPrefix("p"));
    assertBadRequest(gApi.projects().list().withRegex(".*").withSubstring("p"));

    assertThatNameList(gApi.projects().list().withRegex(".*some").get())
        .containsExactly(projectAwesome);
    assertThatNameList(gApi.projects().list().withRegex("some-project$").get())
        .containsExactly(someProject);
    assertThatNameList(gApi.projects().list().withRegex(".*").get())
        .containsExactly(allProjects, allUsers, project, projectAwesome,
            someOtherProject, someProject)
        .inOrder();
  }

  @Test
  public void listProjectsWithStart() throws Exception {
    for (int i = 0; i < 5; i++) {
      createProject(new Project.NameKey("someProject" + i).get());
    }

    List<ProjectInfo> all = gApi.projects().list().get();
    // 5 plus All-Projects, All-Users, and p.
    int n = 8;
    assertThat(all).hasSize(n);
    assertThatNameList(gApi.projects().list().withStart(n - 1).get())
        .containsExactly(new Project.NameKey(Iterables.getLast(all).name));
  }

  @Test
  public void listProjectsWithSubstring() throws Exception {
    Project.NameKey someProject = new Project.NameKey("some-project");
    createProject(someProject.get());
    Project.NameKey someOtherProject =
        new Project.NameKey("some-other-project");
    createProject(someOtherProject.get());
    Project.NameKey projectAwesome = new Project.NameKey("project-awesome");
    createProject(projectAwesome.get());

    assertBadRequest(gApi.projects().list().withSubstring("some")
        .withRegex(".*"));
    assertBadRequest(gApi.projects().list().withSubstring("some")
        .withPrefix("some"));
    assertThatNameList(gApi.projects().list().withSubstring("some").get())
        .containsExactly(projectAwesome, someOtherProject, someProject)
        .inOrder();
  }

  @Test
  public void listProjectsWithTree() throws Exception {
    Project.NameKey someParentProject =
        new Project.NameKey("some-parent-project");
    createProject(someParentProject.get());
    Project.NameKey someChildProject =
        new Project.NameKey("some-child-project");
    createProject(someChildProject.get(), someParentProject);

    Map<String, ProjectInfo> result = gApi.projects().list().withTree(true)
        .getAsMap();
    assertThat(result).containsKey(someChildProject.get());
    assertThat(result.get(someChildProject.get()).parent)
        .isEqualTo(someParentProject.get());
  }

  @Test
  public void listProjectWithType() throws Exception {
    Map<String, ProjectInfo> result = gApi.projects().list()
        .withType(FilterType.PERMISSIONS).getAsMap();
    assertThat(result).hasSize(1);
    assertThat(result).containsKey(allProjects.get());

    assertThatNameList(gApi.projects().list().withType(FilterType.ALL).get())
        .containsExactly(allProjects, allUsers, project).inOrder();
  }

  private static void assertBadRequest(ListRequest req) throws Exception {
    try {
      req.get();
    } catch (BadRequestException expected) {
      // Expected.
    }
  }
}
