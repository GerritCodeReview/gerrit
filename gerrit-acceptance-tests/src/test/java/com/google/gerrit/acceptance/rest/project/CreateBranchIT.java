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
import static org.junit.Assert.assertEquals;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class CreateBranchIT extends AbstractDaemonTest {

  private RestSession userSession;

  private Project.NameKey project;
  private Branch.NameKey branch;

  @Before
  public void setUp() throws Exception {
    TestAccount user = accounts.create("user", "user@example.com", "User");
    userSession = new RestSession(server, user);

    project = new Project.NameKey("p");
    branch = new Branch.NameKey(project, "test");

    SshSession sshSession = new SshSession(server, admin);
    try {
      createProject(sshSession, project.get(), null, true);
    } finally {
      sshSession.close();
    }
  }

  @Test
  public void createBranch_Forbidden() throws IOException {
    RestResponse r =
        userSession.put("/projects/" + project.get()
            + "/branches/" + branch.getShortName());
    assertEquals(HttpStatus.SC_FORBIDDEN, r.getStatusCode());
  }

  @Test
  public void createBranch() throws IOException {
    RestResponse r =
        adminSession.put("/projects/" + project.get()
            + "/branches/" + branch.getShortName());
    assertEquals(HttpStatus.SC_CREATED, r.getStatusCode());
    r.consume();

    r = adminSession.get("/projects/" + project.get()
        + "/branches/" + branch.getShortName());
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
  }

  @Test
  public void branchAlreadyExists_Conflict() throws IOException {
    RestResponse r =
        adminSession.put("/projects/" + project.get()
            + "/branches/" + branch.getShortName());
    assertEquals(HttpStatus.SC_CREATED, r.getStatusCode());
    r.consume();

    r = adminSession.put("/projects/" + project.get()
        + "/branches/" + branch.getShortName());
    assertEquals(HttpStatus.SC_CONFLICT, r.getStatusCode());
  }
}
