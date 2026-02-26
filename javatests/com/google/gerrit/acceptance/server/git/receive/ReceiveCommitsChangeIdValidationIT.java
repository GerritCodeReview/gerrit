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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.junit.Test;

/** Tests for checking the validation of Change-Id during receive-commits. */
public class ReceiveCommitsChangeIdValidationIT extends AbstractDaemonTest {

  private static final String JJ_CHANGE_ID = "mlqnqnkrxpuvuuxzlzoltostwlwyskpx";

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

  @Test
  public void pushWithJujutsuChangeId_acceptedWithoutChangeIdFooter() throws Exception {
    resetHeadToJjCommit("Commit from Jujutsu with Change-Id header\n");

    PushResult result = GitUtil.pushHead(testRepo, "refs/for/master");
    GitUtil.assertPushOk(result, "refs/for/master");

    assertThat(gApi.changes().id(project.get() + "~master~" + JJ_CHANGE_ID).get().changeId)
        .isEqualTo(JJ_CHANGE_ID);
  }

  @Test
  public void pushWithBothJujutsuAndGerritChangeId_gerritFooterHasPriority() throws Exception {
    String gerritChangeId = "I0000000000000000000000000000000000000099";
    resetHeadToJjCommit(
        "Commit with both header and footer change IDs\n\nChange-Id: " + gerritChangeId + "\n");

    PushResult result = GitUtil.pushHead(testRepo, "refs/for/master");
    GitUtil.assertPushOk(result, "refs/for/master");

    assertThat(gApi.changes().id(gerritChangeId).get().changeId).isEqualTo(gerritChangeId);
    assertThrows(
        ResourceNotFoundException.class,
        () -> gApi.changes().id(project.get() + "~master~" + JJ_CHANGE_ID).get());
  }

  @Test
  public void pushRebasedJujutsuCommit_autoClosesChange() throws Exception {
    // Resolve the remote master tip via the API — the local clone may not have
    // refs/heads/master set up, so resolve("refs/heads/master") can return null.
    ObjectId masterTip =
        ObjectId.fromString(
            gApi.projects().name(project.get()).branch("master").get().revision);

    // Step 1: push the original JJ commit to refs/for/master to create the change.
    resetHeadToJjCommit("Commit from Jujutsu\n");
    PushResult result = GitUtil.pushHead(testRepo, "refs/for/master");
    GitUtil.assertPushOk(result, "refs/for/master");

    assertThat(gApi.changes().id(project.get() + "~master~" + JJ_CHANGE_ID).get().status)
        .isEqualTo(ChangeStatus.NEW);

    // Step 2: simulate a rebase — build a sibling commit rooted at the same master tip.
    // Same jj change-id header, different message → different SHA.
    resetHeadToJjCommit(masterTip, "Commit from Jujutsu (rebased)\n");

    // Step 3: push the rebased commit directly to refs/heads/master.
    PushResult directPush = GitUtil.pushHead(testRepo, "refs/heads/master");
    GitUtil.assertPushOk(directPush, "refs/heads/master");

    // The change must be auto-closed even though the commit SHA changed.
    assertThat(gApi.changes().id(project.get() + "~master~" + JJ_CHANGE_ID).get().status)
        .isEqualTo(ChangeStatus.MERGED);
  }

  /** Builds a raw JJ commit with HEAD as parent and resets HEAD to it. */
  private void resetHeadToJjCommit(String message) throws Exception {
    resetHeadToJjCommit(testRepo.getRepository().resolve("HEAD"), message);
  }

  /** Builds a raw JJ commit with the given parent and resets HEAD to it. */
  private void resetHeadToJjCommit(ObjectId parent, String message) throws Exception {
    PersonIdent ident = admin.newIdent();
    StringBuilder raw = new StringBuilder();
    raw.append("tree 4b825dc642cb6eb9a060e54bf8d69288fbee4904\n"); // empty tree
    if (parent != null) {
      raw.append("parent ").append(parent.name()).append("\n");
    }
    raw.append("author ").append(ident.toExternalString()).append("\n");
    raw.append("committer ").append(ident.toExternalString()).append("\n");
    raw.append("change-id ").append(JJ_CHANGE_ID).append("\n");
    raw.append("\n");
    raw.append(message);

    ObjectId commitId;
    try (ObjectInserter ins = testRepo.getRepository().newObjectInserter()) {
      commitId = ins.insert(Constants.OBJ_COMMIT, raw.toString().getBytes(UTF_8));
      ins.flush();
    }
    testRepo.reset(commitId);
  }

  @CanIgnoreReturnValue
  private RevCommit createParentCommit() throws Exception {
    RevCommit parent = commitBuilder().add("f.txt", "content").message("base commit").create();
    testRepo.reset(parent);
    return parent;
  }
}
