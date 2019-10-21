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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.extensions.client.ListChangesOption.MESSAGES;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.server.submit.CommitMergeStatus;
import com.google.inject.Inject;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class SubmitByCherryPickIT extends AbstractSubmit {
  @Inject private ProjectOperations projectOperations;
  @Inject private ExtensionRegistry extensionRegistry;

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.CHERRY_PICK;
  }

  @Test
  public void submitWithCherryPickIfFastForwardPossible() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change = createChange();
    submit(change.getChangeId());
    assertCherryPick(testRepo, false);
    RevCommit newHead = projectOperations.project(project).getHead("master");
    assertThat(newHead.getParent(0)).isEqualTo(change.getCommit().getParent(0));

    assertRefUpdatedEvents(initialHead, newHead);
    assertChangeMergedEvents(change.getChangeId(), newHead.name());
  }

  @Test
  public void submitWithCherryPick() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change = createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit headAfterFirstSubmit = projectOperations.project(project).getHead("master");
    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "other content");
    submit(change2.getChangeId());
    assertCherryPick(testRepo, false);
    RevCommit newHead = projectOperations.project(project).getHead("master");
    assertThat(newHead.getParentCount()).isEqualTo(1);
    assertThat(newHead.getParent(0)).isEqualTo(headAfterFirstSubmit);
    assertCurrentRevision(change2.getChangeId(), 2, newHead);
    assertSubmitter(change2.getChangeId(), 1);
    assertSubmitter(change2.getChangeId(), 2);
    assertPersonEquals(admin.newIdent(), newHead.getAuthorIdent());
    assertPersonEquals(admin.newIdent(), newHead.getCommitterIdent());

    assertRefUpdatedEvents(initialHead, headAfterFirstSubmit, headAfterFirstSubmit, newHead);
    assertChangeMergedEvents(
        change.getChangeId(), headAfterFirstSubmit.name(), change2.getChangeId(), newHead.name());
  }

  @Test
  public void changeMessageOnSubmit() throws Throwable {
    PushOneCommit.Result change = createChange();
    try (Registration registration =
        extensionRegistry
            .newRegistration()
            .add(
                (newCommitMessage, original, mergeTip, destination) ->
                    newCommitMessage + "Custom: " + destination.branch())) {
      submit(change.getChangeId());
    }
    testRepo.git().fetch().setRemote("origin").call();
    ChangeInfo info = get(change.getChangeId(), CURRENT_REVISION);
    RevCommit c = testRepo.getRevWalk().parseCommit(ObjectId.fromString(info.currentRevision));
    testRepo.getRevWalk().parseBody(c);
    assertThat(c.getFooterLines("Custom")).containsExactly("refs/heads/master");
    assertThat(c.getFooterLines(FooterConstants.REVIEWED_ON)).hasSize(1);
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithContentMerge() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change = createChange("Change 1", "a.txt", "aaa\nbbb\nccc\n");
    submit(change.getChangeId());
    RevCommit headAfterFirstSubmit = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "aaa\nbbb\nccc\nddd\n");
    submit(change2.getChangeId());
    RevCommit headAfterSecondSubmit = projectOperations.project(project).getHead("master");

    testRepo.reset(change.getCommit());
    PushOneCommit.Result change3 = createChange("Change 3", "a.txt", "bbb\nccc\n");
    submit(change3.getChangeId());
    assertCherryPick(testRepo, true);
    RevCommit headAfterThirdSubmit = projectOperations.project(project).getHead("master");
    assertThat(headAfterThirdSubmit.getParent(0)).isEqualTo(headAfterSecondSubmit);
    assertApproved(change3.getChangeId());
    assertCurrentRevision(change3.getChangeId(), 2, headAfterThirdSubmit);
    assertSubmitter(change2.getChangeId(), 1);
    assertSubmitter(change2.getChangeId(), 2);

    assertRefUpdatedEvents(
        initialHead,
        headAfterFirstSubmit,
        headAfterFirstSubmit,
        headAfterSecondSubmit,
        headAfterSecondSubmit,
        headAfterThirdSubmit);
    assertChangeMergedEvents(
        change.getChangeId(),
        headAfterFirstSubmit.name(),
        change2.getChangeId(),
        headAfterSecondSubmit.name(),
        change3.getChangeId(),
        headAfterThirdSubmit.name());
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithContentMerge_Conflict() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change = createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit newHead = projectOperations.project(project).getHead("master");
    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "other content");
    submitWithConflict(
        change2.getChangeId(),
        "Failed to submit 1 change due to the following problems:\n"
            + "Change "
            + change2.getChange().getId()
            + ": Change could not be "
            + "merged due to a path conflict. Please rebase the change locally and "
            + "upload the rebased commit for review.");

    assertThat(projectOperations.project(project).getHead("master")).isEqualTo(newHead);
    assertCurrentRevision(change2.getChangeId(), 1, change2.getCommit());
    assertNoSubmitter(change2.getChangeId(), 1);

    assertRefUpdatedEvents(initialHead, newHead);
    assertChangeMergedEvents(change.getChangeId(), newHead.name());
  }

  @Test
  public void submitOutOfOrder() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change = createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit headAfterFirstSubmit = projectOperations.project(project).getHead("master");
    testRepo.reset(initialHead);
    createChange("Change 2", "b.txt", "other content");
    PushOneCommit.Result change3 = createChange("Change 3", "c.txt", "different content");
    submit(change3.getChangeId());
    assertCherryPick(testRepo, false);
    RevCommit headAfterSecondSubmit = projectOperations.project(project).getHead("master");
    assertThat(headAfterSecondSubmit.getParent(0)).isEqualTo(headAfterFirstSubmit);
    assertApproved(change3.getChangeId());
    assertCurrentRevision(change3.getChangeId(), 2, headAfterSecondSubmit);
    assertSubmitter(change3.getChangeId(), 1);
    assertSubmitter(change3.getChangeId(), 2);

    assertRefUpdatedEvents(
        initialHead, headAfterFirstSubmit, headAfterFirstSubmit, headAfterSecondSubmit);
    assertChangeMergedEvents(
        change.getChangeId(),
        headAfterFirstSubmit.name(),
        change3.getChangeId(),
        headAfterSecondSubmit.name());
  }

  @Test
  public void submitOutOfOrder_Conflict() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change = createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit newHead = projectOperations.project(project).getHead("master");
    testRepo.reset(initialHead);
    createChange("Change 2", "b.txt", "other content");
    PushOneCommit.Result change3 = createChange("Change 3", "b.txt", "different content");
    submitWithConflict(
        change3.getChangeId(),
        "Failed to submit 1 change due to the following problems:\n"
            + "Change "
            + change3.getChange().getId()
            + ": Change could not be "
            + "merged due to a path conflict. Please rebase the change locally and "
            + "upload the rebased commit for review.");

    assertThat(projectOperations.project(project).getHead("master")).isEqualTo(newHead);
    assertCurrentRevision(change3.getChangeId(), 1, change3.getCommit());
    assertNoSubmitter(change3.getChangeId(), 1);

    assertRefUpdatedEvents(initialHead, newHead);
    assertChangeMergedEvents(change.getChangeId(), newHead.name());
  }

  @Test
  public void submitMultipleChanges() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");

    testRepo.reset(initialHead);
    PushOneCommit.Result change = createChange("Change 1", "b", "b");

    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "c", "c");

    testRepo.reset(initialHead);
    PushOneCommit.Result change3 = createChange("Change 3", "d", "d");

    approve(change.getChangeId());
    approve(change2.getChangeId());
    submit(change3.getChangeId());

    List<RevCommit> log = getRemoteLog();
    assertThat(log.get(0).getShortMessage()).isEqualTo(change3.getCommit().getShortMessage());
    assertThat(log.get(1).getId()).isEqualTo(initialHead.getId());

    assertNew(change.getChangeId());
    assertNew(change2.getChangeId());

    assertRefUpdatedEvents(initialHead, log.get(0));
    assertChangeMergedEvents(change3.getChangeId(), log.get(0).name());
  }

  @Test
  public void submitDependentNonConflictingChangesOutOfOrder() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");

    testRepo.reset(initialHead);
    PushOneCommit.Result change = createChange("Change 1", "b", "b");
    PushOneCommit.Result change2 = createChange("Change 2", "c", "c");
    assertThat(change2.getCommit().getParent(0)).isEqualTo(change.getCommit());

    // Submit succeeds; change2 is successfully cherry-picked onto head.
    submit(change2.getChangeId());
    RevCommit headAfterFirstSubmit = projectOperations.project(project).getHead("master");
    // Submit succeeds; change is successfully cherry-picked onto head
    // (which was change2's cherry-pick).
    submit(change.getChangeId());
    RevCommit headAfterSecondSubmit = projectOperations.project(project).getHead("master");

    // change is the new tip.
    List<RevCommit> log = getRemoteLog();
    assertThat(log.get(0).getShortMessage()).isEqualTo(change.getCommit().getShortMessage());
    assertThat(log.get(0).getParent(0)).isEqualTo(log.get(1));

    assertThat(log.get(1).getShortMessage()).isEqualTo(change2.getCommit().getShortMessage());
    assertThat(log.get(1).getParent(0)).isEqualTo(log.get(2));

    assertThat(log.get(2).getId()).isEqualTo(initialHead.getId());

    assertRefUpdatedEvents(
        initialHead, headAfterFirstSubmit, headAfterFirstSubmit, headAfterSecondSubmit);
    assertChangeMergedEvents(
        change2.getChangeId(),
        headAfterFirstSubmit.name(),
        change.getChangeId(),
        headAfterSecondSubmit.name());
  }

  @Test
  public void submitDependentConflictingChangesOutOfOrder() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");

    testRepo.reset(initialHead);
    PushOneCommit.Result change = createChange("Change 1", "b", "b1");
    PushOneCommit.Result change2 = createChange("Change 2", "b", "b2");
    assertThat(change2.getCommit().getParent(0)).isEqualTo(change.getCommit());

    // Submit fails; change2 contains the delta "b1" -> "b2", which cannot be
    // applied against tip.
    submitWithConflict(
        change2.getChangeId(),
        "Failed to submit 1 change due to the following problems:\n"
            + "Change "
            + change2.getChange().getId()
            + ": Change could not be "
            + "merged due to a path conflict. Please rebase the change locally and "
            + "upload the rebased commit for review.");

    ChangeInfo info3 = get(change2.getChangeId(), ListChangesOption.MESSAGES);
    assertThat(info3.status).isEqualTo(ChangeStatus.NEW);

    // Tip has not changed.
    List<RevCommit> log = getRemoteLog();
    assertThat(log.get(0)).isEqualTo(initialHead.getId());
    assertNoSubmitter(change2.getChangeId(), 1);

    assertRefUpdatedEvents();
    assertChangeMergedEvents();
  }

  @Test
  public void submitSubsetOfDependentChanges() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");

    testRepo.reset(initialHead);
    PushOneCommit.Result change = createChange("Change 1", "b", "b");
    PushOneCommit.Result change2 = createChange("Change 2", "c", "c");
    PushOneCommit.Result change3 = createChange("Change 3", "e", "e");

    // Out of the above, only submit change 3. Changes 1 and 2 are not
    // related to change 3 by topic or ancestor (due to cherrypicking!)
    approve(change2.getChangeId());
    submit(change3.getChangeId());
    RevCommit newHead = projectOperations.project(project).getHead("master");

    assertNew(change.getChangeId());
    assertNew(change2.getChangeId());

    assertRefUpdatedEvents(initialHead, newHead);
    assertChangeMergedEvents(change3.getChangeId(), newHead.name());
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitIdenticalTree() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");

    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "a");

    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "a");

    submit(change1.getChangeId());
    RevCommit headAfterFirstSubmit = projectOperations.project(project).getHead("master");
    assertThat(headAfterFirstSubmit.getShortMessage()).isEqualTo("Change 1");

    submit(change2.getChangeId(), new SubmitInput(), null, null);

    assertThat(projectOperations.project(project).getHead("master"))
        .isEqualTo(headAfterFirstSubmit);

    ChangeInfo info2 = get(change2.getChangeId(), MESSAGES);
    assertThat(info2.status).isEqualTo(ChangeStatus.MERGED);
    assertThat(Iterables.getLast(info2.messages).message)
        .isEqualTo(CommitMergeStatus.SKIPPED_IDENTICAL_TREE.getDescription());

    assertRefUpdatedEvents(initialHead, headAfterFirstSubmit);
    assertChangeMergedEvents(
        change1.getChangeId(),
        headAfterFirstSubmit.name(),
        change2.getChangeId(),
        headAfterFirstSubmit.name());
  }
}
