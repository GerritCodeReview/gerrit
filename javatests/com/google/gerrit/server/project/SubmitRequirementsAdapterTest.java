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
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRecord.Label;
import com.google.gerrit.entities.SubmitRecord.Status;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.server.rules.DefaultSubmitRule;
import com.google.gerrit.server.rules.IgnoreSelfApprovalRule;
import com.google.gerrit.server.rules.PrologRule;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

public class SubmitRequirementsAdapterTest {
  private SubmitRequirementsAdapter adapter = new SubmitRequirementsAdapter();
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

    labelTypes = Arrays.asList(codeReview, verified, codeStyle);
  }

  @Test
  public void defaultSubmitRule_WithLabelsAllPass() {
    SubmitRecord submitRecord =
        createSubmitRecord(
            DefaultSubmitRule.RULE_NAME,
            Status.OK,
            Arrays.asList(
                createLabel("Code-Review", Label.Status.OK),
                createLabel("Verified", Label.Status.OK)));

    List<SubmitRequirementResult> requirements =
        adapter.createResult(submitRecord, labelTypes, psCommitId);

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
  public void defaultSubmitRule_WithLabelsAllNeed() {
    SubmitRecord submitRecord =
        createSubmitRecord(
            DefaultSubmitRule.RULE_NAME,
            Status.OK,
            Arrays.asList(
                createLabel("Code-Review", Label.Status.NEED),
                createLabel("Verified", Label.Status.NEED)));

    List<SubmitRequirementResult> requirements =
        adapter.createResult(submitRecord, labelTypes, psCommitId);

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
  public void customSubmitRule_NoLabels_WithStatusOk() {
    SubmitRecord submitRecord =
        createSubmitRecord(IgnoreSelfApprovalRule.RULE_NAME, Status.OK, Arrays.asList());

    List<SubmitRequirementResult> requirements =
        adapter.createResult(submitRecord, labelTypes, psCommitId);

    assertThat(requirements).hasSize(1);
    assertResult(
        requirements.get(0),
        /* reqName= */ "Ignore Self Approval",
        /* submitExpression= */ "Ignore Self Approval",
        SubmitRequirementResult.Status.SATISFIED,
        SubmitRequirementExpressionResult.Status.PASS);
  }

  @Test
  public void customSubmitRule_NoLabels_WithStatusNotReady() {
    SubmitRecord submitRecord =
        createSubmitRecord(IgnoreSelfApprovalRule.RULE_NAME, Status.NOT_READY, Arrays.asList());

    List<SubmitRequirementResult> requirements =
        adapter.createResult(submitRecord, labelTypes, psCommitId);

    assertThat(requirements).hasSize(1);
    assertResult(
        requirements.get(0),
        /* reqName= */ "Ignore Self Approval",
        /* submitExpression= */ "Ignore Self Approval",
        SubmitRequirementResult.Status.UNSATISFIED,
        SubmitRequirementExpressionResult.Status.FAIL);
  }

  @Test
  public void customSubmitRule_WithLabels() {
    SubmitRecord submitRecord =
        createSubmitRecord(
            PrologRule.RULE_NAME,
            Status.NOT_READY,
            Arrays.asList(
                createLabel("custom-label-1", Label.Status.NEED),
                createLabel("custom-label-2", Label.Status.REJECT)));

    List<SubmitRequirementResult> requirements =
        adapter.createResult(submitRecord, labelTypes, psCommitId);

    assertThat(requirements).hasSize(2);
    assertResult(
        requirements.get(0),
        /* reqName= */ "custom-label-1",
        /* submitExpression= */ "label:custom-label-1=Prolog",
        SubmitRequirementResult.Status.UNSATISFIED,
        SubmitRequirementExpressionResult.Status.FAIL);
    assertResult(
        requirements.get(1),
        /* reqName= */ "custom-label-2",
        /* submitExpression= */ "label:custom-label-2=Prolog",
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
    assertThat(r.submittabilityExpressionResult().status()).isEqualTo(expressionStatus);
  }

  private SubmitRecord createSubmitRecord(
      String ruleName, SubmitRecord.Status status, List<Label> labels) {
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
