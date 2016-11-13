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
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class SubmitByMergeAlwaysIT extends AbstractSubmitByMerge {

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.MERGE_ALWAYS;
  }

  @Test
  public void submitWithMergeIfFastForwardPossible() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change = createChange();
    submit(change.getChangeId());
    RevCommit headAfterSubmit = getRemoteHead();
    assertThat(headAfterSubmit.getParentCount()).isEqualTo(2);
    assertThat(headAfterSubmit.getParent(0)).isEqualTo(initialHead);
    assertThat(headAfterSubmit.getParent(1)).isEqualTo(change.getCommit());
    assertSubmitter(change.getChangeId(), 1);
    assertPersonEquals(admin.getIdent(), headAfterSubmit.getAuthorIdent());
    assertPersonEquals(serverIdent.get(), headAfterSubmit.getCommitterIdent());

    assertRefUpdatedEvents(initialHead, headAfterSubmit);
    assertChangeMergedEvents(change.getChangeId(), headAfterSubmit.name());
  }

  @Test
  public void submitMultipleChanges() throws Exception {
    RevCommit initialHead = getRemoteHead();

    // Submit a change so that the remote head advances
    PushOneCommit.Result change = createChange("Change 1", "b", "b");
    submit(change.getChangeId());

    // The remote head should now be a merge of the previous head
    // and "Change 1"
    RevCommit headAfterFirstSubmit = getRemoteLog().get(0);
    assertThat(headAfterFirstSubmit.getParent(1).getShortMessage())
        .isEqualTo(change.getCommit().getShortMessage());
    assertThat(headAfterFirstSubmit.getParent(0).getShortMessage())
        .isEqualTo(initialHead.getShortMessage());
    assertThat(headAfterFirstSubmit.getParent(0).getId()).isEqualTo(initialHead.getId());

    // Submit three changes at the same time
    PushOneCommit.Result change2 = createChange("Change 2", "c", "c");
    PushOneCommit.Result change3 = createChange("Change 3", "d", "d");
    PushOneCommit.Result change4 = createChange("Change 4", "e", "e");
    approve(change2.getChangeId());
    approve(change3.getChangeId());
    submit(change4.getChangeId());

    // Submitting change 4 should result in changes 2 and 3 also being submitted
    assertMerged(change2.getChangeId());
    assertMerged(change3.getChangeId());

    // The remote head should now be a merge of the new head after
    // the previous submit, and "Change 4".
    RevCommit headAfterSecondSubmit = getRemoteLog().get(0);
    assertThat(headAfterSecondSubmit.getParent(1).getShortMessage())
        .isEqualTo(change4.getCommit().getShortMessage());
    assertThat(headAfterSecondSubmit.getParent(0).getShortMessage())
        .isEqualTo(headAfterFirstSubmit.getShortMessage());
    assertThat(headAfterSecondSubmit.getParent(0).getId()).isEqualTo(headAfterFirstSubmit.getId());
    assertPersonEquals(admin.getIdent(), headAfterSecondSubmit.getAuthorIdent());
    assertPersonEquals(serverIdent.get(), headAfterSecondSubmit.getCommitterIdent());

    assertRefUpdatedEvents(
        initialHead, headAfterFirstSubmit, headAfterFirstSubmit, headAfterSecondSubmit);
    assertChangeMergedEvents(
        change.getChangeId(),
        headAfterFirstSubmit.name(),
        change2.getChangeId(),
        headAfterSecondSubmit.name(),
        change3.getChangeId(),
        headAfterSecondSubmit.name(),
        change4.getChangeId(),
        headAfterSecondSubmit.name());
  }
}
