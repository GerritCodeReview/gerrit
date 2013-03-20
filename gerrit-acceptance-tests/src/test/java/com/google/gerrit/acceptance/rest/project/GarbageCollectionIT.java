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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.GcAssert;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GarbageCollectionQueue;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import com.jcraft.jsch.JSchException;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

public class GarbageCollectionIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private AllProjectsName allProjects;

  @Inject
  private GarbageCollectionQueue gcQueue;

  @Inject
  private GcAssert gcAssert;

  private TestAccount admin;
  private RestSession session;
  private Project.NameKey project1;
  private Project.NameKey project2;

  @Before
  public void setUp() throws Exception {
    admin =
        accounts.create("admin", "admin@example.com", "Administrator",
            "Administrators");

    SshSession sshSession = new SshSession(admin);

    project1 = new Project.NameKey("p1");
    createProject(sshSession, project1.get());

    project2 = new Project.NameKey("p2");
    createProject(sshSession, project2.get());

    session = new RestSession(admin);
  }

  @Test
  public void testGcNonExistingProject_NotFound() throws IOException {
    assertEquals(HttpStatus.SC_NOT_FOUND, POST("/projects/non-existing/gc"));
  }

  @Test
  public void testGcNotAllowed_Forbidden() throws IOException, OrmException, JSchException {
    assertEquals(HttpStatus.SC_FORBIDDEN,
        new RestSession(accounts.create("user", "user@example.com", "User"))
            .post("/projects/" + allProjects.get() + "/gc").getStatusCode());
  }

  @Test
  public void testGcOneProject() throws JSchException, IOException {
    assertEquals(HttpStatus.SC_OK, POST("/projects/" + allProjects.get() + "/gc"));
    gcAssert.assertHasPackFile(allProjects);
    gcAssert.assertHasNoPackFile(project1, project2);
  }

  @Test
  public void testGcAlreadyScheduled_Conflict() throws IOException {
    gcQueue.addAll(Collections.singletonList(project1));
    assertEquals(HttpStatus.SC_CONFLICT, POST("/projects/" + project1.get() + "/gc"));
  }

  @Test
  public void testRecursiveGc() throws JSchException, IOException {
    SshSession sshSession = new SshSession(admin);
    Project.NameKey child1_1 = new Project.NameKey("p1.1");
    createProject(sshSession, child1_1.get(), project1);
    Project.NameKey child1_2 = new Project.NameKey("p1.2");
    createProject(sshSession, child1_2.get(), project1);
    Project.NameKey child1_1_1 = new Project.NameKey("p1.1.1");
    createProject(sshSession, child1_1_1.get(), child1_1);
    Project.NameKey child1_1_1_1 = new Project.NameKey("p1.1.1.1");
    createProject(sshSession, child1_1_1_1.get(), child1_1_1);

    assertEquals(HttpStatus.SC_OK, POST("/projects/" + project1.get() + "/gc.recursive"));
    gcAssert.assertHasPackFile(project1, child1_1, child1_1_1, child1_1_1_1);
    gcAssert.assertHasNoPackFile(allProjects, project2);
  }

  private int POST(String endPoint) throws IOException {
    return session.post(endPoint).getStatusCode();
  }
}
