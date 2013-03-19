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

import static com.google.gerrit.acceptance.git.GitUtil.createProject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static com.google.gerrit.acceptance.rest.project.ProjectAssert.assertProjects;

import com.google.gson.reflect.TypeToken;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gson.Gson;
import com.google.inject.Inject;

import com.jcraft.jsch.JSchException;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class ListChildProjectsIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private AllProjectsName allProjects;

  private TestAccount admin;
  private RestSession session;

  @Before
  public void setUp() throws Exception {
    admin =
        accounts.create("admin", "admin@example.com", "Administrator",
            "Administrators");
    session = new RestSession(admin);
  }

  @Test
  public void listChildrenOfNonExistingProject_NotFound() throws IOException {
    assertEquals(HttpStatus.SC_NOT_FOUND,
        GET("/projects/non-existing/children/").getStatusCode());
  }

  @Test
  public void listNoChildren() throws IOException {
    RestResponse r = GET("/projects/" + allProjects.get() + "/children/");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    List<ProjectInfo> children =
        (new Gson()).fromJson(r.getReader(),
            new TypeToken<List<ProjectInfo>>() {}.getType());
    assertTrue(children.isEmpty());
  }

  @Test
  public void listChildren() throws IOException, JSchException {
    SshSession sshSession = new SshSession(admin);
    Project.NameKey child1 = new Project.NameKey("p1");
    createProject(sshSession, child1.get());
    Project.NameKey child2 = new Project.NameKey("p2");
    createProject(sshSession, child2.get());
    createProject(sshSession, "p1.1", child1);

    RestResponse r = GET("/projects/" + allProjects.get() + "/children/");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    List<ProjectInfo> children =
        (new Gson()).fromJson(r.getReader(),
            new TypeToken<List<ProjectInfo>>() {}.getType());
    assertProjects(Arrays.asList(child1, child2), children);
  }

  @Test
  public void listChildrenRecursively() throws IOException, JSchException {
    SshSession sshSession = new SshSession(admin);
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
    List<ProjectInfo> children =
        (new Gson()).fromJson(r.getReader(),
            new TypeToken<List<ProjectInfo>>() {}.getType());
    assertProjects(Arrays.asList(child1_1, child1_2, child1_1_1, child1_1_1_1), children);
  }

  private RestResponse GET(String endpoint) throws IOException {
    return session.get(endpoint);
  }
}
