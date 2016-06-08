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
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.server.data.RefUpdateAttribute;
import com.google.gerrit.server.events.RefEvent;
import com.google.gerrit.server.events.RefUpdatedEvent;

import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

import java.util.List;

public class SubmitByMergeAlwaysIT extends AbstractSubmitByMerge {

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.MERGE_ALWAYS;
  }

  @Test
  public void submitWithMergeIfFastForwardPossible() throws Exception {
    RevCommit oldHead = getRemoteHead();
    PushOneCommit.Result change = createChange();
    submit(change.getChangeId());
    RevCommit head = getRemoteHead();
    assertThat(head.getParentCount()).isEqualTo(2);
    assertThat(head.getParent(0)).isEqualTo(oldHead);
    assertThat(head.getParent(1)).isEqualTo(change.getCommit());
    assertSubmitter(change.getChangeId(), 1);
    assertPersonEquals(admin.getIdent(), head.getAuthorIdent());
    assertPersonEquals(serverIdent.get(), head.getCommitterIdent());
  }

  @Test
  public void submitMultipleChanges() throws Exception {
    RevCommit initialHead = getRemoteHead();

    // Submit a change so that the remote head advances
    PushOneCommit.Result change2 = createChange("Change 2", "b", "b");
    submit(change2.getChangeId());

    // The remote head should now be a merge of the previous head
    // and "Change 2"
    RevCommit headAfterFirstSubmit = getRemoteLog().get(0);
    assertThat(headAfterFirstSubmit.getParent(1).getShortMessage()).isEqualTo(
        change2.getCommit().getShortMessage());
    assertThat(headAfterFirstSubmit.getParent(0).getShortMessage()).isEqualTo(
        initialHead.getShortMessage());
    assertThat(headAfterFirstSubmit.getParent(0).getId()).isEqualTo(
        initialHead.getId());

    // Submit two changes at the same time
    PushOneCommit.Result change3 = createChange("Change 3", "c", "c");
    PushOneCommit.Result change4 = createChange("Change 4", "d", "d");
    approve(change3.getChangeId());
    submit(change4.getChangeId());

    // Submitting change 4 should result in change 3 also being submitted
    assertMerged(change3.getChangeId());

    // The remote head should now be a merge of the new head after
    // the previous submit, and "Change 4".
    RevCommit headAfterSecondSubmit = getRemoteLog().get(0);
    assertThat(headAfterSecondSubmit.getParent(1).getShortMessage()).isEqualTo(
        change4.getCommit().getShortMessage());
    assertThat(headAfterSecondSubmit.getParent(0).getShortMessage()).isEqualTo(
        headAfterFirstSubmit.getShortMessage());
    assertThat(headAfterSecondSubmit.getParent(0).getId()).isEqualTo(
        headAfterFirstSubmit.getId());
    assertPersonEquals(admin.getIdent(), headAfterSecondSubmit.getAuthorIdent());
    assertPersonEquals(serverIdent.get(),
        headAfterSecondSubmit.getCommitterIdent());

    // The two submit operations should have resulted in two ref-update events
    List<RefEvent> refUpdates = eventRecorder.getRefUpdates(
        project.get(), "refs/heads/master", 2);

    RefUpdateAttribute refUpdate =
        ((RefUpdatedEvent)(refUpdates.get(0))).refUpdate.get();
    assertThat(refUpdate).isNotNull();

    assertThat(refUpdate.oldRev).isEqualTo(initialHead.name());
    assertThat(refUpdate.newRev).isEqualTo(headAfterFirstSubmit.name());

    refUpdate = ((RefUpdatedEvent)(refUpdates.get(1))).refUpdate.get();
    assertThat(refUpdate).isNotNull();

    assertThat(refUpdate.oldRev).isEqualTo(headAfterFirstSubmit.name());
    assertThat(refUpdate.newRev).isEqualTo(headAfterSecondSubmit.name());
  }
}
