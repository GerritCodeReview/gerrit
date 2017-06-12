// Copyright (C) 2016 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.Test;

@NoHttpd
public class AbandonRestoreIT extends AbstractDaemonTest {

  @Test
  public void withMessage() throws Exception {
    Result result = createChange();
    String commit = result.getCommit().name();
    executeCmd(commit, "abandon", "'abandon it'");
    executeCmd(commit, "restore", "'restore it'");
    assertChangeMessages(
        result.getChangeId(),
        ImmutableList.of(
            "Uploaded patch set 1.", "Abandoned\n\nabandon it", "Restored\n\nrestore it"));
  }

  @Test
  public void withoutMessage() throws Exception {
    Result result = createChange();
    String commit = result.getCommit().name();
    executeCmd(commit, "abandon", null);
    executeCmd(commit, "restore", null);
    assertChangeMessages(
        result.getChangeId(), ImmutableList.of("Uploaded patch set 1.", "Abandoned", "Restored"));
  }

  private void executeCmd(String commit, String op, String message) throws Exception {
    StringBuilder command =
        new StringBuilder("gerrit review ").append(commit).append(" --").append(op);
    if (message != null) {
      command.append(" --message ").append(message);
    }
    String response = adminSshSession.exec(command.toString());
    assert_()
        .withFailureMessage(adminSshSession.getError())
        .that(adminSshSession.hasError())
        .isFalse();
    assertThat(response.toLowerCase(Locale.US)).doesNotContain("error");
  }

  private void assertChangeMessages(String changeId, List<String> expected) throws Exception {
    ChangeInfo c = get(changeId);
    Iterable<ChangeMessageInfo> messages = c.messages;
    assertThat(messages).isNotNull();
    assertThat(messages).hasSize(expected.size());
    List<String> actual = new ArrayList<>();
    for (ChangeMessageInfo info : messages) {
      actual.add(info.message);
    }
    assertThat(actual).containsExactlyElementsIn(expected);
  }
}
