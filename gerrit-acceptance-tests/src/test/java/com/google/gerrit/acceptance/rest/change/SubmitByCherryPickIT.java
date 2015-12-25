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

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;

import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

import java.util.List;

public class SubmitByCherryPickIT extends AbstractSubmit {

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.CHERRY_PICK;
  }

  @Test
  public void submitWithCherryPickIfFastForwardPossible() throws Exception {
    PushOneCommit.Result change = createChange();
    submit(change.getChangeId());
    assertCherryPick(testRepo, false);
    assertThat(getRemoteHead().getParent(0))
      .isEqualTo(change.getCommit().getParent(0));
  }

  @Test
  public void submitWithCherryPick() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change =
        createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = getRemoteHead();
    testRepo.reset(initialHead);
    PushOneCommit.Result change2 =
        createChange("Change 2", "b.txt", "other content");
    submit(change2.getChangeId());
    assertCherryPick(testRepo, false);
    RevCommit newHead = getRemoteHead();
    assertThat(newHead.getParentCount()).isEqualTo(1);
    assertThat(newHead.getParent(0)).isEqualTo(oldHead);
    assertCurrentRevision(change2.getChangeId(), 2, newHead);
    assertSubmitter(change2.getChangeId(), 1);
    assertSubmitter(change2.getChangeId(), 2);
    assertPersonEquals(admin.getIdent(), newHead.getAuthorIdent());
    assertPersonEquals(admin.getIdent(), newHead.getCommitterIdent());
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithContentMerge() throws Exception {
    PushOneCommit.Result change =
        createChange("Change 1", "a.txt", "aaa\nbbb\nccc\n");
    submit(change.getChangeId());
    PushOneCommit.Result change2 =
        createChange("Change 2", "a.txt", "aaa\nbbb\nccc\nddd\n");
    submit(change2.getChangeId());

    RevCommit oldHead = getRemoteHead();
    testRepo.reset(change.getCommitId());
    PushOneCommit.Result change3 =
        createChange("Change 3", "a.txt", "bbb\nccc\n");
    submit(change3.getChangeId());
    assertCherryPick(testRepo, true);
    RevCommit newHead = getRemoteHead();
    assertThat(newHead.getParent(0)).isEqualTo(oldHead);
    assertApproved(change3.getChangeId());
    assertCurrentRevision(change3.getChangeId(), 2, newHead);
    assertSubmitter(change2.getChangeId(), 1);
    assertSubmitter(change2.getChangeId(), 2);
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithContentMerge_Conflict() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change =
        createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = getRemoteHead();
    testRepo.reset(initialHead);
    PushOneCommit.Result change2 =
        createChange("Change 2", "a.txt", "other content");
    submitWithConflict(change2.getChangeId(),
        "Failed to submit 1 change due to the following problems:\n" +
        "Change " + change2.getChange().getId() + ": Change could not be " +
        "merged due to a path conflict. Please rebase the change locally and " +
        "upload the rebased commit for review.");

    assertThat(getRemoteHead()).isEqualTo(oldHead);
    assertCurrentRevision(change2.getChangeId(), 1, change2.getCommitId());
    assertNoSubmitter(change2.getChangeId(), 1);
  }

  @Test
  public void submitOutOfOrder() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change =
        createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = getRemoteHead();
    testRepo.reset(initialHead);
    createChange("Change 2", "b.txt", "other content");
    PushOneCommit.Result change3 =
        createChange("Change 3", "c.txt", "different content");
    submit(change3.getChangeId());
    assertCherryPick(testRepo, false);
    RevCommit newHead = getRemoteHead();
    assertThat(newHead.getParent(0)).isEqualTo(oldHead);
    assertApproved(change3.getChangeId());
    assertCurrentRevision(change3.getChangeId(), 2, newHead);
    assertSubmitter(change3.getChangeId(), 1);
    assertSubmitter(change3.getChangeId(), 2);
  }

  @Test
  public void submitOutOfOrder_Conflict() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change =
        createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = getRemoteHead();
    testRepo.reset(initialHead);
    createChange("Change 2", "b.txt", "other content");
    PushOneCommit.Result change3 =
        createChange("Change 3", "b.txt", "different content");
    submitWithConflict(change3.getChangeId(),
        "Failed to submit 1 change due to the following problems:\n" +
        "Change " + change3.getChange().getId() + ": Change could not be " +
        "merged due to a path conflict. Please rebase the change locally and " +
        "upload the rebased commit for review.");

    assertThat(getRemoteHead()).isEqualTo(oldHead);
    assertCurrentRevision(change3.getChangeId(), 1, change3.getCommitId());
    assertNoSubmitter(change3.getChangeId(), 1);
  }

  @Test
  public void submitMultipleChanges() throws Exception {
    RevCommit initialHead = getRemoteHead();

    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "b", "b");

    testRepo.reset(initialHead);
    PushOneCommit.Result change3 = createChange("Change 3", "c", "c");

    testRepo.reset(initialHead);
    PushOneCommit.Result change4 = createChange("Change 4", "d", "d");

    approve(change2.getChangeId());
    approve(change3.getChangeId());
    submit(change4.getChangeId());

    List<RevCommit> log = getRemoteLog();
    assertThat(log.get(0).getShortMessage()).isEqualTo(
        change4.getCommit().getShortMessage());
    assertThat(log.get(1).getId()).isEqualTo(initialHead.getId());

    assertNew(change2.getChangeId());
    assertNew(change3.getChangeId());
  }

  @Test
  public void submitDependentNonConflictingChangesOutOfOrder() throws Exception {
    RevCommit initialHead = getRemoteHead();

    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "b", "b");
    PushOneCommit.Result change3 = createChange("Change 3", "c", "c");
    assertThat(change3.getCommit().getParent(0)).isEqualTo(change2.getCommit());

    // Submit succeeds; change3 is successfully cherry-picked onto head.
    submit(change3.getChangeId());
    // Submit succeeds; change2 is successfully cherry-picked onto head
    // (which was change3's cherry-pick).
    submit(change2.getChangeId());

    // change2 is the new tip.
    List<RevCommit> log = getRemoteLog();
    assertThat(log.get(0).getShortMessage()).isEqualTo(
        change2.getCommit().getShortMessage());
    assertThat(log.get(0).getParent(0)).isEqualTo(log.get(1));

    assertThat(log.get(1).getShortMessage()).isEqualTo(
        change3.getCommit().getShortMessage());
    assertThat(log.get(1).getParent(0)).isEqualTo(log.get(2));

    assertThat(log.get(2).getId()).isEqualTo(initialHead.getId());
  }

  @Test
  public void submitDependentConflictingChangesOutOfOrder() throws Exception {
    RevCommit initialHead = getRemoteHead();

    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "b", "b1");
    PushOneCommit.Result change3 = createChange("Change 3", "b", "b2");
    assertThat(change3.getCommit().getParent(0)).isEqualTo(change2.getCommit());

    // Submit fails; change3 contains the delta "b1" -> "b2", which cannot be
    // applied against tip.
    submitWithConflict(change3.getChangeId(),
        "Failed to submit 1 change due to the following problems:\n" +
        "Change " + change3.getChange().getId() + ": Change could not be " +
        "merged due to a path conflict. Please rebase the change locally and " +
        "upload the rebased commit for review.");

    ChangeInfo info3 = get(change3.getChangeId(), ListChangesOption.MESSAGES);
    assertThat(info3.status).isEqualTo(ChangeStatus.NEW);

    // Tip has not changed.
    List<RevCommit> log = getRemoteLog();
    assertThat(log.get(0)).isEqualTo(initialHead.getId());
    assertNoSubmitter(change3.getChangeId(), 1);
  }

  @Test
  public void submitSubsetOfDependentChanges() throws Exception {
    RevCommit initialHead = getRemoteHead();

    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "b", "b");
    PushOneCommit.Result change3 = createChange("Change 3", "c", "c");
    PushOneCommit.Result change4 = createChange("Change 5", "e", "e");

    // Out of the above, only submit 4. 2,3 are not related to 4
    // by topic or ancestor (due to cherrypicking!)
    approve(change3.getChangeId());
    submit(change4.getChangeId());

    assertNew(change2.getChangeId());
    assertNew(change3.getChangeId());
  }
}
