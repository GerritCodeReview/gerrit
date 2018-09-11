// Copyright (C) 2018 The Android Open Source Project
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
import static com.google.common.truth.TruthJUnit.assume;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.LabelFunction;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;

public class SubmitRuleIT extends AbstractDaemonTest {
  private final SubmitRuleOptions ALLOW_CLOSED =
      SubmitRuleOptions.defaults().toBuilder().allowClosed(true).build();

  @Inject private SubmitRuleEvaluator.Factory submitRuleEvaluatorFactory;

  @Test
  public void submitRecordsForClosedChanges_ParsedBackByDefault_NoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();

    SubmitRuleEvaluator submitRuleEvaluator =
        submitRuleEvaluatorFactory.create(SubmitRuleOptions.defaults());
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
  public void submitRecordsForClosedChanges_RecomputedIfRequested_NoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();

    SubmitRuleEvaluator submitRuleEvaluator = submitRuleEvaluatorFactory.create(ALLOW_CLOSED);
    PushOneCommit.Result r = createChange();
    approve(r.getChangeId());
    List<SubmitRecord> recordsBeforeSubmission = submitRuleEvaluator.evaluate(r.getChange());
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

  @Test
  public void submitRecordsForClosedChanges_ReturnClosedByDefault_ReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();

    SubmitRuleEvaluator submitRuleEvaluator =
        submitRuleEvaluatorFactory.create(SubmitRuleOptions.defaults());
    PushOneCommit.Result r = createChange();
    approve(r.getChangeId());
    gApi.changes().id(r.getChangeId()).current().submit();

    List<SubmitRecord> recordsAfterSubmission = submitRuleEvaluator.evaluate(r.getChange());
    SubmitRecord closed = new SubmitRecord();
    closed.status = SubmitRecord.Status.CLOSED;
    assertThat(recordsAfterSubmission).containsExactly(closed);
  }

  @Test
  public void submitRecordsForClosedChanges_RecomputedIfRequested_ReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();

    SubmitRuleEvaluator submitRuleEvaluator = submitRuleEvaluatorFactory.create(ALLOW_CLOSED);
    PushOneCommit.Result r = createChange();
    approve(r.getChangeId());
    List<SubmitRecord> recordsBeforeSubmission = submitRuleEvaluator.evaluate(r.getChange());
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
    haveCodeReview.status = SubmitRecord.Label.Status.OK;
    haveCodeReview.appliedBy = admin.id;
    assertThat(recordLabels).contains(haveCodeReview);
  }

  private void assertMyLabelNeed(List<SubmitRecord.Label> recordLabels) {
    SubmitRecord.Label needCustomLabel = new SubmitRecord.Label();
    needCustomLabel.label = "My-Label";
    needCustomLabel.status = SubmitRecord.Label.Status.NEED;
    assertThat(recordLabels).contains(needCustomLabel);
  }

  private void setupCustomBlockingLabel() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType myLabel =
          new LabelType(
              "My-Label",
              ImmutableList.of(
                  new LabelValue((short) 0, "Not approved"),
                  new LabelValue((short) 1, "Approved")));
      myLabel.setFunction(LabelFunction.MAX_WITH_BLOCK);
      u.getConfig().getLabelSections().put("My-Label", myLabel);
      u.save();
    }
  }
}
