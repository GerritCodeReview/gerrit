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

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.change.Submit.TestSubmitInput;
import com.google.gerrit.server.git.strategy.CommitMergeStatus;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
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
    testRepo.reset(change.getCommit());
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
    assertCurrentRevision(change2.getChangeId(), 1, change2.getCommit());
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
    assertCurrentRevision(change3.getChangeId(), 1, change3.getCommit());
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

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitIdenticalTree() throws Exception {
    RevCommit initialHead = getRemoteHead();

    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "a");

    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "a");

    submit(change1.getChangeId());
    RevCommit oldHead = getRemoteHead();
    assertThat(oldHead.getShortMessage()).isEqualTo("Change 1");

    // Don't check merge result, since ref isn't updated.
    submit(change2.getChangeId(), new SubmitInput(), null, null, false);

    assertThat(getRemoteHead()).isEqualTo(oldHead);

    ChangeInfo info2 = get(change2.getChangeId());
    assertThat(info2.status).isEqualTo(ChangeStatus.MERGED);
    assertThat(Iterables.getLast(info2.messages).message)
        .isEqualTo(CommitMergeStatus.SKIPPED_IDENTICAL_TREE.getMessage());
  }

  @Test
  public void repairChangeStateAfterFailure() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change =
        createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = getRemoteHead();
    testRepo.reset(initialHead);
    PushOneCommit.Result change2 =
        createChange("Change 2", "b.txt", "other content");
    Change.Id id2 = change2.getChange().getId();
    SubmitInput failAfterRefUpdates =
        new TestSubmitInput(new SubmitInput(), true);
    submit(change2.getChangeId(), failAfterRefUpdates,
        ResourceConflictException.class, "Failing after ref updates", true);

    // Bad: ref advanced but change wasn't updated.
    PatchSet.Id psId1 = new PatchSet.Id(id2, 1);
    PatchSet.Id psId2 = new PatchSet.Id(id2, 2);
    ChangeInfo info = gApi.changes().id(id2.get()).get();
    assertThat(info.status).isEqualTo(ChangeStatus.NEW);
    assertThat(info.revisions.get(info.currentRevision)._number).isEqualTo(1);
    assertThat(getPatchSet(psId2)).isNull();

    ObjectId rev2;
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      ObjectId rev1 = repo.exactRef(psId1.toRefName()).getObjectId();
      assertThat(rev1).isNotNull();

      rev2 = repo.exactRef(psId2.toRefName()).getObjectId();
      assertThat(rev2).isNotNull();
      assertThat(rev2).isNotEqualTo(rev1);
      assertThat(rw.parseCommit(rev2).getParent(0)).isEqualTo(oldHead);

      assertThat(repo.exactRef("refs/heads/master").getObjectId())
          .isEqualTo(rev2);
    }

    submit(change2.getChangeId());

    // Change status and patch set entities were updated, and branch tip stayed
    // the same.
    info = gApi.changes().id(id2.get()).get();
    assertThat(info.status).isEqualTo(ChangeStatus.MERGED);
    assertThat(info.revisions.get(info.currentRevision)._number).isEqualTo(2);
    PatchSet ps2 = getPatchSet(psId2);
    assertThat(ps2).isNotNull();
    assertThat(ps2.getRevision().get()).isEqualTo(rev2.name());
    assertThat(Iterables.getLast(info.messages).message)
        .isEqualTo("Change has been successfully cherry-picked as "
            + rev2.name() + " by Administrator");

    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.exactRef("refs/heads/master").getObjectId())
          .isEqualTo(rev2);
    }
  }
}
