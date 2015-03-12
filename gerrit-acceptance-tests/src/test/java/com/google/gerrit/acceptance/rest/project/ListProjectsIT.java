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
import static com.google.gerrit.acceptance.rest.project.ProjectAssert.assertProjects;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;

import org.apache.http.HttpStatus;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

public class ListProjectsIT extends AbstractDaemonTest {

  @Inject
  private AllUsersName allUsers;

  @Test
  public void listProjects() throws Exception {
    Project.NameKey someProject = new Project.NameKey("some-project");
    createProject(someProject.get());

    RestResponse r = GET("/projects/");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    Map<String, ProjectInfo> result = toProjectInfoMap(r);
    assertProjects(Arrays.asList(allUsers, someProject, project),
        result.values());
  }

  @Test
  public void listProjectsWithBranch() throws Exception {
    RestResponse r = GET("/projects/?b=master");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    Map<String, ProjectInfo> result = toProjectInfoMap(r);
    assertThat(result.get(project.get())).isNotNull();
    assertThat(result.get(project.get()).branches).isNotNull();
    assertThat(result.get(project.get()).branches).hasSize(1);
    assertThat(result.get(project.get()).branches.get("master")).isNotNull();
  }

  @Test
  public void listProjectWithDescription() throws Exception {
    ProjectInput projectInput = new ProjectInput();
    projectInput.name = "some-project";
    projectInput.description = "Description of some-project";
    gApi.projects().create(projectInput);

    // description not be included in the results by default.
    RestResponse r = GET("/projects/");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    Map<String, ProjectInfo> result = toProjectInfoMap(r);
    assertThat(result.get(projectInput.name)).isNotNull();
    assertThat(result.get(projectInput.name).description).isNull();

    r = GET("/projects/?d");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    result = toProjectInfoMap(r);
    assertThat(result.get(projectInput.name)).isNotNull();
    assertThat(result.get(projectInput.name).description).isEqualTo(
        projectInput.description);
  }

  @Test
  public void listProjectsWithLimit() throws Exception {
    for (int i = 0; i < 5; i++) {
      createProject(new Project.NameKey("someProject" + i).get());
    }

    RestResponse r = GET("/projects/");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    Map<String, ProjectInfo> result = toProjectInfoMap(r);
    assertThat(result).hasSize(7); // 5 plus 2 existing projects: p and
                                   // All-Users

    r = GET("/projects/?n=2");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    result = toProjectInfoMap(r);
    assertThat(result).hasSize(2);
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

    assertThat(GET("/projects/?p=some&r=.*").getStatusCode()).isEqualTo(
        HttpStatus.SC_BAD_REQUEST);
    assertThat(GET("/projects/?p=some&m=some").getStatusCode()).isEqualTo(
        HttpStatus.SC_BAD_REQUEST);

    RestResponse r = GET("/projects/?p=some");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    Map<String, ProjectInfo> result = toProjectInfoMap(r);
    assertProjects(Arrays.asList(someProject, someOtherProject),
        result.values());
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

    assertThat(GET("/projects/?r=[.*some").getStatusCode()).isEqualTo(
        HttpStatus.SC_BAD_REQUEST);
    assertThat(GET("/projects/?r=.*&p=s").getStatusCode()).isEqualTo(
        HttpStatus.SC_BAD_REQUEST);
    assertThat(GET("/projects/?r=.*&m=s").getStatusCode()).isEqualTo(
        HttpStatus.SC_BAD_REQUEST);

    RestResponse r = GET("/projects/?r=.*some");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    Map<String, ProjectInfo> result = toProjectInfoMap(r);
    assertProjects(Arrays.asList(projectAwesome), result.values());

    r = GET("/projects/?r=some-project$");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    result = toProjectInfoMap(r);
    assertProjects(Arrays.asList(someProject), result.values());

    r = GET("/projects/?r=.*");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    result = toProjectInfoMap(r);
    assertProjects(Arrays.asList(someProject, someOtherProject, projectAwesome,
        project, allUsers), result.values());
  }

  @Test
  public void listProjectsWithSkip() throws Exception {
    for (int i = 0; i < 5; i++) {
      createProject(new Project.NameKey("someProject" + i).get());
    }

    RestResponse r = GET("/projects/");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    Map<String, ProjectInfo> result = toProjectInfoMap(r);
    assertThat(result).hasSize(7); // 5 plus 2 existing projects: p and
                                   // All-Users

    r = GET("/projects/?S=6");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    result = toProjectInfoMap(r);
    assertThat(result).hasSize(1);
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

    assertThat(GET("/projects/?m=some&r=.*").getStatusCode()).isEqualTo(
        HttpStatus.SC_BAD_REQUEST);
    assertThat(GET("/projects/?m=some&p=some").getStatusCode()).isEqualTo(
        HttpStatus.SC_BAD_REQUEST);

    RestResponse r = GET("/projects/?m=some");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    Map<String, ProjectInfo> result = toProjectInfoMap(r);
    assertProjects(
        Arrays.asList(someProject, someOtherProject, projectAwesome),
        result.values());
  }

  @Test
  public void listProjectsWithTree() throws Exception {
    Project.NameKey someParentProject =
        new Project.NameKey("some-parent-project");
    createProject(someParentProject.get());
    Project.NameKey someChildProject =
        new Project.NameKey("some-child-project");
    createProject(someChildProject.get(), someParentProject);

    RestResponse r = GET("/projects/?tree");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    Map<String, ProjectInfo> result = toProjectInfoMap(r);
    assertThat(result.get(someChildProject.get())).isNotNull();
    assertThat(result.get(someChildProject.get()).parent).isEqualTo(
        someParentProject.get());
  }

  @Test
  public void listProjectWithType() throws Exception {
    RestResponse r = GET("/projects/?type=PERMISSIONS");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    Map<String, ProjectInfo> result = toProjectInfoMap(r);
    assertThat(result).hasSize(1);
    assertThat(result.get(allProjects.get())).isNotNull();

    r = GET("/projects/?type=ALL");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    result = toProjectInfoMap(r);
    assertThat(result).hasSize(3);
    assertProjects(Arrays.asList(allProjects, allUsers, project),
        result.values());
  }

  private static Map<String, ProjectInfo> toProjectInfoMap(RestResponse r)
      throws IOException {
    Map<String, ProjectInfo> result =
        newGson().fromJson(r.getReader(),
            new TypeToken<Map<String, ProjectInfo>>() {}.getType());
    return result;
  }

  private RestResponse GET(String endpoint) throws IOException {
    return adminSession.get(endpoint);
  }
}
