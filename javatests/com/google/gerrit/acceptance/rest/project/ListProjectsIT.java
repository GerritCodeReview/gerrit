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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.ConfigInfo;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.api.projects.Projects.ListRequest;
import com.google.gerrit.extensions.api.projects.Projects.ListRequest.FilterType;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.project.ProjectCacheImpl;
import com.google.gerrit.server.project.testing.Util;
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
  @Inject private ListProjects listProjects;

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

    try (ProjectConfigUpdate u = updateProject(project)) {
      Util.block(u.getConfig(), Permission.READ, REGISTERED_USERS, "refs/*");
      u.save();
    }

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
    ProjectCacheImpl projectCacheImpl = (ProjectCacheImpl) projectCache;
    for (int i = 0; i < 5; i++) {
      createProject("someProject" + i);
    }

    String p = name("");
    // 5, plus p which was automatically created.
    int n = 6;
    projectCacheImpl.evictAllByName();
    for (int i = 1; i <= n + 2; i++) {
      assertThatNameList(gApi.projects().list().withPrefix(p).withLimit(i).get())
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
        .mapToObj(
            i -> {
              String projectName = prefix + i;
              try {
                return createProject(projectName);
              } catch (RestApiException e) {
                throw new IllegalStateException("Unable to create project " + projectName, e);
              }
            })
        .map(Project.NameKey::get)
        .collect(toList());
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
    p = name("SOME");
    assertThatNameList(filter(gApi.projects().list().withPrefix(p).get())).isEmpty();
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
    assertThatNameList(filter(gApi.projects().list().withSubstring("SOME").get()))
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
    assertThat(result.keySet()).containsExactly(allProjects.get(), allUsers.get());

    assertThatNameList(filter(gApi.projects().list().withType(FilterType.ALL).get()))
        .containsExactly(allProjects, allUsers, project)
        .inOrder();
  }

  @Test
  public void listParentCandidates() throws Exception {
    Map<String, ProjectInfo> result =
        gApi.projects().list().withType(FilterType.PARENT_CANDIDATES).getAsMap();
    assertThat(result).hasSize(1);
    assertThat(result).containsKey(allProjects.get());

    // Create a new project with 'project' as parent
    Project.NameKey testProject = createProject(name("test"), project);

    // Parent candidates are All-Projects and 'project'
    assertThatNameList(filter(gApi.projects().list().withType(FilterType.PARENT_CANDIDATES).get()))
        .containsExactly(allProjects, project)
        .inOrder();

    // All projects are listed
    assertThatNameList(filter(gApi.projects().list().get()))
        .containsExactly(allProjects, allUsers, testProject, project)
        .inOrder();
  }

  @Test
  public void listWithHiddenAndReadonlyProjects() throws Exception {
    Project.NameKey hidden = createProject("project-to-hide");
    Project.NameKey readonly = createProject("project-to-read");

    // Set project read-only
    ConfigInput input = new ConfigInput();
    input.state = ProjectState.READ_ONLY;
    ConfigInfo info = gApi.projects().name(readonly.get()).config(input);
    assertThat(info.state).isEqualTo(input.state);

    // The hidden project is included because it was not hidden yet.
    // The read-only project is included.
    assertThatNameList(gApi.projects().list().get())
        .containsExactly(allProjects, allUsers, project, hidden, readonly)
        .inOrder();

    // Hide the project
    input.state = ProjectState.HIDDEN;
    info = gApi.projects().name(hidden.get()).config(input);
    assertThat(info.state).isEqualTo(input.state);

    // Project is still accessible directly
    gApi.projects().name(hidden.get()).get();

    // Hidden project is not included in the list
    assertThatNameList(gApi.projects().list().get())
        .containsExactly(allProjects, allUsers, project, readonly)
        .inOrder();

    // ALL filter applies to type, and doesn't include hidden state
    assertThatNameList(gApi.projects().list().withType(FilterType.ALL).get())
        .containsExactly(allProjects, allUsers, project, readonly)
        .inOrder();

    // "All" boolean option causes hidden projects to be included
    assertThatNameList(gApi.projects().list().withAll(true).get())
        .containsExactly(allProjects, allUsers, project, hidden, readonly)
        .inOrder();

    // "State" option causes only the projects in that state to be included
    assertThatNameList(gApi.projects().list().withState(ProjectState.HIDDEN).get())
        .containsExactly(hidden);
    assertThatNameList(gApi.projects().list().withState(ProjectState.READ_ONLY).get())
        .containsExactly(readonly);
    assertThatNameList(gApi.projects().list().withState(ProjectState.ACTIVE).get())
        .containsExactly(allProjects, allUsers, project)
        .inOrder();

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
