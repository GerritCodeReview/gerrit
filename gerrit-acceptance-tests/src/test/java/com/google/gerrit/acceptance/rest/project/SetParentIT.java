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
import static com.google.gerrit.acceptance.git.GitUtil.initSsh;
import static org.junit.Assert.assertEquals;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;

import com.jcraft.jsch.JSchException;

import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class SetParentIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  private RestSession adminSession;
  private RestSession userSession;
  private SshSession sshSession;

  private String project;

  @Before
  public void setUp() throws Exception {
    TestAccount admin = accounts.admin();
    adminSession = new RestSession(server, admin);

    TestAccount user = accounts.create("user", "user@example.com", "User");
    userSession = new RestSession(server, user);


    initSsh(admin);
    sshSession = new SshSession(server, admin);
    project = "p";
    createProject(sshSession, project, null, true);
  }

  @After
  public void cleanup() {
    sshSession.close();
  }

  @Test
  public void setParent_Forbidden() throws IOException, JSchException {
    String parent = "parent";
    createProject(sshSession, parent, null, true);
    RestResponse r =
        userSession.put("/projects/" + project + "/parent",
            new ParentInput(parent));
    assertEquals(HttpStatus.SC_FORBIDDEN, r.getStatusCode());
    r.consume();
  }

  @Test
  public void setParent() throws IOException, JSchException {
    String parent = "parent";
    createProject(sshSession, parent, null, true);
    RestResponse r =
        adminSession.put("/projects/" + project + "/parent",
            new ParentInput(parent));
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    r.consume();

    r = adminSession.get("/projects/" + project + "/parent");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    String newParent =
        (new Gson()).fromJson(r.getReader(),
            new TypeToken<String>() {}.getType());
    assertEquals(parent, newParent);
    r.consume();
  }

  @SuppressWarnings("unused")
  private static class ParentInput {
    String parent;

    ParentInput(String parent) {
      this.parent = parent;
    }
  }
}
