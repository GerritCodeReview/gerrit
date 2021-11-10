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
    LabelType codeReview =
        LabelType.builder(
                "Code-Review",
                ImmutableList.of(
                    LabelValue.create((short) 1, "Looks good to me"),
                    LabelValue.create((short) 0, "No score"),
                    LabelValue.create((short) -1, "I would prefer this is not merged as is")))
            .setFunction(LabelFunction.MAX_WITH_BLOCK)
            .build();

    LabelType verified =
        LabelType.builder(
                "Verified",
                ImmutableList.of(
                    LabelValue.create((short) 1, "Looks good to me"),
                    LabelValue.create((short) 0, "No score"),
                    LabelValue.create((short) -1, "I would prefer this is not merged as is")))
            .setFunction(LabelFunction.MAX_NO_BLOCK)
            .build();

    LabelType codeStyle =
        LabelType.builder(
                "Code-Style",
                ImmutableList.of(
                    LabelValue.create((short) 1, "Looks good to me"),
                    LabelValue.create((short) 0, "No score"),
                    LabelValue.create((short) -1, "I would prefer this is not merged as is")))
            .setFunction(LabelFunction.ANY_WITH_BLOCK)
            .build();

    LabelType ignoreSelfApprovalLabel =
        LabelType.builder(
                "ISA-Label",
                ImmutableList.of(
                    LabelValue.create((short) 1, "Looks good to me"),
                    LabelValue.create((short) 0, "No score"),
                    LabelValue.create((short) -1, "I would prefer this is not merged as is")))
            .setFunction(LabelFunction.MAX_WITH_BLOCK)
            .setIgnoreSelfApproval(true)
            .build();

    labelTypes = Arrays.asList(codeReview, verified, codeStyle, ignoreSelfApprovalLabel);
  }

  @Test
  public void defaultSubmitRule_withLabelsAllPass() {
    SubmitRecord submitRecord =
        createSubmitRecord(
            "gerrit~DefaultSubmitRule",
            Status.OK,
            Arrays.asList(
                createLabel("Code-Review", Label.Status.OK),
                createLabel("Verified", Label.Status.OK)));

    List<SubmitRequirementResult> requirements =
        SubmitRequirementsAdapter.createResult(submitRecord, labelTypes, psCommitId);

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
            "gerrit~DefaultSubmitRule",
            Status.OK,
            Arrays.asList(
                createLabel("Code-Review", Label.Status.NEED),
                createLabel("Verified", Label.Status.NEED)));

    List<SubmitRequirementResult> requirements =
        SubmitRequirementsAdapter.createResult(submitRecord, labelTypes, psCommitId);

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
  public void defaultSubmitRule_withLabelStatusNeed_labelHasIgnoreSelfApproval() throws Exception {
    SubmitRecord submitRecord =
        createSubmitRecord(
            "gerrit~DefaultSubmitRule",
            Status.NOT_READY,
            Arrays.asList(createLabel("ISA-Label", Label.Status.NEED)));

    List<SubmitRequirementResult> requirements =
        SubmitRequirementsAdapter.createResult(submitRecord, labelTypes, psCommitId);

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
            "gerrit~DefaultSubmitRule",
            Status.OK,
            Arrays.asList(createLabel("ISA-Label", Label.Status.OK)));

    List<SubmitRequirementResult> requirements =
        SubmitRequirementsAdapter.createResult(submitRecord, labelTypes, psCommitId);

    assertThat(requirements).hasSize(1);
    assertResult(
        requirements.get(0),
        /* reqName= */ "ISA-Label",
        /* submitExpression= */ "label:ISA-Label=MAX,user=non_uploader -label:ISA-Label=MIN",
        SubmitRequirementResult.Status.SATISFIED,
        SubmitRequirementExpressionResult.Status.PASS);
  }

  @Test
  public void customSubmitRule_noLabels_withStatusOk() {
    SubmitRecord submitRecord =
        createSubmitRecord("gerrit~IgnoreSelfApprovalRule", Status.OK, Arrays.asList());

    List<SubmitRequirementResult> requirements =
        SubmitRequirementsAdapter.createResult(submitRecord, labelTypes, psCommitId);

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

    List<SubmitRequirementResult> requirements =
        SubmitRequirementsAdapter.createResult(submitRecord, labelTypes, psCommitId);

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

    List<SubmitRequirementResult> requirements =
        SubmitRequirementsAdapter.createResult(submitRecord, labelTypes, psCommitId);

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

    List<SubmitRequirementResult> requirements =
        SubmitRequirementsAdapter.createResult(submitRecord, labelTypes, psCommitId);

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
  public void customSubmitRule_withMixOfPassingAndFailingLabels() {
    SubmitRecord submitRecord =
        createSubmitRecord(
            "gerrit~PrologRule",
            Status.NOT_READY,
            Arrays.asList(
                createLabel("custom-label-1", Label.Status.OK),
                createLabel("custom-label-2", Label.Status.REJECT)));

    List<SubmitRequirementResult> requirements =
        SubmitRequirementsAdapter.createResult(submitRecord, labelTypes, psCommitId);

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
    assertThat(r.submitRequirement().submittabilityExpression().get().expressionString())
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
