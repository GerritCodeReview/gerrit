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
import static com.google.common.truth.TruthJUnit.assume;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.InheritableBoolean;
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

public abstract class AbstractSubmitByMerge extends AbstractSubmit {

  @Test
  public void submitWithMerge() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change = createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = getRemoteHead();
    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "other content");
    submit(change2.getChangeId());
    RevCommit head = getRemoteHead();
    assertThat(head.getParentCount()).isEqualTo(2);
    assertThat(head.getParent(0)).isEqualTo(oldHead);
    assertThat(head.getParent(1)).isEqualTo(change2.getCommit());
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithContentMerge() throws Exception {
    PushOneCommit.Result change = createChange("Change 1", "a.txt", "aaa\nbbb\nccc\n");
    submit(change.getChangeId());
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "aaa\nbbb\nccc\nddd\n");
    submit(change2.getChangeId());

    RevCommit oldHead = getRemoteHead();
    testRepo.reset(change.getCommit());
    PushOneCommit.Result change3 = createChange("Change 3", "a.txt", "bbb\nccc\n");
    submit(change3.getChangeId());
    RevCommit head = getRemoteHead();
    assertThat(head.getParentCount()).isEqualTo(2);
    assertThat(head.getParent(0)).isEqualTo(oldHead);
    assertThat(head.getParent(1)).isEqualTo(change3.getCommit());
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithContentMerge_Conflict() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change = createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = getRemoteHead();
    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "other content");
    submitWithConflict(
        change2.getChangeId(),
        "Failed to submit 1 change due to the following problems:\n"
            + "Change "
            + change2.getChange().getId()
            + ": "
            + "Change could not be merged due to a path conflict. "
            + "Please rebase the change locally "
            + "and upload the rebased commit for review.");
    assertThat(getRemoteHead()).isEqualTo(oldHead);
  }

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void submitMultipleCommitsToEmptyRepoAsFastForward() throws Exception {
    PushOneCommit.Result change1 = createChange();
    PushOneCommit.Result change2 = createChange();
    approve(change1.getChangeId());
    submit(change2.getChangeId());
    assertThat(getRemoteHead().getId()).isEqualTo(change2.getCommit());
  }

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void submitMultipleCommitsToEmptyRepoWithOneMerge() throws Exception {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();
    PushOneCommit.Result change1 =
        pushFactory
            .create(db, admin.getIdent(), testRepo, "Change 1", "a", "a")
            .to("refs/for/master/" + name("topic"));

    PushOneCommit push2 = pushFactory.create(db, admin.getIdent(), testRepo, "Change 2", "b", "b");
    push2.noParents();
    PushOneCommit.Result change2 = push2.to("refs/for/master/" + name("topic"));
    change2.assertOkStatus();

    approve(change1.getChangeId());
    submit(change2.getChangeId());

    RevCommit head = getRemoteHead();
    assertThat(head.getParents()).hasLength(2);
    assertThat(head.getParent(0)).isEqualTo(change1.getCommit());
    assertThat(head.getParent(1)).isEqualTo(change2.getCommit());
  }

  @Test
  public void repairChangeStateAfterFailure() throws Exception {
    // In NoteDb-only mode, repo and meta updates are atomic (at least in InMemoryRepository).
    assume().that(notesMigration.disableChangeReviewDb()).isFalse();

    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change = createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());
    RevCommit afterChange1Head = getRemoteHead();

    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "other content");
    Change.Id id2 = change2.getChange().getId();
    TestSubmitInput failInput = new TestSubmitInput();
    failInput.failAfterRefUpdates = true;
    submit(
        change2.getChangeId(),
        failInput,
        ResourceConflictException.class,
        "Failing after ref updates");

    // Bad: ref advanced but change wasn't updated.
    PatchSet.Id psId1 = new PatchSet.Id(id2, 1);
    ChangeInfo info = gApi.changes().id(id2.get()).get();
    assertThat(info.status).isEqualTo(ChangeStatus.NEW);
    assertThat(info.revisions.get(info.currentRevision)._number).isEqualTo(1);

    RevCommit tip;
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      ObjectId rev1 = repo.exactRef(psId1.toRefName()).getObjectId();
      assertThat(rev1).isNotNull();

      tip = rw.parseCommit(repo.exactRef("refs/heads/master").getObjectId());
      assertThat(tip.getParentCount()).isEqualTo(2);
      assertThat(tip.getParent(0)).isEqualTo(afterChange1Head);
      assertThat(tip.getParent(1)).isEqualTo(change2.getCommit());
    }

    submit(change2.getChangeId(), new SubmitInput(), null, null);

    // Change status and patch set entities were updated, and branch tip stayed
    // the same.
    info = gApi.changes().id(id2.get()).get();
    assertThat(info.status).isEqualTo(ChangeStatus.MERGED);
    assertThat(info.revisions.get(info.currentRevision)._number).isEqualTo(1);
    assertThat(Iterables.getLast(info.messages).message)
        .isEqualTo("Change has been successfully merged by Administrator");

    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.exactRef("refs/heads/master").getObjectId()).isEqualTo(tip);
    }
  }
}
