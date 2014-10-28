// Copyright (C) 2013 The Android Open Source Project
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
import static org.junit.Assert.assertTrue;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gson.reflect.TypeToken;

import org.apache.http.HttpStatus;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class ListChildProjectsIT extends AbstractDaemonTest {

  @Test
  public void listChildrenOfNonExistingProject_NotFound() throws Exception {
    assertEquals(HttpStatus.SC_NOT_FOUND,
        GET("/projects/non-existing/children/").getStatusCode());
  }

  @Test
  public void listNoChildren() throws Exception {
    RestResponse r = GET("/projects/" + allProjects.get() + "/children/");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    List<ProjectInfo> projectInfoList = toProjectInfoList(r);
    // Project 'p' was already created in the base class
    assertTrue(projectInfoList.size() == 2);
  }

  @Test
  public void listChildren() throws Exception {
    Project.NameKey existingProject = new Project.NameKey("p");
    Project.NameKey child1 = new Project.NameKey("p1");
    createProject(sshSession, child1.get());
    Project.NameKey child2 = new Project.NameKey("p2");
    createProject(sshSession, child2.get());
    createProject(sshSession, "p1.1", child1);

    RestResponse r = GET("/projects/" + allProjects.get() + "/children/");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertProjects(
        Arrays.asList(
            new Project.NameKey("All-Users"),
            existingProject, child1, child2),
        toProjectInfoList(r));
  }

  @Test
  public void listChildrenRecursively() throws Exception {
    Project.NameKey child1 = new Project.NameKey("p1");
    createProject(sshSession, child1.get());
    createProject(sshSession, "p2");
    Project.NameKey child1_1 = new Project.NameKey("p1.1");
    createProject(sshSession, child1_1.get(), child1);
    Project.NameKey child1_2 = new Project.NameKey("p1.2");
    createProject(sshSession, child1_2.get(), child1);
    Project.NameKey child1_1_1 = new Project.NameKey("p1.1.1");
    createProject(sshSession, child1_1_1.get(), child1_1);
    Project.NameKey child1_1_1_1 = new Project.NameKey("p1.1.1.1");
    createProject(sshSession, child1_1_1_1.get(), child1_1_1);

    RestResponse r = GET("/projects/" + child1.get() + "/children/?recursive");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertProjects(Arrays.asList(child1_1, child1_2,
        child1_1_1, child1_1_1_1), toProjectInfoList(r));
  }

  private static List<ProjectInfo> toProjectInfoList(RestResponse r)
      throws IOException {
    return newGson().fromJson(r.getReader(),
        new TypeToken<List<ProjectInfo>>() {}.getType());
  }

  private RestResponse GET(String endpoint) throws IOException {
    return adminSession.get(endpoint);
  }
}
