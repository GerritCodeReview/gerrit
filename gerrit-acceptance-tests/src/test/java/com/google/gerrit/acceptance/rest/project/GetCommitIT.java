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
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.server.project.ListBranches.BranchInfo;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

import java.io.IOException;

public class GetCommitIT extends AbstractDaemonTest {

  @Test
  public void getCommit() throws IOException {
    RestResponse r =
        adminSession.get("/projects/" + project.get() + "/branches/"
            + IdString.fromDecoded("refs/meta/config").encoded());
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
  public void getCommit_Forbidden() throws IOException {
    RestResponse r =
        adminSession.get("/projects/" + project.get() + "/branches/"
            + IdString.fromDecoded("refs/meta/config").encoded());
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    BranchInfo branchInfo =
        newGson().fromJson(r.getReader(), BranchInfo.class);
    r.consume();

    r = userSession.get("/projects/" + project.get() + "/commits/"
        + branchInfo.revision);
    assertEquals(HttpStatus.SC_FORBIDDEN, r.getStatusCode());
  }

  @Test
  public void getCommit_NotFound() throws IOException {
    RestResponse r = adminSession.get("/projects/" + project.get() + "/commits/"
        + ObjectId.zeroId().name());
    assertEquals(HttpStatus.SC_NOT_FOUND, r.getStatusCode());
  }
}
