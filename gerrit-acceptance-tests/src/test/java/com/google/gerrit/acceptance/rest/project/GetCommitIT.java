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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ListBranches.BranchInfo;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

import java.io.IOException;

public class GetCommitIT extends AbstractDaemonTest {

  @Inject
  private ProjectCache projectCache;

  @Inject
  private AllProjectsName allProjects;

  @Inject
  private MetaDataUpdate.Server metaDataUpdateFactory;

  @Test
  public void getCommit() throws IOException {
    RestResponse r =
        adminSession.get("/projects/" + project.get() + "/branches/"
            + IdString.fromDecoded(RefNames.REFS_CONFIG).encoded());
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    BranchInfo branchInfo =
        newGson().fromJson(r.getReader(), BranchInfo.class);
    r.consume();

    r = adminSession.get("/projects/" + project.get() + "/commits/"
        + branchInfo.revision);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    CommitInfo commitInfo =
        newGson().fromJson(r.getReader(), CommitInfo.class);
    assertEquals(branchInfo.revision, commitInfo.commit);
    assertEquals("Created project", commitInfo.subject);
    assertEquals("Created project\n", commitInfo.message);
    assertNotNull(commitInfo.author);
    assertEquals("Administrator", commitInfo.author.name);
    assertNotNull(commitInfo.committer);
    assertEquals("Gerrit Code Review", commitInfo.committer.name);
    assertTrue(commitInfo.parents.isEmpty());
  }

  @Test
  public void getNonExistingCommit_NotFound() throws IOException {
    RestResponse r = adminSession.get("/projects/" + project.get() + "/commits/"
        + ObjectId.zeroId().name());
    assertEquals(HttpStatus.SC_NOT_FOUND, r.getStatusCode());
  }

  @Test
  public void getNonVisibleCommit_NotFound() throws IOException {
    RestResponse r =
        adminSession.get("/projects/" + project.get() + "/branches/"
            + IdString.fromDecoded(RefNames.REFS_CONFIG).encoded());
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    BranchInfo branchInfo =
        newGson().fromJson(r.getReader(), BranchInfo.class);
    r.consume();

    ProjectConfig cfg = projectCache.checkedGet(allProjects).getConfig();
    cfg.getAccessSection("refs/*", false).removePermission(Permission.READ);
    saveProjectConfig(cfg);
    projectCache.evict(cfg.getProject());

    r = adminSession.get("/projects/" + project.get() + "/commits/"
        + branchInfo.revision);
    assertEquals(HttpStatus.SC_NOT_FOUND, r.getStatusCode());
  }

  private void saveProjectConfig(ProjectConfig cfg) throws IOException {
    MetaDataUpdate md = metaDataUpdateFactory.create(allProjects);
    try {
      cfg.commit(md);
    } finally {
      md.close();
    }
  }
}
