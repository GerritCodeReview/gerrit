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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRecord.Label;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.entities.SubmitRequirementExpressionResult.Status;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Convert {@link com.google.gerrit.entities.SubmitRecord} entities to {@link
 * com.google.gerrit.entities.SubmitRequirementResult}s.
 */
public class SubmitRequirementsAdapter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private SubmitRequirementsAdapter() {}

  /**
   * Retrieve legacy submit records (created by label functions and other {@link
   * com.google.gerrit.server.rules.SubmitRule}s) and convert them to submit requirement results.
   */
  public static Map<SubmitRequirement, SubmitRequirementResult> getLegacyRequirements(
      SubmitRuleEvaluator.Factory evaluator, ChangeData cd) {
    // We use SubmitRuleOptions.defaults() which does not recompute submit rules for closed changes.
    // This doesn't have an effect since we never call this class (i.e. to evaluate submit
    // requirements) for closed changes.
    List<SubmitRecord> records = evaluator.create(SubmitRuleOptions.defaults()).evaluate(cd);
    List<LabelType> labelTypes = cd.getLabelTypes().getLabelTypes();
    ObjectId commitId = cd.currentPatchSet().commitId();
    return records.stream()
        .map(r -> createResult(r, labelTypes, commitId))
        .flatMap(List::stream)
        .collect(Collectors.toMap(sr -> sr.submitRequirement(), Function.identity()));
  }

  static List<SubmitRequirementResult> createResult(
      SubmitRecord record, List<LabelType> labelTypes, ObjectId psCommitId) {
    List<SubmitRequirementResult> results;
    if (record.ruleName != null && record.ruleName.equals("gerrit~DefaultSubmitRule")) {
      results = createFromDefaultSubmitRecord(record.labels, labelTypes, psCommitId);
    } else {
      results = createFromCustomSubmitRecord(record, psCommitId);
    }
    logger.atFine().log("Converted submit record %s to submit requirements %s", record, results);
    return results;
  }

  private static List<SubmitRequirementResult> createFromDefaultSubmitRecord(
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
              .setAllowOverrideInChildProjects(labelType.isCanOverride());
      result.add(
          SubmitRequirementResult.builder()
              .legacy(Optional.of(true))
              .submitRequirement(req.build())
              .submittabilityExpressionResult(
                  createExpressionResult(toExpression(atoms), mapStatus(label), atoms))
              .patchSetCommitId(psCommitId)
              .build());
    }
    return result.build();
  }

  private static List<SubmitRequirementResult> createFromCustomSubmitRecord(
      SubmitRecord record, ObjectId psCommitId) {
    String ruleName = record.ruleName != null ? record.ruleName : "Custom-Rule";
    if (record.labels == null || record.labels.isEmpty()) {
      SubmitRequirement sr =
          SubmitRequirement.builder()
              .setName(ruleName)
              .setSubmittabilityExpression(
                  SubmitRequirementExpression.create(String.format("rule:%s", ruleName)))
              .setAllowOverrideInChildProjects(false)
              .build();
      return ImmutableList.of(
          SubmitRequirementResult.builder()
              .legacy(Optional.of(true))
              .submitRequirement(sr)
              .submittabilityExpressionResult(
                  createExpressionResult(
                      sr.submittabilityExpression(), mapStatus(record), ImmutableList.of(ruleName)))
              .patchSetCommitId(psCommitId)
              .build());
    }
    ImmutableList.Builder<SubmitRequirementResult> result = ImmutableList.builder();
    for (Label label : record.labels) {
      String expressionString = String.format("label:%s=%s", label.label, ruleName);
      SubmitRequirement sr =
          SubmitRequirement.builder()
              .setName(label.label)
              .setSubmittabilityExpression(SubmitRequirementExpression.create(expressionString))
              .setAllowOverrideInChildProjects(false)
              .build();
      result.add(
          SubmitRequirementResult.builder()
              .legacy(Optional.of(true))
              .submitRequirement(sr)
              .submittabilityExpressionResult(
                  createExpressionResult(
                      sr.submittabilityExpression(),
                      mapStatus(label),
                      ImmutableList.of(expressionString)))
              .patchSetCommitId(psCommitId)
              .build());
    }
    return result.build();
  }

  private static boolean isBlocking(LabelType labelType) {
    return labelType.getFunction().isBlock() || labelType.getFunction().isRequired();
  }

  private static SubmitRequirementExpression toExpression(List<String> atoms) {
    return SubmitRequirementExpression.create(String.join(" ", atoms));
  }

  private static ImmutableList<String> toExpressionAtomList(LabelType lt) {
    String ignoreSelfApproval =
        lt.isIgnoreSelfApproval() ? ",user=" + ChangeQueryBuilder.ARG_ID_NON_UPLOADER : "";
    switch (lt.getFunction()) {
      case MAX_WITH_BLOCK:
        return ImmutableList.of(
            String.format("label:%s=MAX", lt.getName()) + ignoreSelfApproval,
            String.format("-label:%s=MIN", lt.getName()));
      case ANY_WITH_BLOCK:
        return ImmutableList.of(String.format(String.format("-label:%s=MIN", lt.getName())));
      case MAX_NO_BLOCK:
        return ImmutableList.of(
            String.format(String.format("label:%s=MAX", lt.getName())) + ignoreSelfApproval);
      case NO_BLOCK:
      case NO_OP:
      case PATCH_SET_LOCK:
      default:
        return ImmutableList.of();
    }
  }

  private static Status mapStatus(Label label) {
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

  private static Status mapStatus(SubmitRecord submitRecord) {
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

  private static SubmitRequirementExpressionResult createExpressionResult(
      SubmitRequirementExpression expression, Status status, ImmutableList<String> atoms) {
    return SubmitRequirementExpressionResult.create(
        expression,
        status,
        status == Status.PASS ? atoms : ImmutableList.of(),
        status == Status.FAIL ? atoms : ImmutableList.of());
  }

  private static LabelType getLabelType(List<LabelType> labelTypes, String labelName) {
    return labelTypes.stream()
        .filter(lt -> lt.getName().equals(labelName))
        .collect(MoreCollectors.onlyElement());
  }
}
