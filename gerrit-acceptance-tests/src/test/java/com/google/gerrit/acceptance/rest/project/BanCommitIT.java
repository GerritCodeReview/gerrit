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

import static com.google.gerrit.acceptance.GitUtil.add;
import static com.google.gerrit.acceptance.GitUtil.createCommit;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil.Commit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.server.project.BanCommit;
import com.google.gerrit.server.project.BanCommit.BanResultInfo;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.transport.PushResult;
import org.junit.Test;

public class BanCommitIT extends AbstractDaemonTest {

  @Test
  public void banCommit() throws Exception {
    add(git, "a.txt", "some content");
    Commit c = createCommit(git, admin.getIdent(), "subject");

    RestResponse r =
        adminSession.put("/projects/" + project.get() + "/ban/",
            BanCommit.Input.fromCommits(c.getCommit().getName()));
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    BanResultInfo info = newGson().fromJson(r.getReader(), BanResultInfo.class);
    assertEquals(c.getCommit().getName(), Iterables.getOnlyElement(info.newlyBanned));
    assertNull(info.alreadyBanned);
    assertNull(info.ignored);

    PushResult pushResult = pushHead(git, "refs/heads/master", false);
    assertTrue(pushResult.getRemoteUpdate("refs/heads/master").getMessage()
        .startsWith("contains banned commit"));
  }

  @Test
  public void banAlreadyBannedCommit() throws Exception {
    RestResponse r =
        adminSession.put("/projects/" + project.get() + "/ban/",
            BanCommit.Input.fromCommits("a8a477efffbbf3b44169bb9a1d3a334cbbd9aa96"));
    r.consume();

    r = adminSession.put("/projects/" + project.get() + "/ban/",
        BanCommit.Input.fromCommits("a8a477efffbbf3b44169bb9a1d3a334cbbd9aa96"));
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    BanResultInfo info = newGson().fromJson(r.getReader(), BanResultInfo.class);
    assertEquals("a8a477efffbbf3b44169bb9a1d3a334cbbd9aa96", Iterables.getOnlyElement(info.alreadyBanned));
    assertNull(info.newlyBanned);
    assertNull(info.ignored);
  }

  @Test
  public void banCommit_Forbidden() throws Exception {
    RestResponse r =
        userSession.put("/projects/" + project.get() + "/ban/",
            BanCommit.Input.fromCommits("a8a477efffbbf3b44169bb9a1d3a334cbbd9aa96"));
    assertEquals(HttpStatus.SC_FORBIDDEN, r.getStatusCode());
  }
}
