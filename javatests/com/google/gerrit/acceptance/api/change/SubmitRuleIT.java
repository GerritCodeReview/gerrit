// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class SubmitRuleIT extends AbstractDaemonTest {
  @Inject private SubmitRuleEvaluator submitRuleEvaluator;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void submitRecordsForClosedChanges_parsedFromNoteDb() throws Exception {
    PushOneCommit.Result r = createChange();
    approve(r.getChangeId());
    List<SubmitRecord> recordsBeforeSubmission = submitRuleEvaluator.evaluate(r.getChange());
    gApi.changes().id(r.getChangeId()).current().submit();
    // Add a new label that blocks submission if not granted. In case we reevaluate the rules,
    // this would show up as blocking submission.
    setupCustomBlockingLabel();
    List<SubmitRecord> recordsAfterSubmission = submitRuleEvaluator.evaluate(r.getChange());
    assertThat(recordsBeforeSubmission).isEqualTo(recordsAfterSubmission);
  }

  @Test
  public void submitRecordsForClosedChanges_approvalsAddedAfterSubmissionAreServedOnApi()
      throws Exception {
    PushOneCommit.Result r = createChange();
    approve(r.getChangeId());
    List<SubmitRecord> recordsBeforeSubmission = submitRuleEvaluator.evaluate(r.getChange());
    gApi.changes().id(r.getChangeId()).current().submit();
    // After submission, user votes Code-Review+1 which does not change the submit requirements.
    requestScopeOperations.setApiUser(user.id());
    recommend(r.getChangeId());
    List<SubmitRecord> recordsAfterSubmission = submitRuleEvaluator.evaluate(r.getChange());
    assertThat(recordsBeforeSubmission).isEqualTo(recordsAfterSubmission);
    // However, the vote is reflected in the labels field in ChangeInfo.
    List<ApprovalInfo> approvals =
        Iterables.getOnlyElement(
                gApi.changes()
                    .id(r.getChangeId())
                    .get(ListChangesOption.DETAILED_LABELS)
                    .labels
                    .values())
            .all;
    assertThat(approvals).hasSize(2);
    assertThat(approvals.stream().collect(Collectors.toMap(ai -> ai._accountId, ai -> ai.value)))
        .containsExactlyEntriesIn(ImmutableMap.of(user.id().get(), 1, admin.id().get(), 2));
  }

  private void setupCustomBlockingLabel() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType.Builder myLabel =
          LabelType.builder(
              "My-Label",
              ImmutableList.of(
                  LabelValue.create((short) 0, "Not approved"),
                  LabelValue.create((short) 1, "Approved")));
      myLabel.setFunction(LabelFunction.MAX_WITH_BLOCK);
      u.getConfig().upsertLabelType(myLabel.build());
      u.save();
    }
  }
}
