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

package com.google.gerrit.server.rules;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.LabelFunction;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitRequirement;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Rule to require a non-author approval or block submission. */
@Singleton
public class RequireNonUploaderApprovalRule implements SubmitRule {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String E_UNABLE_TO_FETCH_UPLOADER = "Unable to fetch uploader";
  private static final String E_UNABLE_TO_FETCH_LABELS =
      "Unable to fetch labels and approvals for the change";

  public static class Module extends FactoryModule {
    @Override
    public void configure() {
      bind(SubmitRule.class)
          .annotatedWith(Exports.named("RequireNonUploaderApprovalRule"))
          .to(RequireNonUploaderApprovalRule.class);
    }
  }

  @Inject
  RequireNonUploaderApprovalRule() {}

  @Override
  public Collection<SubmitRecord> evaluate(ChangeData cd, SubmitRuleOptions options) {
    List<LabelType> labelTypes;
    List<PatchSetApproval> approvals;
    try {
      labelTypes = cd.getLabelTypes().getLabelTypes();
      approvals = cd.currentApprovals();
    } catch (OrmException e) {
      logger.atWarning().withCause(e).log(E_UNABLE_TO_FETCH_LABELS);
      return singletonRuleError(E_UNABLE_TO_FETCH_LABELS);
    }

    boolean shouldIgnoreUploaderApproval =
        labelTypes.stream().anyMatch(l -> l.ignoreUploaderSelfApproval());
    if (!shouldIgnoreUploaderApproval) {
      // Shortcut to avoid further processing if no label should ignore author approvals
      return ImmutableList.of();
    }

    Account.Id uploader;
    try {
      uploader = cd.currentPatchSet().getUploader();
    } catch (OrmException e) {
      logger.atWarning().withCause(e).log(E_UNABLE_TO_FETCH_UPLOADER);
      return singletonRuleError(E_UNABLE_TO_FETCH_UPLOADER);
    }

    SubmitRecord submitRecord = new SubmitRecord();
    submitRecord.status = SubmitRecord.Status.OK;
    submitRecord.labels = new ArrayList<>(labelTypes.size());
    submitRecord.requirements = new ArrayList<>();

    for (LabelType t : labelTypes) {
      if (!t.ignoreUploaderSelfApproval()) {
        // The default rules are enough in this case.
        continue;
      }

      LabelFunction labelFunction = t.getFunction();
      if (labelFunction == null) {
        continue;
      }

      SubmitRecord.Label ignoreUploaderCheckResult =
          labelFunction.check(t, getNonUploaderApprovalsForLabel(approvals, t, uploader));
      SubmitRecord.Label uploaderCheckResult =
          labelFunction.check(t, getAllApprovalsForLabel(approvals, t));

      if (labelCheckPassed(uploaderCheckResult) && !labelCheckPassed(ignoreUploaderCheckResult)) {
        // The label has a valid approval from the author and no other valid approval. Set the label
        // to NOT_READY and indicate the need for non-author approval as requirement.
        submitRecord.labels.add(ignoreUploaderCheckResult);
        submitRecord.status = SubmitRecord.Status.NOT_READY;
        // Add an additional requirement to be more descriptive on why the label counts as not
        // approved.
        submitRecord.requirements.add(
            SubmitRequirement.builder()
                .setFallbackText("Approval from non-uploader required")
                .setType("non_uploader_approval")
                .build());
      }
    }

    if (submitRecord.labels.isEmpty()) {
      return ImmutableList.of();
    }

    return ImmutableList.of(submitRecord);
  }

  /** Returns the approvals for a given label. */
  private static Collection<PatchSetApproval> getAllApprovalsForLabel(
      Collection<PatchSetApproval> approvals, LabelType t) {
    return approvals
        .stream()
        .filter(input -> input.getLabelId().get().equals(t.getLabelId().get()))
        .collect(toImmutableList());
  }

  private static boolean labelCheckPassed(SubmitRecord.Label label) {
    switch (label.status) {
      case OK:
      case MAY:
        return true;

      case NEED:
      case REJECT:
      case IMPOSSIBLE:
        return false;
    }
    return false;
  }

  private static Collection<SubmitRecord> singletonRuleError(String reason) {
    SubmitRecord submitRecord = new SubmitRecord();
    submitRecord.errorMessage = reason;
    submitRecord.status = SubmitRecord.Status.RULE_ERROR;
    return ImmutableList.of(submitRecord);
  }

  /**
   * Returns the approvals for a given label, for everyone except from `user`, except if the vote is
   * negative.
   */
  @VisibleForTesting
  static Collection<PatchSetApproval> getNonUploaderApprovalsForLabel(
      Collection<PatchSetApproval> approvals, LabelType t, Account.Id user) {
    return approvals
        .stream()
        .filter(input -> input.getValue() < 0 || !input.getAccountId().equals(user))
        .filter(input -> input.getLabelId().get().equals(t.getLabelId().get()))
        .collect(toImmutableList());
  }
}
