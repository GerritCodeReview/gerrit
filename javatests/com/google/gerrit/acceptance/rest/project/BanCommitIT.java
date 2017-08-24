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
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_OTHER_REASON;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.server.project.BanCommit;
import com.google.gerrit.server.project.BanCommit.BanResultInfo;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.Test;

public class BanCommitIT extends AbstractDaemonTest {

  @Test
  public void banCommit() throws Exception {
    RevCommit c = commitBuilder().add("a.txt", "some content").create();

    RestResponse r =
        adminRestSession.put(
            "/projects/" + project.get() + "/ban/", BanCommit.Input.fromCommits(c.name()));
    r.assertOK();
    BanResultInfo info = newGson().fromJson(r.getReader(), BanResultInfo.class);
    assertThat(Iterables.getOnlyElement(info.newlyBanned)).isEqualTo(c.name());
    assertThat(info.alreadyBanned).isNull();
    assertThat(info.ignored).isNull();

    RemoteRefUpdate u =
        pushHead(testRepo, "refs/heads/master", false).getRemoteUpdate("refs/heads/master");
    assertThat(u).isNotNull();
    assertThat(u.getStatus()).isEqualTo(REJECTED_OTHER_REASON);
    assertThat(u.getMessage()).startsWith("contains banned commit");
  }

  @Test
  public void banAlreadyBannedCommit() throws Exception {
    RestResponse r =
        adminRestSession.put(
            "/projects/" + project.get() + "/ban/",
            BanCommit.Input.fromCommits("a8a477efffbbf3b44169bb9a1d3a334cbbd9aa96"));
    r.consume();

    r =
        adminRestSession.put(
            "/projects/" + project.get() + "/ban/",
            BanCommit.Input.fromCommits("a8a477efffbbf3b44169bb9a1d3a334cbbd9aa96"));
    r.assertOK();
    BanResultInfo info = newGson().fromJson(r.getReader(), BanResultInfo.class);
    assertThat(Iterables.getOnlyElement(info.alreadyBanned))
        .isEqualTo("a8a477efffbbf3b44169bb9a1d3a334cbbd9aa96");
    assertThat(info.newlyBanned).isNull();
    assertThat(info.ignored).isNull();
  }

  @Test
  public void banCommit_Forbidden() throws Exception {
    userRestSession
        .put(
            "/projects/" + project.get() + "/ban/",
            BanCommit.Input.fromCommits("a8a477efffbbf3b44169bb9a1d3a334cbbd9aa96"))
        .assertForbidden();
  }
}
