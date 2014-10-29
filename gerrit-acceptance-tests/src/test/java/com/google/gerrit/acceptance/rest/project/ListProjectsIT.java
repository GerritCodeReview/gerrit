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

import static com.google.gerrit.acceptance.GitUtil.createProject;
import static com.google.gerrit.acceptance.rest.project.ProjectAssert.assertProjects;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
    createProject(sshSession, someProject.get());

    RestResponse r = GET("/projects/");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    Map<String, ProjectInfo> result = toProjectInfoMap(r);
    assertProjects(Arrays.asList(allUsers, someProject, project),
        result.values());
  }

  @Test
  public void listProjectsWithBranch() throws Exception {
    RestResponse r = GET("/projects/?b=master");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    Map<String, ProjectInfo> result = toProjectInfoMap(r);
    assertNotNull(result.get(project.get()));
    assertNotNull(result.get(project.get()).branches);
    assertEquals(1, result.get(project.get()).branches.size());
    assertNotNull(result.get(project.get()).branches.get("master"));
  }

  @Test
  public void listProjectWithDescription() throws Exception {
    ProjectInput projectInput = new ProjectInput();
    projectInput.name = "some-project";
    projectInput.description = "Description of some-project";
    gApi.projects().name(projectInput.name).create(projectInput);

    // description not be included in the results by default.
    RestResponse r = GET("/projects/");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    Map<String, ProjectInfo> result = toProjectInfoMap(r);
    assertNotNull(result.get(projectInput.name));
    assertNull(result.get(projectInput.name).description);

    r = GET("/projects/?d");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    result = toProjectInfoMap(r);
    assertNotNull(result.get(projectInput.name));
    assertEquals(projectInput.description,
        result.get(projectInput.name).description);
  }

  @Test
  public void listProjectsWithLimit() throws Exception {
    for (int i = 0; i < 5; i++) {
      createProject(sshSession, new Project.NameKey("someProject" + i).get());
    }

    RestResponse r = GET("/projects/");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    Map<String, ProjectInfo> result = toProjectInfoMap(r);
    assertEquals(7, result.size()); // 5 plus 2 existing projects: p and
                                    // All-Users

    r = GET("/projects/?n=2");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    result = toProjectInfoMap(r);
    assertEquals(2, result.size());
  }

  @Test
  public void listProjectsWithPrefix() throws Exception {
    Project.NameKey someProject = new Project.NameKey("some-project");
    createProject(sshSession, someProject.get());
    Project.NameKey someOtherProject =
        new Project.NameKey("some-other-project");
    createProject(sshSession, someOtherProject.get());
    Project.NameKey projectAwesome = new Project.NameKey("project-awesome");
    createProject(sshSession, projectAwesome.get());

    assertEquals(HttpStatus.SC_BAD_REQUEST,
        GET("/projects/?p=some&r=.*").getStatusCode());
    assertEquals(HttpStatus.SC_BAD_REQUEST,
        GET("/projects/?p=some&m=some").getStatusCode());

    RestResponse r = GET("/projects/?p=some");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    Map<String, ProjectInfo> result = toProjectInfoMap(r);
    assertProjects(Arrays.asList(someProject, someOtherProject),
        result.values());
  }

  @Test
  public void listProjectsWithRegex() throws Exception {
    Project.NameKey someProject = new Project.NameKey("some-project");
    createProject(sshSession, someProject.get());
    Project.NameKey someOtherProject =
        new Project.NameKey("some-other-project");
    createProject(sshSession, someOtherProject.get());
    Project.NameKey projectAwesome = new Project.NameKey("project-awesome");
    createProject(sshSession, projectAwesome.get());

    assertEquals(HttpStatus.SC_BAD_REQUEST,
        GET("/projects/?r=[.*some").getStatusCode());
    assertEquals(HttpStatus.SC_BAD_REQUEST,
        GET("/projects/?r=.*&p=s").getStatusCode());
    assertEquals(HttpStatus.SC_BAD_REQUEST,
        GET("/projects/?r=.*&m=s").getStatusCode());

    RestResponse r = GET("/projects/?r=.*some");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    Map<String, ProjectInfo> result = toProjectInfoMap(r);
    assertProjects(Arrays.asList(projectAwesome), result.values());

    r = GET("/projects/?r=some-project$");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    result = toProjectInfoMap(r);
    assertProjects(Arrays.asList(someProject), result.values());

    r = GET("/projects/?r=.*");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    result = toProjectInfoMap(r);
    assertProjects(Arrays.asList(someProject, someOtherProject, projectAwesome,
        project, allUsers), result.values());
  }

  @Test
  public void listProjectsWithSkip() throws Exception {
    for (int i = 0; i < 5; i++) {
      createProject(sshSession, new Project.NameKey("someProject" + i).get());
    }

    RestResponse r = GET("/projects/");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    Map<String, ProjectInfo> result = toProjectInfoMap(r);
    assertEquals(7, result.size()); // 5 plus 2 existing projects: p and
                                    // All-Users

    r = GET("/projects/?S=6");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    result = toProjectInfoMap(r);
    assertEquals(1, result.size());
  }

  @Test
  public void listProjectsWithSubstring() throws Exception {
    Project.NameKey someProject = new Project.NameKey("some-project");
    createProject(sshSession, someProject.get());
    Project.NameKey someOtherProject =
        new Project.NameKey("some-other-project");
    createProject(sshSession, someOtherProject.get());
    Project.NameKey projectAwesome = new Project.NameKey("project-awesome");
    createProject(sshSession, projectAwesome.get());

    assertEquals(HttpStatus.SC_BAD_REQUEST,
        GET("/projects/?m=some&r=.*").getStatusCode());
    assertEquals(HttpStatus.SC_BAD_REQUEST,
        GET("/projects/?m=some&p=some").getStatusCode());

    RestResponse r = GET("/projects/?m=some");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    Map<String, ProjectInfo> result = toProjectInfoMap(r);
    assertProjects(
        Arrays.asList(someProject, someOtherProject, projectAwesome),
        result.values());
  }

  @Test
  public void listProjectsWithTree() throws Exception {
    Project.NameKey someParentProject =
        new Project.NameKey("some-parent-project");
    createProject(sshSession, someParentProject.get());
    Project.NameKey someChildProject =
        new Project.NameKey("some-child-project");
    createProject(sshSession, someChildProject.get(), someParentProject);

    RestResponse r = GET("/projects/?tree");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    Map<String, ProjectInfo> result = toProjectInfoMap(r);
    assertNotNull(result.get(someChildProject.get()));
    assertEquals(someParentProject.get(),
        result.get(someChildProject.get()).parent);
  }

  @Test
  public void listProjectWithType() throws Exception {
    RestResponse r = GET("/projects/?type=PERMISSIONS");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    Map<String, ProjectInfo> result = toProjectInfoMap(r);
    assertEquals(1, result.size());
    assertNotNull(result.get(allProjects.get()));

    r = GET("/projects/?type=ALL");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    result = toProjectInfoMap(r);
    assertEquals(3, result.size());
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
