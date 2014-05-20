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

import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.Util.allow;
import static com.google.gerrit.server.project.Util.block;
import static org.junit.Assert.assertEquals;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class DeleteBranchIT extends AbstractDaemonTest {

  @Inject
  private MetaDataUpdate.Server metaDataUpdateFactory;

  @Inject
  private ProjectCache projectCache;

  @Inject
  private AllProjectsName allProjects;

  private Branch.NameKey branch;

  @Before
  public void setUp() throws Exception {
    branch = new Branch.NameKey(project, "test");
    adminSession.put("/projects/" + project.get()
        + "/branches/" + branch.getShortName()).consume();
  }

  @Test
  public void deleteBranch_Forbidden() throws IOException {
    RestResponse r =
        userSession.delete("/projects/" + project.get()
            + "/branches/" + branch.getShortName());
    assertEquals(HttpStatus.SC_FORBIDDEN, r.getStatusCode());
    r.consume();
  }

  @Test
  public void deleteBranchByAdmin() throws IOException {
    RestResponse r =
        adminSession.delete("/projects/" + project.get()
            + "/branches/" + branch.getShortName());
    assertEquals(HttpStatus.SC_NO_CONTENT, r.getStatusCode());
    r.consume();

    r = adminSession.get("/projects/" + project.get()
        + "/branches/" + branch.getShortName());
    assertEquals(HttpStatus.SC_NOT_FOUND, r.getStatusCode());
    r.consume();
  }

  @Test
  public void deleteBranchByProjectOwner() throws IOException,
      ConfigInvalidException {
    grantOwner();

    RestResponse r =
        userSession.delete("/projects/" + project.get()
            + "/branches/" + branch.getShortName());
    assertEquals(HttpStatus.SC_NO_CONTENT, r.getStatusCode());
    r.consume();

    r = userSession.get("/projects/" + project.get()
        + "/branches/" + branch.getShortName());
    assertEquals(HttpStatus.SC_NOT_FOUND, r.getStatusCode());
    r.consume();
  }

  @Test
  public void deleteBranchByAdminForcePushBlocked() throws IOException,
      ConfigInvalidException {
    blockForcePush();
    RestResponse r =
        adminSession.delete("/projects/" + project.get()
            + "/branches/" + branch.getShortName());
    assertEquals(HttpStatus.SC_NO_CONTENT, r.getStatusCode());
    r.consume();

    r = adminSession.get("/projects/" + project.get()
        + "/branches/" + branch.getShortName());
    assertEquals(HttpStatus.SC_NOT_FOUND, r.getStatusCode());
    r.consume();
  }

  @Test
  public void deleteBranchByProjectOwnerForcePushBlocked_Forbidden()
      throws IOException, ConfigInvalidException {
    grantOwner();
    blockForcePush();
    RestResponse r =
        userSession.delete("/projects/" + project.get()
            + "/branches/" + branch.getShortName());
    assertEquals(HttpStatus.SC_FORBIDDEN, r.getStatusCode());
    r.consume();
  }

  private void blockForcePush() throws IOException, ConfigInvalidException {
    ProjectConfig cfg = projectCache.checkedGet(allProjects).getConfig();
    block(cfg, Permission.PUSH, ANONYMOUS_USERS, "refs/heads/*").setForce(true);
    saveProjectConfig(allProjects, cfg);
    projectCache.evict(cfg.getProject());
  }

  private void grantOwner() throws IOException, ConfigInvalidException {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    allow(cfg, Permission.OWNER, REGISTERED_USERS, "refs/*");
    saveProjectConfig(project, cfg);
    projectCache.evict(cfg.getProject());
  }

  private void saveProjectConfig(Project.NameKey p, ProjectConfig cfg) throws IOException {
    MetaDataUpdate md = metaDataUpdateFactory.create(p);
    try {
      cfg.commit(md);
    } finally {
      md.close();
    }
  }
}
