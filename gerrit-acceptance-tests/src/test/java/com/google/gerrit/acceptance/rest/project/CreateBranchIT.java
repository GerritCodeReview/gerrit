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
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.Util.block;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.server.git.ProjectConfig;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;

public class CreateBranchIT extends AbstractDaemonTest {
  private Branch.NameKey branch;

  @Before
  public void setUp() throws Exception {
    branch = new Branch.NameKey(project, "test");
  }

  @Test
  public void createBranch_Forbidden() throws Exception {
    RestResponse r =
        userSession.put("/projects/" + project.get()
            + "/branches/" + branch.getShortName());
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void createBranchByAdmin() throws Exception {
    RestResponse r =
        adminSession.put("/projects/" + project.get()
            + "/branches/" + branch.getShortName());
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_CREATED);
    r.consume();

    r = adminSession.get("/projects/" + project.get()
        + "/branches/" + branch.getShortName());
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
  }

  @Test
  public void branchAlreadyExists_Conflict() throws Exception {
    RestResponse r =
        adminSession.put("/projects/" + project.get()
            + "/branches/" + branch.getShortName());
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_CREATED);
    r.consume();

    r = adminSession.put("/projects/" + project.get()
        + "/branches/" + branch.getShortName());
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_CONFLICT);
  }

  @Test
  public void createBranchByProjectOwner() throws Exception {
    grantOwner();

    RestResponse r =
        userSession.put("/projects/" + project.get()
            + "/branches/" + branch.getShortName());
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_CREATED);
    r.consume();

    r = adminSession.get("/projects/" + project.get()
        + "/branches/" + branch.getShortName());
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
  }

  @Test
  public void createBranchByAdminCreateReferenceBlocked() throws Exception {
    blockCreateReference();
    RestResponse r =
        adminSession.put("/projects/" + project.get()
            + "/branches/" + branch.getShortName());
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_CREATED);
    r.consume();

    r = adminSession.get("/projects/" + project.get()
        + "/branches/" + branch.getShortName());
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
  }

  @Test
  public void createBranchByProjectOwnerCreateReferenceBlocked_Forbidden()
      throws Exception {
    grantOwner();
    blockCreateReference();
    RestResponse r =
        userSession.put("/projects/" + project.get()
            + "/branches/" + branch.getShortName());
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_FORBIDDEN);
  }

  private void blockCreateReference() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(allProjects).getConfig();
    block(cfg, Permission.CREATE, ANONYMOUS_USERS, "refs/*");
    saveProjectConfig(allProjects, cfg);
  }

  private void grantOwner() throws Exception {
    allow(Permission.OWNER, REGISTERED_USERS, "refs/*");
  }
}
