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

package com.google.gerrit.server.project;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRecord.Label;
import com.google.gerrit.entities.SubmitRecord.Status;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.server.rules.DefaultSubmitRule;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

public class SubmitRequirementsAdapterTest {
  private List<LabelType> labelTypes;

  private static final ObjectId psCommitId = ObjectId.zeroId();

  @Before
  public void setup() {
    @SuppressWarnings("deprecation")
    LabelType codeReview =
        LabelType.builder(
                "Code-Review",
                ImmutableList.of(
                    LabelValue.create((short) 1, "Looks good to me"),
                    LabelValue.create((short) 0, "No score"),
                    LabelValue.create((short) -1, "I would prefer this is not submitted as is")))
            .setFunction(LabelFunction.MAX_WITH_BLOCK)
            .build();

    @SuppressWarnings("deprecation")
    LabelType verified =
        LabelType.builder(
                "Verified",
                ImmutableList.of(
                    LabelValue.create((short) 1, "Looks good to me"),
                    LabelValue.create((short) 0, "No score"),
                    LabelValue.create((short) -1, "I would prefer this is not submitted as is")))
            .setFunction(LabelFunction.MAX_NO_BLOCK)
            .build();

    @SuppressWarnings("deprecation")
    LabelType codeStyle =
        LabelType.builder(
                "Code-Style",
                ImmutableList.of(
                    LabelValue.create((short) 1, "Looks good to me"),
                    LabelValue.create((short) 0, "No score"),
                    LabelValue.create((short) -1, "I would prefer this is not submitted as is")))
            .setFunction(LabelFunction.ANY_WITH_BLOCK)
            .build();

    @SuppressWarnings("deprecation")
    LabelType ignoreSelfApprovalLabel =
        LabelType.builder(
                "ISA-Label",
                ImmutableList.of(
                    LabelValue.create((short) 1, "Looks good to me"),
                    LabelValue.create((short) 0, "No score"),
                    LabelValue.create((short) -1, "I would prefer this is not submitted as is")))
            .setFunction(LabelFunction.MAX_WITH_BLOCK)
            .setIgnoreSelfApproval(true)
            .build();

    labelTypes = Arrays.asList(codeReview, verified, codeStyle, ignoreSelfApprovalLabel);
  }

  @Test
  public void defaultSubmitRule_withLabelsAllPass() {
    SubmitRecord submitRecord =
        createSubmitRecord(
            DefaultSubmitRule.RULE_NAME,
            Status.OK,
            Arrays.asList(
                createLabel("Code-Review", Label.Status.OK),
                createLabel("Verified", Label.Status.OK)));

    ImmutableList<SubmitRequirementResult> requirements =
        SubmitRequirementsAdapter.createResult(
            submitRecord, labelTypes, psCommitId, /* isForced= */ false);

    assertThat(requirements).hasSize(2);
    assertResult(
        requirements.get(0),
        /* reqName= */ "Code-Review",
        /* submitExpression= */ "label:Code-Review=MAX -label:Code-Review=MIN",
        SubmitRequirementResult.Status.SATISFIED,
        SubmitRequirementExpressionResult.Status.PASS);
    assertResult(
        requirements.get(1),
        /* reqName= */ "Verified",
        /* submitExpression= */ "label:Verified=MAX",
        SubmitRequirementResult.Status.SATISFIED,
        SubmitRequirementExpressionResult.Status.PASS);
  }

  @Test
  public void defaultSubmitRule_withLabelsAllNeed() {
    SubmitRecord submitRecord =
        createSubmitRecord(
            DefaultSubmitRule.RULE_NAME,
            Status.OK,
            Arrays.asList(
                createLabel("Code-Review", Label.Status.NEED),
                createLabel("Verified", Label.Status.NEED)));

    ImmutableList<SubmitRequirementResult> requirements =
        SubmitRequirementsAdapter.createResult(
            submitRecord, labelTypes, psCommitId, /* isForced= */ false);

    assertThat(requirements).hasSize(2);
    assertResult(
        requirements.get(0),
        /* reqName= */ "Code-Review",
        /* submitExpression= */ "label:Code-Review=MAX -label:Code-Review=MIN",
        SubmitRequirementResult.Status.UNSATISFIED,
        SubmitRequirementExpressionResult.Status.FAIL);
    assertResult(
        requirements.get(1),
        /* reqName= */ "Verified",
        /* submitExpression= */ "label:Verified=MAX",
        SubmitRequirementResult.Status.UNSATISFIED,
        SubmitRequirementExpressionResult.Status.FAIL);
  }

  @Test
  public void defaultSubmitRule_withOneLabelForced() {
    SubmitRecord submitRecord =
        createSubmitRecord(
            DefaultSubmitRule.RULE_NAME,
            Status.OK,
            Arrays.asList(createLabel("Code-Review", Label.Status.NEED)));

    // Submit records that are forced are written with their initial status in NoteDb, e.g. NEED.
    // If we do a force submit, the gerrit server appends an extra marker record with status=FORCED
    // to indicate that all other records were forced, that's why we explicitly pass isForced=true
    // to the "submit requirements adapter". The resulting submit requirement result has a
    // status=FORCED.
    ImmutableList<SubmitRequirementResult> requirements =
        SubmitRequirementsAdapter.createResult(
            submitRecord, labelTypes, psCommitId, /* isForced= */ true);

    assertThat(requirements).hasSize(1);
    assertResult(
        requirements.get(0),
        /* reqName= */ "Code-Review",
        /* submitExpression= */ "label:Code-Review=MAX -label:Code-Review=MIN",
        SubmitRequirementResult.Status.FORCED,
        SubmitRequirementExpressionResult.Status.FAIL);
  }

  @Test
  public void defaultSubmitRule_withLabelStatusNeed_labelHasIgnoreSelfApproval() throws Exception {
    SubmitRecord submitRecord =
        createSubmitRecord(
            DefaultSubmitRule.RULE_NAME,
            Status.NOT_READY,
            Arrays.asList(createLabel("ISA-Label", Label.Status.NEED)));

    ImmutableList<SubmitRequirementResult> requirements =
        SubmitRequirementsAdapter.createResult(
            submitRecord, labelTypes, psCommitId, /* isForced= */ false);

    assertThat(requirements).hasSize(1);
    assertResult(
        requirements.get(0),
        /* reqName= */ "ISA-Label",
        /* submitExpression= */ "label:ISA-Label=MAX,user=non_uploader -label:ISA-Label=MIN",
        SubmitRequirementResult.Status.UNSATISFIED,
        SubmitRequirementExpressionResult.Status.FAIL);
  }

  @Test
  public void defaultSubmitRule_withLabelStatusOk_labelHasIgnoreSelfApproval() throws Exception {
    SubmitRecord submitRecord =
        createSubmitRecord(
            DefaultSubmitRule.RULE_NAME,
            Status.OK,
            Arrays.asList(createLabel("ISA-Label", Label.Status.OK)));

    ImmutableList<SubmitRequirementResult> requirements =
        SubmitRequirementsAdapter.createResult(
            submitRecord, labelTypes, psCommitId, /* isForced= */ false);

    assertThat(requirements).hasSize(1);
    assertResult(
        requirements.get(0),
        /* reqName= */ "ISA-Label",
        /* submitExpression= */ "label:ISA-Label=MAX,user=non_uploader -label:ISA-Label=MIN",
        SubmitRequirementResult.Status.SATISFIED,
        SubmitRequirementExpressionResult.Status.PASS);
  }

  @Test
  public void defaultSubmitRule_withNonExistingLabel() throws Exception {
    SubmitRecord submitRecord =
        createSubmitRecord(
            DefaultSubmitRule.RULE_NAME,
            Status.OK,
            Arrays.asList(createLabel("Non-Existing", Label.Status.OK)));

    ImmutableList<SubmitRequirementResult> requirements =
        SubmitRequirementsAdapter.createResult(
            submitRecord, labelTypes, psCommitId, /* isForced= */ false);

    assertThat(requirements).isEmpty();
  }

  @Test
  public void defaultSubmitRule_withExistingAndNonExistingLabels() throws Exception {
    SubmitRecord submitRecord =
        createSubmitRecord(
            DefaultSubmitRule.RULE_NAME,
            Status.OK,
            Arrays.asList(
                createLabel("Non-Existing", Label.Status.OK),
                createLabel("Code-Review", Label.Status.OK)));

    ImmutableList<SubmitRequirementResult> requirements =
        SubmitRequirementsAdapter.createResult(
            submitRecord, labelTypes, psCommitId, /* isForced= */ false);

    // The "Non-Existing" label was skipped since it does not exist in the project config.
    assertThat(requirements).hasSize(1);
    assertResult(
        requirements.get(0),
        /* reqName= */ "Code-Review",
        /* submitExpression= */ "label:Code-Review=MAX -label:Code-Review=MIN",
        SubmitRequirementResult.Status.SATISFIED,
        SubmitRequirementExpressionResult.Status.PASS);
  }

  @Test
  public void customSubmitRule_noLabels_withStatusOk() {
    SubmitRecord submitRecord =
        createSubmitRecord("gerrit~IgnoreSelfApprovalRule", Status.OK, Arrays.asList());

    ImmutableList<SubmitRequirementResult> requirements =
        SubmitRequirementsAdapter.createResult(
            submitRecord, labelTypes, psCommitId, /* isForced= */ false);

    assertThat(requirements).hasSize(1);
    assertResult(
        requirements.get(0),
        /* reqName= */ "gerrit~IgnoreSelfApprovalRule",
        /* submitExpression= */ "rule:gerrit~IgnoreSelfApprovalRule",
        SubmitRequirementResult.Status.SATISFIED,
        SubmitRequirementExpressionResult.Status.PASS);
  }

  @Test
  public void customSubmitRule_nullLabels_withStatusOk() {
    SubmitRecord submitRecord =
        createSubmitRecord("gerrit~IgnoreSelfApprovalRule", Status.OK, /* labels= */ null);

    ImmutableList<SubmitRequirementResult> requirements =
        SubmitRequirementsAdapter.createResult(
            submitRecord, labelTypes, psCommitId, /* isForced= */ false);

    assertThat(requirements).hasSize(1);
    assertResult(
        requirements.get(0),
        /* reqName= */ "gerrit~IgnoreSelfApprovalRule",
        /* submitExpression= */ "rule:gerrit~IgnoreSelfApprovalRule",
        SubmitRequirementResult.Status.SATISFIED,
        SubmitRequirementExpressionResult.Status.PASS);
  }

  @Test
  public void customSubmitRule_noLabels_withStatusNotReady() {
    SubmitRecord submitRecord =
        createSubmitRecord("gerrit~IgnoreSelfApprovalRule", Status.NOT_READY, Arrays.asList());

    ImmutableList<SubmitRequirementResult> requirements =
        SubmitRequirementsAdapter.createResult(
            submitRecord, labelTypes, psCommitId, /* isForced= */ false);

    assertThat(requirements).hasSize(1);
    assertResult(
        requirements.get(0),
        /* reqName= */ "gerrit~IgnoreSelfApprovalRule",
        /* submitExpression= */ "rule:gerrit~IgnoreSelfApprovalRule",
        SubmitRequirementResult.Status.UNSATISFIED,
        SubmitRequirementExpressionResult.Status.FAIL);
  }

  @Test
  public void customSubmitRule_withLabels() {
    SubmitRecord submitRecord =
        createSubmitRecord(
            "gerrit~PrologRule",
            Status.NOT_READY,
            Arrays.asList(
                createLabel("custom-label-1", Label.Status.NEED),
                createLabel("custom-label-2", Label.Status.REJECT)));

    ImmutableList<SubmitRequirementResult> requirements =
        SubmitRequirementsAdapter.createResult(
            submitRecord, labelTypes, psCommitId, /* isForced= */ false);

    assertThat(requirements).hasSize(2);
    assertResult(
        requirements.get(0),
        /* reqName= */ "custom-label-1",
        /* submitExpression= */ "label:custom-label-1=gerrit~PrologRule",
        SubmitRequirementResult.Status.UNSATISFIED,
        SubmitRequirementExpressionResult.Status.FAIL);
    assertResult(
        requirements.get(1),
        /* reqName= */ "custom-label-2",
        /* submitExpression= */ "label:custom-label-2=gerrit~PrologRule",
        SubmitRequirementResult.Status.UNSATISFIED,
        SubmitRequirementExpressionResult.Status.FAIL);
  }

  @Test
  public void customSubmitRule_withLabels_withStatusOk() {
    SubmitRecord submitRecord =
        createSubmitRecord(
            "gerrit~PrologRule",
            Status.OK,
            Arrays.asList(
                createLabel("custom-need-label-1", Label.Status.NEED),
                createLabel("custom-pass-label-2", Label.Status.OK),
                createLabel("custom-may-label-3", Label.Status.MAY)));

    ImmutableList<SubmitRequirementResult> requirements =
        SubmitRequirementsAdapter.createResult(submitRecord, labelTypes, psCommitId, false);

    assertThat(requirements).hasSize(1);
    assertResult(
        requirements.get(0),
        /* reqName= */ "custom-pass-label-2",
        /* submitExpression= */ "label:custom-pass-label-2=gerrit~PrologRule",
        SubmitRequirementResult.Status.SATISFIED,
        SubmitRequirementExpressionResult.Status.PASS);
  }

  @Test
  public void customSubmitRule_withMixOfPassingAndFailingLabels() {
    SubmitRecord submitRecord =
        createSubmitRecord(
            "gerrit~PrologRule",
            Status.NOT_READY,
            Arrays.asList(
                createLabel("custom-label-1", Label.Status.OK),
                createLabel("custom-label-2", Label.Status.REJECT)));

    ImmutableList<SubmitRequirementResult> requirements =
        SubmitRequirementsAdapter.createResult(
            submitRecord, labelTypes, psCommitId, /* isForced= */ false);

    assertThat(requirements).hasSize(2);
    assertResult(
        requirements.get(0),
        /* reqName= */ "custom-label-1",
        /* submitExpression= */ "label:custom-label-1=gerrit~PrologRule",
        SubmitRequirementResult.Status.SATISFIED,
        SubmitRequirementExpressionResult.Status.PASS);
    assertResult(
        requirements.get(1),
        /* reqName= */ "custom-label-2",
        /* submitExpression= */ "label:custom-label-2=gerrit~PrologRule",
        SubmitRequirementResult.Status.UNSATISFIED,
        SubmitRequirementExpressionResult.Status.FAIL);
  }

  private void assertResult(
      SubmitRequirementResult r,
      String reqName,
      String submitExpression,
      SubmitRequirementResult.Status status,
      SubmitRequirementExpressionResult.Status expressionStatus) {
    assertThat(r.submitRequirement().name()).isEqualTo(reqName);
    assertThat(r.submitRequirement().submittabilityExpression().expressionString())
        .isEqualTo(submitExpression);
    assertThat(r.status()).isEqualTo(status);
    assertThat(r.submittabilityExpressionResult().get().status()).isEqualTo(expressionStatus);
  }

  private SubmitRecord createSubmitRecord(
      String ruleName, SubmitRecord.Status status, @Nullable List<Label> labels) {
    SubmitRecord record = new SubmitRecord();
    record.ruleName = ruleName;
    record.status = status;
    record.labels = labels;
    return record;
  }

  private Label createLabel(String name, Label.Status status) {
    Label label = new Label();
    label.label = name;
    label.status = status;
    return label;
  }
}
