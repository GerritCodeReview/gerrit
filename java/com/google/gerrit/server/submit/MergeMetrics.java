// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.submit;

import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.MagicLabelPredicate;
import com.google.gerrit.server.query.change.SubmitRequirementChangeQueryBuilder;
import com.google.inject.Inject;
import com.google.inject.Provider;

/** Metrics are recorded when a change is merged (aka submitted). */
public class MergeMetrics {
  private final Provider<SubmitRequirementChangeQueryBuilder> submitRequirementChangequeryBuilder;

  // TODO: This metric is for measuring the impact of allowing users to rebase changes on behalf of
  // the uploader. Once this feature has been rolled out and its impact as been measured, we may
  // remove this metric.
  private final Counter0 countChangesThatWereSubmittedWithRebaserApproval;

  @Inject
  public MergeMetrics(
      Provider<SubmitRequirementChangeQueryBuilder> submitRequirementChangequeryBuilder,
      MetricMaker metricMaker) {
    this.submitRequirementChangequeryBuilder = submitRequirementChangequeryBuilder;

    this.countChangesThatWereSubmittedWithRebaserApproval =
        metricMaker.newCounter(
            "change/submitted_with_rebaser_approval",
            new Description(
                    "Number of rebased changes that were submitted with a Code-Review approval of"
                        + " the rebaser that would not have been submittable if the rebase was not"
                        + " done on behalf of the uploader.")
                .setRate());
  }

  public void countChangesThatWereSubmittedWithRebaserApproval(ChangeData cd) {
    if (isRebaseOnBehalfOfUploader(cd)
        && hasCodeReviewApprovalOfRealUploader(cd)
        && ignoresCodeReviewApprovalsOfUploader(cd)) {
      // 1. The patch set that is being submitted was created by rebasing on behalf of the uploader.
      // The uploader of the patch set is the original uploader on whom's behalf the rebase was
      // done. The real uploader is the user that did the rebase on behalf of the uploader (e.g. by
      // clicking on the rebase button).
      //
      // 2. The change has Code-Review approvals of the real uploader (aka the rebaser).
      //
      // 3. Code-Review approvals of the uploader are ignored.
      //
      // If instead of a rebase on behalf of the uploader a normal rebase would have been done the
      // rebaser would have been the uploader of the patch set. In this case the Code-Review
      // approval of the rebaser would not have counted since Code-Review approvals of the uploader
      // are ignored.
      //
      // In this case we assume that the change would not be submittable if a normal rebase had been
      // done. This is not always correct (e.g. if there are approvals of multiple reviewers) but
      // it's good enough for the metric.
      countChangesThatWereSubmittedWithRebaserApproval.increment();
    }
  }

  private boolean isRebaseOnBehalfOfUploader(ChangeData cd) {
    // If the uploader differs from the real uploader the upload of the patch set has been
    // impersonated. Impersonating the uploader is only allowed on rebase by rebasing on behalf of
    // the uploader. Hence if the current patch set has different accounts as uploader and real
    // uploader we can assume that it was created by rebase on behalf of the uploader.
    return !cd.currentPatchSet().uploader().equals(cd.currentPatchSet().realUploader());
  }

  private boolean hasCodeReviewApprovalOfRealUploader(ChangeData cd) {
    return cd.currentApprovals().stream()
        .anyMatch(psa -> psa.accountId().equals(cd.currentPatchSet().realUploader()));
  }

  private boolean ignoresCodeReviewApprovalsOfUploader(ChangeData cd) {
    for (SubmitRequirement submitRequirement : cd.submitRequirements().keySet()) {
      try {
        Predicate<ChangeData> predicate =
            submitRequirementChangequeryBuilder
                .get()
                .parse(submitRequirement.submittabilityExpression().expressionString());
        return ignoresCodeReviewApprovalsOfUploader(predicate);
      } catch (QueryParseException e) {
        return false;
      }
    }
    return false;
  }

  private boolean ignoresCodeReviewApprovalsOfUploader(Predicate<ChangeData> predicate) {
    if (predicate.getChildCount() == 0) {
      // Submit requirements may require a Code-Review approval but ignore approvals by the
      // uploader. This is done by using a label predicate with 'user=non_uploader' or
      // 'user=non_contributor', e.g. 'label:Code-Review=+2,user=non_uploader'. After the submit
      // requirement expression has been parsed these label predicates are represented by
      // MagicLabelPredicate in the predicate tree. Hence to know whether Code-Review approvals of
      // the uploader are ignored, we must check if there is any MagicLabelPredicate for the
      // Code-Review label that ignores approvals of the uploader (aka has user set to non_uploader
      // or non_contributor).
      if (predicate instanceof MagicLabelPredicate) {
        MagicLabelPredicate magicLabelPredicate = (MagicLabelPredicate) predicate;
        if (magicLabelPredicate.getLabel().equalsIgnoreCase("Code-Review")
            && magicLabelPredicate.ignoresUploaderApprovals()) {
          return true;
        }
      }
      return false;
    }

    for (Predicate<ChangeData> childPredicate : predicate.getChildren()) {
      if (ignoresCodeReviewApprovalsOfUploader(childPredicate)) {
        return true;
      }
    }
    return false;
  }
}
