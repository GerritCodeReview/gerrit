// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.git.receive;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.config.GerritConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

/** Tests for checking the validation of Change-Id during receive-commits. */
public class ReceiveCommitsChangeIdValidationIT extends AbstractDaemonTest {

  @Test
  public void disallowTruncatingChangeIdAcrossPatchSets() throws Exception {
    RevCommit parent = createParentCommit();

    String changeId = "I0000000000000000000000000000000000000012";
    String truncatedChangeId = "I000000000000000000000000000000000000001";

    // The initial Change PS1 is accepted
    pushFactory
        .create(
            admin.newIdent(),
            testRepo,
            "blah",
            ImmutableMap.of("foo.txt", "first patch-set"),
            changeId)
        .setParent(parent)
        .to("refs/for/master")
        .assertOkStatus();

    // The Change PS2 is rejected because the Change-Id is truncated
    pushFactory
        .create(
            admin.newIdent(),
            testRepo,
            "blah\n\nChange-Id: " + truncatedChangeId,
            ImmutableMap.of("foo.txt", "second patch-set"))
        .setParent(parent)
        .to("refs/for/master")
        .assertErrorStatus("invalid Change-Id");
  }

  @Test
  public void pushWithMissingChangeId_rejectedWithDefaultCommitMessageHook() throws Exception {
    createParentCommit();
    PushOneCommit.Result pushResult =
        pushFactory
            .create(admin.newIdent(), testRepo, /* insertChangeIdIfNotExist= */ false)
            .to("refs/for/master");
    String missingChangeIdRegex =
        "^commit [a-z0-9]+: missing Change-Id in message footer[\\s\\S]+"
            + "Hint: to automatically insert a Change-Id, install the hook:\n"
            + "f=\"\\$\\(git rev-parse --git-dir\\)/hooks/commit-msg\"; "
            + "curl -o \"\\$f\" "
            + "http://localhost:[0-9]+/tools/hooks/commit-msg ; "
            + "chmod \\+x \"\\$f\"\n"
            + "and then amend the commit:\n"
            + "  git commit --amend --no-edit\n"
            + "Finally, push your changes again\n\n$";
    assertThat(pushResult.getMessage()).matches(missingChangeIdRegex);
  }

  @Test
  @GerritConfig(name = "gerrit.installCommitMsgHookCommand", value = "Install custom hook")
  public void pushWithMissingChangeId_rejectedWithCustomCommitMessageHook() throws Exception {
    createParentCommit();
    PushOneCommit.Result pushResult =
        pushFactory
            .create(admin.newIdent(), testRepo, /* insertChangeIdIfNotExist= */ false)
            .to("refs/for/master");
    String missingChangeIdRegex =
        "^commit [a-z0-9]+: missing Change-Id in message footer[\\s\\S]+"
            + "Hint: to automatically insert a Change-Id, install the hook:\n"
            + "Install custom hook\n"
            + "and then amend the commit:\n"
            + "  git commit --amend --no-edit\n"
            + "Finally, push your changes again\n\n$";
    assertThat(pushResult.getMessage()).matches(missingChangeIdRegex);
  }

  @CanIgnoreReturnValue
  private RevCommit createParentCommit() throws Exception {
    RevCommit parent = commitBuilder().add("f.txt", "content").message("base commit").create();
    testRepo.reset(parent);
    return parent;
  }
}
