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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.projects.ConfigInfo;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.api.projects.Projects.ListRequest;
import com.google.gerrit.extensions.api.projects.Projects.ListRequest.FilterType;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.server.project.ProjectCacheImpl;
import com.google.gerrit.server.restapi.project.ListProjects;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.Test;

@NoHttpd
@Sandboxed
public class ListProjectsIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ListProjects listProjects;

  @Test
  public void listProjects() throws Exception {
    Project.NameKey someProject = projectOperations.newProject().create();
    assertThatNameList(gApi.projects().list().get())
        .containsExactly(allProjects, allUsers, project, someProject);
    assertThatNameList(gApi.projects().list().get()).isInOrder();
  }

  @Test
  public void listProjectsFiltersInvisibleProjects() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    assertThatNameList(gApi.projects().list().get()).contains(project);

    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
        .update();

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
    ProjectCacheImpl projectCacheImpl = (ProjectCacheImpl) projectCache;
    String pre = "lpwl-someProject";
    int n = 6;
    for (int i = 0; i < n; i++) {
      projectOperations.newProject().name(pre + i).create();
    }

    projectCacheImpl.evictAllByName();
    for (int i = 1; i <= n + 2; i++) {
      assertThatNameList(gApi.projects().list().withPrefix(pre).withLimit(i).get())
          .hasSize(Math.min(i, n));
      assertThat(projectCacheImpl.sizeAllByName())
          .isAtMost((long) (i + 2)); // 2 = AllProjects + AllUsers
    }
  }

  @Test
  @GerritConfig(name = "gerrit.listProjectsFromIndex", value = "true")
  public void listProjectsFromIndexShouldBeLimitedTo500() throws Exception {
    int numTestProjects = 501;
    assertThat(createProjects("foo", numTestProjects)).hasSize(numTestProjects);
    assertThat(gApi.projects().list().get()).hasSize(500);
  }

  @Test
  public void listProjectsShouldNotBeLimitedByDefault() throws Exception {
    int numTestProjects = 501;
    assertThat(createProjects("foo", numTestProjects)).hasSize(numTestProjects);
    assertThat(gApi.projects().list().get().size()).isAtLeast(numTestProjects);
  }

  @Test
  public void listProjectsToOutputStream() throws Exception {
    int numInitialProjects = gApi.projects().list().get().size();
    int numTestProjects = 5;
    List<String> testProjects = createProjects("zzz_testProject", numTestProjects);
    try (ByteArrayOutputStream displayOut = new ByteArrayOutputStream()) {

      listProjects.setStart(numInitialProjects);
      listProjects.displayToStream(displayOut);

      List<String> lines =
          Splitter.on("\n")
              .omitEmptyStrings()
              .splitToList(new String(displayOut.toByteArray(), UTF_8));
      assertThat(lines).isEqualTo(testProjects);
    }
  }

  @Test
  public void listProjectsAsJsonMultilineToOutputStream() throws Exception {
    listProjectsAsJsonToOutputStream(OutputFormat.JSON);
  }

  @Test
  public void listProjectsAsJsonCompactToOutputStream() throws Exception {
    String jsonOutput = listProjectsAsJsonToOutputStream(OutputFormat.JSON_COMPACT).trim();
    assertThat(jsonOutput).doesNotContain("\n");
  }

  private String listProjectsAsJsonToOutputStream(OutputFormat jsonFormat) throws Exception {
    assertThat(jsonFormat.isJson()).isTrue();

    int numInitialProjects = gApi.projects().list().get().size();
    int numTestProjects = 5;
    Set<String> testProjects =
        ImmutableSet.copyOf(createProjects("zzz_testProject", numTestProjects));
    try (ByteArrayOutputStream displayOut = new ByteArrayOutputStream()) {

      listProjects.setStart(numInitialProjects);
      listProjects.setFormat(jsonFormat);
      listProjects.displayToStream(displayOut);

      String projectsJsonOutput = new String(displayOut.toByteArray(), UTF_8);

      Gson gson = jsonFormat.newGson();
      Set<String> projectsJsonNames = gson.fromJson(projectsJsonOutput, JsonObject.class).keySet();
      assertThat(projectsJsonNames).isEqualTo(testProjects);

      return projectsJsonOutput;
    }
  }

  private List<String> createProjects(String prefix, int numProjects) {
    return IntStream.range(0, numProjects)
        .mapToObj(i -> projectOperations.newProject().name(prefix + i).create())
        .map(Project.NameKey::get)
        .collect(toList());
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
        .containsExactly(Project.nameKey(Iterables.getLast(all).name));
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
    assertThrows(BadRequestException.class, () -> req.get());
  }
}
