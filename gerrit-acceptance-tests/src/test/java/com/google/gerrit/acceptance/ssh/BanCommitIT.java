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

package com.google.gerrit.acceptance.ssh;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_OTHER_REASON;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import java.util.Locale;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.Test;

@NoHttpd
public class BanCommitIT extends AbstractDaemonTest {

  @Test
  public void banCommit() throws Exception {
    RevCommit c = commitBuilder().add("a.txt", "some content").create();

    String response = adminSshSession.exec("gerrit ban-commit " + project.get() + " " + c.name());
    assert_()
        .withFailureMessage(adminSshSession.getError())
        .that(adminSshSession.hasError())
        .isFalse();
    assertThat(response.toLowerCase(Locale.US)).doesNotContain("error");

    RemoteRefUpdate u =
        pushHead(testRepo, "refs/heads/master", false).getRemoteUpdate("refs/heads/master");
    assertThat(u).isNotNull();
    assertThat(u.getStatus()).isEqualTo(REJECTED_OTHER_REASON);
    assertThat(u.getMessage()).startsWith("contains banned commit");
  }
}
