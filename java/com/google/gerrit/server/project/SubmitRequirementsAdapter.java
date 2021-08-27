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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.MoreCollectors;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRecord.Label;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.entities.SubmitRequirementExpressionResult.Status;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.server.rules.DefaultSubmitRule;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Convert {@link com.google.gerrit.entities.SubmitRecord} entities to {@link
 * com.google.gerrit.entities.SubmitRequirementResult}s.
 */
public class SubmitRequirementsAdapter {
  public List<SubmitRequirementResult> createResult(
      SubmitRecord record, List<LabelType> labelTypes, ObjectId psCommitId) {
    if (record.ruleName.equals(DefaultSubmitRule.RULE_NAME)) {
      return createFromDefaultSubmitRecord(record.labels, labelTypes, psCommitId);
    }
    return createFromCustomSubmitRecord(record, psCommitId);
  }

  private List<SubmitRequirementResult> createFromDefaultSubmitRecord(
      List<Label> labels, List<LabelType> labelTypes, ObjectId psCommitId) {
    ImmutableList.Builder<SubmitRequirementResult> result = ImmutableList.builder();
    for (Label label : labels) {
      LabelType labelType = getLabelType(labelTypes, label.label);
      if (!isBlocking(labelType)) {
        continue;
      }
      ImmutableList<String> atoms = toExpressionAtomList(labelType);
      SubmitRequirement.Builder req =
          SubmitRequirement.builder()
              .setName(label.label)
              .setSubmittabilityExpression(toExpression(atoms))
              .setAllowOverrideInChildProjects(false);
      result.add(
          SubmitRequirementResult.builder()
              .submitRequirement(req.build())
              .submittabilityExpressionResult(
                  createExpressionResult(toExpression(atoms), mapStatus(label), atoms))
              .patchSetCommitId(psCommitId)
              .build());
    }
    return result.build();
  }

  private List<SubmitRequirementResult> createFromCustomSubmitRecord(
      SubmitRecord record, ObjectId psCommitId) {
    if (record.labels.isEmpty()) {
      SubmitRequirement sr =
          SubmitRequirement.builder()
              .setName(record.ruleName)
              .setSubmittabilityExpression(SubmitRequirementExpression.create(record.ruleName))
              .setAllowOverrideInChildProjects(false)
              .build();
      return ImmutableList.of(
          SubmitRequirementResult.builder()
              .submitRequirement(sr)
              .submittabilityExpressionResult(
                  createExpressionResult(
                      sr.submittabilityExpression(),
                      mapStatus(record),
                      ImmutableList.of(record.ruleName)))
              .patchSetCommitId(psCommitId)
              .build());
    }
    ImmutableList.Builder<SubmitRequirementResult> result = ImmutableList.builder();
    for (Label label : record.labels) {
      String expressionString = String.format("label:%s=%s", label.label, record.ruleName);
      SubmitRequirement sr =
          SubmitRequirement.builder()
              .setName(label.label)
              .setSubmittabilityExpression(SubmitRequirementExpression.create(expressionString))
              .setAllowOverrideInChildProjects(false)
              .build();
      result.add(
          SubmitRequirementResult.builder()
              .submitRequirement(sr)
              .submittabilityExpressionResult(
                  createExpressionResult(
                      sr.submittabilityExpression(),
                      mapStatus(record),
                      ImmutableList.of(expressionString)))
              .patchSetCommitId(psCommitId)
              .build());
    }
    return result.build();
  }

  private boolean isBlocking(LabelType labelType) {
    return labelType.getFunction().isBlock() || labelType.getFunction().isRequired();
  }

  private SubmitRequirementExpression toExpression(List<String> atoms) {
    return SubmitRequirementExpression.create(String.join(" ", atoms));
  }

  private ImmutableList<String> toExpressionAtomList(LabelType lt) {
    switch (lt.getFunction()) {
      case MAX_WITH_BLOCK:
        return ImmutableList.of(
            String.format("label:%s=MAX", lt.getName()),
            String.format("-label:%s=MIN", lt.getName()));
      case ANY_WITH_BLOCK:
        return ImmutableList.of(String.format(String.format("-label:%s=MIN", lt.getName())));
      case MAX_NO_BLOCK:
        return ImmutableList.of(String.format(String.format("label:%s=MAX", lt.getName())));
      default:
        return ImmutableList.of();
    }
  }

  private Status mapStatus(Label label) {
    SubmitRequirementExpressionResult.Status status = Status.PASS;
    switch (label.status) {
      case OK:
      case MAY:
        status = Status.PASS;
        break;
      case REJECT:
      case NEED:
      case IMPOSSIBLE:
        status = Status.FAIL;
        break;
    }
    return status;
  }

  private Status mapStatus(SubmitRecord submitRecord) {
    switch (submitRecord.status) {
      case OK:
      case CLOSED:
      case FORCED:
        return Status.PASS;
      case NOT_READY:
        return Status.FAIL;
      case RULE_ERROR:
      default:
        return Status.ERROR;
    }
  }

  private SubmitRequirementExpressionResult createExpressionResult(
      SubmitRequirementExpression expression, Status status, ImmutableList<String> atoms) {
    return SubmitRequirementExpressionResult.create(
        expression,
        status,
        status == Status.PASS ? atoms : ImmutableList.of(),
        status == Status.FAIL ? atoms : ImmutableList.of());
  }

  private LabelType getLabelType(List<LabelType> labelTypes, String labelName) {
    return labelTypes.stream()
        .filter(lt -> lt.getName().equals(labelName))
        .collect(MoreCollectors.onlyElement());
  }
}
