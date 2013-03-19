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
import static com.google.gerrit.acceptance.rest.project.ProjectAssert.assertProjectInfo;
import static org.junit.Assert.assertEquals;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;

import com.jcraft.jsch.JSchException;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class GetChildProjectIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private AllProjectsName allProjects;

  @Inject
  private ProjectCache projectCache;

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
  public void getNonExistingChildProject_NotFound() throws IOException {
    assertEquals(HttpStatus.SC_NOT_FOUND,
        GET("/projects/" + allProjects.get() + "/children/non-existing").getStatusCode());
  }

  @Test
  public void getNonChildProject_NotFound() throws IOException, JSchException {
    SshSession sshSession = new SshSession(admin);
    Project.NameKey p1 = new Project.NameKey("p1");
    createProject(sshSession, p1.get());
    Project.NameKey p2 = new Project.NameKey("p2");
    createProject(sshSession, p2.get());
    assertEquals(HttpStatus.SC_NOT_FOUND,
        GET("/projects/" + p1.get() + "/children/" + p2.get()).getStatusCode());
  }

  @Test
  public void getChildProject() throws IOException, JSchException {
    SshSession sshSession = new SshSession(admin);
    Project.NameKey child = new Project.NameKey("p1");
    createProject(sshSession, child.get());
    RestResponse r = GET("/projects/" + allProjects.get() + "/children/" + child.get());
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    ProjectInfo childInfo =
        (new Gson()).fromJson(r.getReader(),
            new TypeToken<ProjectInfo>() {}.getType());
    assertProjectInfo(projectCache.get(child).getProject(), childInfo);
  }

  @Test
  public void getGrandChildProject_NotFound() throws IOException, JSchException {
    SshSession sshSession = new SshSession(admin);
    Project.NameKey child = new Project.NameKey("p1");
    createProject(sshSession, child.get());
    Project.NameKey grandChild = new Project.NameKey("p1.1");
    createProject(sshSession, grandChild.get(), child);
    assertEquals(HttpStatus.SC_NOT_FOUND,
        GET("/projects/" + allProjects.get() + "/children/" + grandChild.get())
            .getStatusCode());
  }

  @Test
  public void getGrandChildProjectWithRecursiveFlag() throws IOException,
      JSchException {
    SshSession sshSession = new SshSession(admin);
    Project.NameKey child = new Project.NameKey("p1");
    createProject(sshSession, child.get());
    Project.NameKey grandChild = new Project.NameKey("p1.1");
    createProject(sshSession, grandChild.get(), child);
    RestResponse r =
        GET("/projects/" + allProjects.get() + "/children/" + grandChild.get()
            + "?recursive");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    ProjectInfo grandChildInfo =
        (new Gson()).fromJson(r.getReader(), new TypeToken<ProjectInfo>() {}.getType());
    assertProjectInfo(projectCache.get(grandChild).getProject(), grandChildInfo);
  }

  private RestResponse GET(String endpoint) throws IOException {
    return session.get(endpoint);
  }
}
