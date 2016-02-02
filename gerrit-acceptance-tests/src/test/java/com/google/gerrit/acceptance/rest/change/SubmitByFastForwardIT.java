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
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.change.Submit.TestSubmitInput;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

import java.util.Map;

public class SubmitByFastForwardIT extends AbstractSubmit {

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.FAST_FORWARD_ONLY;
  }

  @Test
  public void submitWithFastForward() throws Exception {
    RevCommit oldHead = getRemoteHead();
    PushOneCommit.Result change = createChange();
    submit(change.getChangeId());
    RevCommit head = getRemoteHead();
    assertThat(head.getId()).isEqualTo(change.getCommitId());
    assertThat(head.getParent(0)).isEqualTo(oldHead);
    assertSubmitter(change.getChangeId(), 1);
  }

  @Test
  public void submitTwoChangesWithFastForward() throws Exception {
    PushOneCommit.Result change = createChange();
    PushOneCommit.Result change2 = createChange();

    String id1 = change.getChangeId();
    String id2 = change2.getChangeId();
    approve(id1);
    submit(id2);

    RevCommit head = getRemoteHead();
    assertThat(head.getId()).isEqualTo(change2.getCommitId());
    assertThat(head.getParent(0).getId()).isEqualTo(change.getCommitId());
    assertSubmitter(change.getChangeId(), 1);
    assertSubmitter(change2.getChangeId(), 1);
    assertPersonEquals(admin.getIdent(), head.getAuthorIdent());
    assertPersonEquals(admin.getIdent(), head.getCommitterIdent());
    assertSubmittedTogether(id1, id2, id1);
    assertSubmittedTogether(id2, id2, id1);
  }

  @Test
  public void submitTwoChangesWithFastForward_missingDependency() throws Exception {
    RevCommit oldHead = getRemoteHead();
    PushOneCommit.Result change1 = createChange();
    PushOneCommit.Result change2 = createChange();

    Change.Id id1 = change1.getPatchSetId().getParentKey();
    submitWithConflict(change2.getChangeId(),
        "Failed to submit 2 changes due to the following problems:\n"
        + "Change " + id1 + ": needs Code-Review");

    RevCommit head = getRemoteHead();
    assertThat(head.getId()).isEqualTo(oldHead.getId());
  }

  @Test
  public void submitFastForwardNotPossible_Conflict() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change =
        createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = getRemoteHead();
    testRepo.reset(initialHead);
    PushOneCommit.Result change2 =
        createChange("Change 2", "b.txt", "other content");

    approve(change2.getChangeId());
    Map<String, ActionInfo> actions = getActions(change2.getChangeId());

    assertThat(actions).containsKey("submit");
    ActionInfo info = actions.get("submit");
    assertThat(info.enabled).isNull();

    submitWithConflict(change2.getChangeId(),
        "Failed to submit 1 change due to the following problems:\n" +
        "Change " + change2.getChange().getId() + ": Project policy requires " +
        "all submissions to be a fast-forward. Please rebase the change " +
        "locally and upload again for review.");
    assertThat(getRemoteHead()).isEqualTo(oldHead);
    assertSubmitter(change.getChangeId(), 1);
  }

  @Test
  public void repairChangeStateAfterFailure() throws Exception {
    PushOneCommit.Result change = createChange("Change 1", "a.txt", "content");
    Change.Id id = change.getChange().getId();
    SubmitInput failAfterRefUpdates =
        new TestSubmitInput(new SubmitInput(), true);
    submit(change.getChangeId(), failAfterRefUpdates,
        ResourceConflictException.class, "Failing after ref updates", true);

    // Bad: ref advanced but change wasn't updated.
    PatchSet.Id psId = new PatchSet.Id(id, 1);
    ChangeInfo info = gApi.changes().id(id.get()).get();
    assertThat(info.status).isEqualTo(ChangeStatus.NEW);
    assertThat(info.revisions.get(info.currentRevision)._number).isEqualTo(1);
    ChangeMessageInfo lastMessage = Iterables.getLast(info.messages);

    ObjectId rev;
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      rev = repo.exactRef(psId.toRefName()).getObjectId();
      assertThat(rev).isNotNull();
      assertThat(repo.exactRef("refs/heads/master").getObjectId())
          .isEqualTo(rev);
    }

    submit(change.getChangeId());

    // Change status was updated, and branch tip stayed the same.
    info = gApi.changes().id(id.get()).get();
    assertThat(info.status).isEqualTo(ChangeStatus.MERGED);
    assertThat(info.revisions.get(info.currentRevision)._number).isEqualTo(1);
    assertThat(Iterables.getLast(info.messages).message)
        .isEqualTo(lastMessage.message);

    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.exactRef("refs/heads/master").getObjectId())
          .isEqualTo(rev);
    }
  }
}
