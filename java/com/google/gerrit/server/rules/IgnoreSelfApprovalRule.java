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
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LegacySubmitRequirement;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Rule to require an approval from a user that did not upload the current patch set or block
 * submission.
 */
@Singleton
public class IgnoreSelfApprovalRule implements SubmitRule {
  public static class IgnoreSelfApprovalRuleModule extends AbstractModule {
    @Override
    public void configure() {
      bind(SubmitRule.class)
          .annotatedWith(Exports.named("IgnoreSelfApprovalRule"))
          .to(IgnoreSelfApprovalRule.class);
    }
  }

  @Override
  public Optional<SubmitRecord> evaluate(ChangeData cd) {
    List<LabelType> labelTypes = cd.getLabelTypes().getLabelTypes();
    List<PatchSetApproval> approvals = cd.currentApprovals();
    boolean shouldIgnoreSelfApproval =
        labelTypes.stream().anyMatch(LabelType::isIgnoreSelfApproval);
    if (!shouldIgnoreSelfApproval) {
      // Shortcut to avoid further processing if no label should ignore uploader approvals
      return Optional.empty();
    }

    Account.Id uploader = cd.currentPatchSet().uploader();
    SubmitRecord submitRecord = new SubmitRecord();
    submitRecord.status = SubmitRecord.Status.OK;
    submitRecord.labels = new ArrayList<>(labelTypes.size());
    submitRecord.requirements = new ArrayList<>();

    for (LabelType t : labelTypes) {
      if (!t.isIgnoreSelfApproval()) {
        // The default rules are enough in this case.
        continue;
      }

      LabelFunction labelFunction = t.getFunction();
      if (labelFunction == null) {
        continue;
      }

      Collection<PatchSetApproval> allApprovalsForLabel = filterApprovalsByLabel(approvals, t);
      SubmitRecord.Label allApprovalsCheckResult = labelFunction.check(t, allApprovalsForLabel);
      SubmitRecord.Label ignoreSelfApprovalCheckResult =
          labelFunction.check(t, filterOutPositiveApprovalsOfUser(allApprovalsForLabel, uploader));

      if (labelCheckPassed(allApprovalsCheckResult)
          && !labelCheckPassed(ignoreSelfApprovalCheckResult)) {
        // The label has a valid approval from the uploader and no other valid approval. Set the
        // label
        // to NOT_READY and indicate the need for non-uploader approval as requirement.
        submitRecord.labels.add(ignoreSelfApprovalCheckResult);
        submitRecord.status = SubmitRecord.Status.NOT_READY;
        // Add an additional requirement to be more descriptive on why the label counts as not
        // approved.
        submitRecord.requirements.add(
            LegacySubmitRequirement.builder()
                .setFallbackText("Approval from non-uploader required")
                .setType("non_uploader_approval")
                .build());
      }
    }

    if (submitRecord.labels.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(submitRecord);
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

  @VisibleForTesting
  static Collection<PatchSetApproval> filterOutPositiveApprovalsOfUser(
      Collection<PatchSetApproval> approvals, Account.Id user) {
    return approvals.stream()
        .filter(input -> input.value() < 0 || !input.accountId().equals(user))
        .collect(toImmutableList());
  }

  @VisibleForTesting
  static Collection<PatchSetApproval> filterApprovalsByLabel(
      Collection<PatchSetApproval> approvals, LabelType t) {
    return approvals.stream()
        .filter(input -> input.labelId().get().equals(t.getLabelId().get()))
        .collect(toImmutableList());
  }
}
