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
import static com.google.gerrit.acceptance.GitUtil.pushHead;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.change.Submit.TestSubmitInput;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.junit.Test;

public class SubmitByFastForwardIT extends AbstractSubmit {

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.FAST_FORWARD_ONLY;
  }

  @Test
  public void submitWithFastForward() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change = createChange();
    submit(change.getChangeId());
    RevCommit updatedHead = getRemoteHead();
    assertThat(updatedHead.getId()).isEqualTo(change.getCommit());
    assertThat(updatedHead.getParent(0)).isEqualTo(initialHead);
    assertSubmitter(change.getChangeId(), 1);

    assertRefUpdatedEvents(initialHead, updatedHead);
    assertChangeMergedEvents(change.getChangeId(), updatedHead.name());
  }

  @Test
  public void submitMultipleChangesWithFastForward() throws Exception {
    RevCommit initialHead = getRemoteHead();

    PushOneCommit.Result change = createChange();
    PushOneCommit.Result change2 = createChange();
    PushOneCommit.Result change3 = createChange();

    String id1 = change.getChangeId();
    String id2 = change2.getChangeId();
    String id3 = change3.getChangeId();
    approve(id1);
    approve(id2);
    submit(id3);

    RevCommit updatedHead = getRemoteHead();
    assertThat(updatedHead.getId()).isEqualTo(change3.getCommit());
    assertThat(updatedHead.getParent(0).getId()).isEqualTo(change2.getCommit());
    assertSubmitter(change.getChangeId(), 1);
    assertSubmitter(change2.getChangeId(), 1);
    assertSubmitter(change3.getChangeId(), 1);
    assertPersonEquals(admin.getIdent(), updatedHead.getAuthorIdent());
    assertPersonEquals(admin.getIdent(), updatedHead.getCommitterIdent());
    assertSubmittedTogether(id1, id3, id2, id1);
    assertSubmittedTogether(id2, id3, id2, id1);
    assertSubmittedTogether(id3, id3, id2, id1);

    assertRefUpdatedEvents(initialHead, updatedHead);
    assertChangeMergedEvents(
        id1, updatedHead.name(), id2, updatedHead.name(), id3, updatedHead.name());
  }

  @Test
  public void submitTwoChangesWithFastForward_missingDependency() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change1 = createChange();
    PushOneCommit.Result change2 = createChange();

    Change.Id id1 = change1.getPatchSetId().getParentKey();
    submitWithConflict(
        change2.getChangeId(),
        "Failed to submit 2 changes due to the following problems:\n"
            + "Change "
            + id1
            + ": needs Code-Review");

    RevCommit updatedHead = getRemoteHead();
    assertThat(updatedHead.getId()).isEqualTo(initialHead.getId());
    assertRefUpdatedEvents();
    assertChangeMergedEvents();
  }

  @Test
  public void submitFastForwardNotPossible_Conflict() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change = createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit headAfterFirstSubmit = getRemoteHead();
    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "other content");

    approve(change2.getChangeId());
    Map<String, ActionInfo> actions = getActions(change2.getChangeId());

    assertThat(actions).containsKey("submit");
    ActionInfo info = actions.get("submit");
    assertThat(info.enabled).isNull();

    submitWithConflict(
        change2.getChangeId(),
        "Failed to submit 1 change due to the following problems:\n"
            + "Change "
            + change2.getChange().getId()
            + ": Project policy requires "
            + "all submissions to be a fast-forward. Please rebase the change "
            + "locally and upload again for review.");
    assertThat(getRemoteHead()).isEqualTo(headAfterFirstSubmit);
    assertSubmitter(change.getChangeId(), 1);

    assertRefUpdatedEvents(initialHead, headAfterFirstSubmit);
    assertChangeMergedEvents(change.getChangeId(), headAfterFirstSubmit.name());
  }

  @Test
  public void repairChangeStateAfterFailure() throws Exception {
    // In NoteDb-only mode, repo and meta updates are atomic (at least in InMemoryRepository).
    assume().that(notesMigration.disableChangeReviewDb()).isFalse();

    PushOneCommit.Result change = createChange("Change 1", "a.txt", "content");
    Change.Id id = change.getChange().getId();
    TestSubmitInput failInput = new TestSubmitInput();
    failInput.failAfterRefUpdates = true;
    submit(
        change.getChangeId(),
        failInput,
        ResourceConflictException.class,
        "Failing after ref updates");

    // Bad: ref advanced but change wasn't updated.
    PatchSet.Id psId = new PatchSet.Id(id, 1);
    ChangeInfo info = gApi.changes().id(id.get()).get();
    assertThat(info.status).isEqualTo(ChangeStatus.NEW);
    assertThat(info.revisions.get(info.currentRevision)._number).isEqualTo(1);

    ObjectId rev;
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      rev = repo.exactRef(psId.toRefName()).getObjectId();
      assertThat(rev).isNotNull();
      assertThat(repo.exactRef("refs/heads/master").getObjectId()).isEqualTo(rev);
    }

    submit(change.getChangeId());

    // Change status was updated, and branch tip stayed the same.
    info = gApi.changes().id(id.get()).get();
    assertThat(info.status).isEqualTo(ChangeStatus.MERGED);
    assertThat(info.revisions.get(info.currentRevision)._number).isEqualTo(1);
    assertThat(Iterables.getLast(info.messages).message)
        .isEqualTo("Change has been successfully merged by Administrator");

    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.exactRef("refs/heads/master").getObjectId()).isEqualTo(rev);
    }

    assertRefUpdatedEvents();
    assertChangeMergedEvents(change.getChangeId(), getRemoteHead().name());
  }

  @Test
  public void submitSameCommitsAsInExperimentalBranch() throws Exception {
    RevCommit initialHead = getRemoteHead();

    grant(project, "refs/heads/*", Permission.CREATE);
    grant(project, "refs/heads/experimental", Permission.PUSH);

    RevCommit c1 = commitBuilder().add("b.txt", "1").message("commit at tip").create();
    String id1 = GitUtil.getChangeId(testRepo, c1).get();

    PushResult r1 = pushHead(testRepo, "refs/for/master", false);
    assertThat(r1.getRemoteUpdate("refs/for/master").getNewObjectId()).isEqualTo(c1.getId());

    PushResult r2 = pushHead(testRepo, "refs/heads/experimental", false);
    assertThat(r2.getRemoteUpdate("refs/heads/experimental").getNewObjectId())
        .isEqualTo(c1.getId());

    submit(id1);
    RevCommit headAfterSubmit = getRemoteHead();

    assertThat(getRemoteHead().getId()).isEqualTo(c1.getId());
    assertSubmitter(id1, 1);

    assertRefUpdatedEvents(initialHead, headAfterSubmit);
    assertChangeMergedEvents(id1, headAfterSubmit.name());
  }
}
