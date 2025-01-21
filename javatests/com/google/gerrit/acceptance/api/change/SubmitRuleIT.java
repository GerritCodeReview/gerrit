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
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.rules.DefaultSubmitRule;
import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class SubmitRuleIT extends AbstractDaemonTest {
  @Inject private SubmitRuleEvaluator.Factory submitRuleEvaluatorFactory;

  @Test
  public void submitRecordsForClosedChanges_parsedBackByDefault() throws Exception {
    SubmitRuleEvaluator submitRuleEvaluator =
        submitRuleEvaluatorFactory.create(SubmitRuleOptions.defaults());
    PushOneCommit.Result r = createChange();
    approve(r.getChangeId());
    List<SubmitRecord> recordsBeforeSubmission = submitRuleEvaluator.evaluate(r.getChange());
    assertThat(
            recordsBeforeSubmission.stream()
                .map(record -> record.ruleName)
                .collect(Collectors.toList()))
        .containsExactly(DefaultSubmitRule.RULE_NAME);
    gApi.changes().id(r.getChangeId()).current().submit();
    // Add a new label that blocks submission if not granted. In case we reevaluate the rules,
    // this would show up as blocking submission.
    setupCustomBlockingLabel();
    List<SubmitRecord> recordsAfterSubmission = submitRuleEvaluator.evaluate(r.getChange());
    recordsBeforeSubmission.forEach(
        sr -> sr.status = SubmitRecord.Status.CLOSED); // Set status to closed
    assertThat(recordsBeforeSubmission).isEqualTo(recordsAfterSubmission);
  }

  @Test
  public void submitRecordsForClosedChanges_recomputedIfRequested() throws Exception {
    SubmitRuleEvaluator submitRuleEvaluator =
        submitRuleEvaluatorFactory.create(
            SubmitRuleOptions.builder().recomputeOnClosedChanges(true).build());
    PushOneCommit.Result r = createChange();
    approve(r.getChangeId());
    List<SubmitRecord> recordsBeforeSubmission = submitRuleEvaluator.evaluate(r.getChange());
    assertThat(
            recordsBeforeSubmission.stream()
                .map(record -> record.ruleName)
                .collect(Collectors.toList()))
        .containsExactly(DefaultSubmitRule.RULE_NAME);
    gApi.changes().id(r.getChangeId()).current().submit();
    // Add a new label that blocks submission if not granted. In case we reevaluate the rules,
    // this would show up as blocking submission.
    setupCustomBlockingLabel();
    List<SubmitRecord> recordsAfterSubmission = submitRuleEvaluator.evaluate(r.getChange());
    assertThat(recordsBeforeSubmission).isNotEqualTo(recordsAfterSubmission);
    assertThat(recordsAfterSubmission).hasSize(1);
    List<SubmitRecord.Label> recordLabels = recordsAfterSubmission.get(0).labels;

    assertThat(recordLabels).hasSize(2);
    assertCodeReviewApproved(recordLabels);
    assertMyLabelNeed(recordLabels);
  }

  private void assertCodeReviewApproved(List<SubmitRecord.Label> recordLabels) {
    SubmitRecord.Label haveCodeReview = new SubmitRecord.Label();
    haveCodeReview.label = "Code-Review";
    haveCodeReview.status = SubmitRecord.Label.Status.MAY;
    haveCodeReview.appliedBy = admin.id();
    assertThat(recordLabels).contains(haveCodeReview);
  }

  private void assertMyLabelNeed(List<SubmitRecord.Label> recordLabels) {
    SubmitRecord.Label needCustomLabel = new SubmitRecord.Label();
    needCustomLabel.label = "My-Label";
    needCustomLabel.status = SubmitRecord.Label.Status.NEED;
    assertThat(recordLabels).contains(needCustomLabel);
  }

  @SuppressWarnings("deprecation")
  private void setupCustomBlockingLabel() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .upsertLabelType(
              LabelType.builder(
                      "My-Label",
                      ImmutableList.of(
                          LabelValue.create((short) 0, "Not approved"),
                          LabelValue.create((short) 1, "Approved")))
                  .setFunction(LabelFunction.MAX_WITH_BLOCK)
                  .build());
      u.save();
    }
  }
}
