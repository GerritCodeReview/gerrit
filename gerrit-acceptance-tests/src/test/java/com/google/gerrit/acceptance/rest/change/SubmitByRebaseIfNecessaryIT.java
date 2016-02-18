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
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.change.Submit.TestSubmitInput;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

public class SubmitByRebaseIfNecessaryIT extends AbstractSubmit {

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.REBASE_IF_NECESSARY;
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithFastForward() throws Exception {
    RevCommit oldHead = getRemoteHead();
    PushOneCommit.Result change = createChange();
    submit(change.getChangeId());
    RevCommit head = getRemoteHead();
    assertThat(head.getId()).isEqualTo(change.getCommit());
    assertThat(head.getParent(0)).isEqualTo(oldHead);
    assertApproved(change.getChangeId());
    assertCurrentRevision(change.getChangeId(), 1, head);
    assertSubmitter(change.getChangeId(), 1);
    assertPersonEquals(admin.getIdent(), head.getAuthorIdent());
    assertPersonEquals(admin.getIdent(), head.getCommitterIdent());
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithRebase() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change =
        createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = getRemoteHead();
    testRepo.reset(initialHead);
    PushOneCommit.Result change2 =
        createChange("Change 2", "b.txt", "other content");
    submit(change2.getChangeId());
    assertRebase(testRepo, false);
    RevCommit head = getRemoteHead();
    assertThat(head.getParent(0)).isEqualTo(oldHead);
    assertApproved(change2.getChangeId());
    assertCurrentRevision(change2.getChangeId(), 2, head);
    assertSubmitter(change2.getChangeId(), 1);
    assertSubmitter(change2.getChangeId(), 2);
    assertPersonEquals(admin.getIdent(), head.getAuthorIdent());
    assertPersonEquals(admin.getIdent(), head.getCommitterIdent());
  }


  @Test
  public void submitWithRebaseMultipleChanges() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change1 =
        createChange("Change 1", "a.txt", "content");
    submit(change1.getChangeId());

    testRepo.reset(initialHead);
    PushOneCommit.Result change2 =
        createChange("Change 2", "b.txt", "other content");
    assertThat(change2.getCommit().getParent(0))
        .isNotEqualTo(change1.getCommit());
    PushOneCommit.Result change3 =
        createChange("Change 3", "c.txt", "third content");
    approve(change2.getChangeId());
    submit(change3.getChangeId());

    assertRebase(testRepo, false);
    assertApproved(change2.getChangeId());
    assertApproved(change3.getChangeId());

    RevCommit head = parse(getRemoteHead());
    assertThat(head.getShortMessage()).isEqualTo("Change 3");
    assertThat(head).isNotEqualTo(change3.getCommit());
    assertCurrentRevision(change3.getChangeId(), 2, head);

    RevCommit parent = parse(head.getParent(0));
    assertThat(parent.getShortMessage()).isEqualTo("Change 2");
    assertThat(parent).isNotEqualTo(change2.getCommit());
    assertCurrentRevision(change2.getChangeId(), 2, parent);

    RevCommit grandparent = parse(parent.getParent(0));
    assertThat(grandparent).isEqualTo(change1.getCommit());
    assertCurrentRevision(change1.getChangeId(), 1, grandparent);
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
    assertRebase(testRepo, true);
    RevCommit head = getRemoteHead();
    assertThat(head.getParent(0)).isEqualTo(oldHead);
    assertApproved(change3.getChangeId());
    assertCurrentRevision(change3.getChangeId(), 2, head);
    assertSubmitter(change3.getChangeId(), 1);
    assertSubmitter(change3.getChangeId(), 2);
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
    submitWithConflict(change2.getChangeId(), "Merge Conflict");
    RevCommit head = getRemoteHead();
    assertThat(head).isEqualTo(oldHead);
    assertCurrentRevision(change2.getChangeId(), 1, change2.getCommit());
    assertNoSubmitter(change2.getChangeId(), 1);
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
        .isEqualTo("Change has been successfully rebased as "
            + rev2.name() + " by Administrator");

    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.exactRef("refs/heads/master").getObjectId())
          .isEqualTo(rev2);
    }
  }

  private RevCommit parse(ObjectId id) throws Exception {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      RevCommit c = rw.parseCommit(id);
      rw.parseBody(c);
      return c;
    }
  }
}
