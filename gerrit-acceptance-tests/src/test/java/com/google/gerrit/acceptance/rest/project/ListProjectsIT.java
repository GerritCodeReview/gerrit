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
import static org.junit.Assert.fail;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.Projects.ListRequest;
import com.google.gerrit.extensions.api.projects.Projects.ListRequest.FilterType;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.Util;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.Test;

@NoHttpd
public class ListProjectsIT extends AbstractDaemonTest {

  @Inject private AllUsersName allUsers;

  @Test
  public void listProjects() throws Exception {
    Project.NameKey someProject = createProject("some-project");
    assertThatNameList(filter(gApi.projects().list().get()))
        .containsExactly(allProjects, allUsers, project, someProject)
        .inOrder();
  }

  @Test
  public void listProjectsFiltersInvisibleProjects() throws Exception {
    setApiUser(user);
    assertThatNameList(gApi.projects().list().get()).contains(project);

    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    Util.block(cfg, Permission.READ, REGISTERED_USERS, "refs/*");
    saveProjectConfig(project, cfg);

    assertThatNameList(filter(gApi.projects().list().get())).doesNotContain(project);
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
    for (int i = 0; i < 5; i++) {
      createProject("someProject" + i);
    }

    String p = name("");
    // 5, plus p which was automatically created.
    int n = 6;
    for (int i = 1; i <= n + 2; i++) {
      assertThatNameList(gApi.projects().list().withPrefix(p).withLimit(i).get())
          .hasSize(Math.min(i, n));
    }
  }

  @Test
  public void listProjectsWithPrefix() throws Exception {
    Project.NameKey someProject = createProject("some-project");
    Project.NameKey someOtherProject = createProject("some-other-project");
    createProject("project-awesome");

    String p = name("some");
    assertBadRequest(gApi.projects().list().withPrefix(p).withRegex(".*"));
    assertBadRequest(gApi.projects().list().withPrefix(p).withSubstring(p));
    assertThatNameList(filter(gApi.projects().list().withPrefix(p).get()))
        .containsExactly(someOtherProject, someProject)
        .inOrder();
  }

  @Test
  public void listProjectsWithRegex() throws Exception {
    Project.NameKey someProject = createProject("some-project");
    Project.NameKey someOtherProject = createProject("some-other-project");
    Project.NameKey projectAwesome = createProject("project-awesome");

    assertBadRequest(gApi.projects().list().withRegex("[.*"));
    assertBadRequest(gApi.projects().list().withRegex(".*").withPrefix("p"));
    assertBadRequest(gApi.projects().list().withRegex(".*").withSubstring("p"));

    assertThatNameList(filter(gApi.projects().list().withRegex(".*some").get()))
        .containsExactly(projectAwesome);
    String r = name("some-project$").replace(".", "\\.");
    assertThatNameList(filter(gApi.projects().list().withRegex(r).get()))
        .containsExactly(someProject);
    assertThatNameList(filter(gApi.projects().list().withRegex(".*").get()))
        .containsExactly(
            allProjects, allUsers, project, projectAwesome, someOtherProject, someProject)
        .inOrder();
  }

  @Test
  public void listProjectsWithStart() throws Exception {
    for (int i = 0; i < 5; i++) {
      createProject(new Project.NameKey("someProject" + i).get());
    }

    String p = name("");
    List<ProjectInfo> all = gApi.projects().list().withPrefix(p).get();
    // 5, plus p which was automatically created.
    int n = 6;
    assertThat(all).hasSize(n);
    assertThatNameList(gApi.projects().list().withPrefix(p).withStart(n - 1).get())
        .containsExactly(new Project.NameKey(Iterables.getLast(all).name));
  }

  @Test
  public void listProjectsWithSubstring() throws Exception {
    Project.NameKey someProject = createProject("some-project");
    Project.NameKey someOtherProject = createProject("some-other-project");
    Project.NameKey projectAwesome = createProject("project-awesome");

    assertBadRequest(gApi.projects().list().withSubstring("some").withRegex(".*"));
    assertBadRequest(gApi.projects().list().withSubstring("some").withPrefix("some"));
    assertThatNameList(filter(gApi.projects().list().withSubstring("some").get()))
        .containsExactly(projectAwesome, someOtherProject, someProject)
        .inOrder();
  }

  @Test
  public void listProjectsWithTree() throws Exception {
    Project.NameKey someParentProject = createProject("some-parent-project");
    Project.NameKey someChildProject = createProject("some-child-project", someParentProject);

    Map<String, ProjectInfo> result = gApi.projects().list().withTree(true).getAsMap();
    assertThat(result).containsKey(someChildProject.get());
    assertThat(result.get(someChildProject.get()).parent).isEqualTo(someParentProject.get());
  }

  @Test
  public void listProjectWithType() throws Exception {
    Map<String, ProjectInfo> result =
        gApi.projects().list().withType(FilterType.PERMISSIONS).getAsMap();
    assertThat(result).hasSize(1);
    assertThat(result).containsKey(allProjects.get());

    assertThatNameList(filter(gApi.projects().list().withType(FilterType.ALL).get()))
        .containsExactly(allProjects, allUsers, project)
        .inOrder();
  }

  private void assertBadRequest(ListRequest req) throws Exception {
    try {
      req.get();
      fail("Expected BadRequestException");
    } catch (BadRequestException expected) {
      // Expected.
    }
  }

  private Iterable<ProjectInfo> filter(Iterable<ProjectInfo> infos) {
    String prefix = name("");
    return Iterables.filter(
        infos,
        p -> {
          return p.name != null
              && (p.name.equals(allProjects.get())
                  || p.name.equals(allUsers.get())
                  || p.name.startsWith(prefix));
        });
  }
}
