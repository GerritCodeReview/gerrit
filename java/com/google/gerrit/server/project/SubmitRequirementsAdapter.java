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
      ChangeData cd) {
    // We use SubmitRuleOptions.defaults() which does not recompute submit rules for closed changes.
    // This doesn't have an effect since we never call this class (i.e. to evaluate submit
    // requirements) for closed changes.
    List<SubmitRecord> records = cd.submitRecords(SubmitRuleOptions.defaults());
    boolean areForced =
        records.stream().anyMatch(record -> SubmitRecord.Status.FORCED.equals(record.status));
    List<LabelType> labelTypes = cd.getLabelTypes().getLabelTypes();
    ObjectId commitId = cd.currentPatchSet().commitId();
    return records.stream()
        // Filter out the "FORCED" submit record. This is a marker submit record that was just used
        // to indicate that all other records were forced. "FORCED" means that the change was pushed
        // with the %submit option bypassing submit rules.
        .filter(r -> !SubmitRecord.Status.FORCED.equals(r.status))
        .map(r -> createResult(r, labelTypes, commitId, areForced))
        .flatMap(List::stream)
        .collect(
            Collectors.toMap(
                sr -> sr.submitRequirement(),
                Function.identity(),
                (r1, r2) -> {
                  // We convert submit records to submit requirements by generating a separate
                  // submit requirement result for each available label in each submit record.
                  // The SR status is derived from the label status of the submit record.
                  // This conversion might result in duplicate entries.
                  // One such example can be a prolog rule emitting the same label name twice.
                  // Another case might happen if two different submit rules emit the same label
                  // name. In such cases, we need to merge these entries and return a single submit
                  // requirement result. If both entries agree in their status, return any of them.
                  // Otherwise, favour the entry that is blocking submission.
                  if (r1.fulfilled() == r2.fulfilled()) {
                    return r1;
                  }
                  return r1.fulfilled() ? r2 : r1;
                }));
  }

  static List<SubmitRequirementResult> createResult(
      SubmitRecord record, List<LabelType> labelTypes, ObjectId psCommitId, boolean isForced) {
    List<SubmitRequirementResult> results;
    if (record.ruleName != null && record.ruleName.equals("gerrit~DefaultSubmitRule")) {
      results = createFromDefaultSubmitRecord(record.labels, labelTypes, psCommitId, isForced);
    } else {
      results = createFromCustomSubmitRecord(record, psCommitId, isForced);
    }
    logger.atFine().log("Converted submit record %s to submit requirements %s", record, results);
    return results;
  }

  private static List<SubmitRequirementResult> createFromDefaultSubmitRecord(
      List<Label> labels, List<LabelType> labelTypes, ObjectId psCommitId, boolean isForced) {
    ImmutableList.Builder<SubmitRequirementResult> result = ImmutableList.builder();
    for (Label label : labels) {
      Optional<LabelType> maybeLabelType = getLabelType(labelTypes, label.label);
      if (!maybeLabelType.isPresent()) {
        // Label type might have been removed from the project config. We don't have information
        // if it was blocking or not, hence we skip the label.
        continue;
      }
      LabelType labelType = maybeLabelType.get();
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
              .forced(Optional.of(isForced))
              .build());
    }
    return result.build();
  }

  private static List<SubmitRequirementResult> createFromCustomSubmitRecord(
      SubmitRecord record, ObjectId psCommitId, boolean isForced) {
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
              .forced(Optional.of(isForced))
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

  private static Optional<LabelType> getLabelType(List<LabelType> labelTypes, String labelName) {
    List<LabelType> label =
        labelTypes.stream()
            .filter(lt -> lt.getName().equals(labelName))
            .collect(Collectors.toList());
    if (label.isEmpty()) {
      // Label might have been removed from the project.
      logger.atFine().log("Label '%s' was not found for the project.", labelName);
      return Optional.empty();
    } else if (label.size() > 1) {
      logger.atWarning().log("Found more than one label definition for label name '%s'", labelName);
      return Optional.empty();
    }
    return Optional.of(label.get(0));
  }
}
