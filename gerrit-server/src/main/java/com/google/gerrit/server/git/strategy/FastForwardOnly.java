// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.git.strategy;

import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CommitMergeStatus;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.MergeTip;

import java.util.Collection;
import java.util.List;

public class FastForwardOnly extends SubmitStrategy {
  FastForwardOnly(SubmitStrategy.Arguments args) {
    super(args);
  }

  @Override
  protected MergeTip _run(final CodeReviewCommit branchTip,
      final Collection<CodeReviewCommit> toMerge) throws IntegrationException {
    MergeTip mergeTip = new MergeTip(branchTip, toMerge);
    List<CodeReviewCommit> sorted = args.mergeUtil.reduceToMinimalMerge(
        args.mergeSorter, toMerge);
    final CodeReviewCommit newMergeTipCommit =
        args.mergeUtil.getFirstFastForward(branchTip, args.rw, sorted);
    mergeTip.moveTipTo(newMergeTipCommit, newMergeTipCommit);

    while (!sorted.isEmpty()) {
      final CodeReviewCommit n = sorted.remove(0);
      n.setStatusCode(CommitMergeStatus.NOT_FAST_FORWARD);
    }

    args.mergeUtil.markCleanMerges(args.rw, args.canMergeFlag,
        newMergeTipCommit, args.alreadyAccepted);

    return mergeTip;
  }

  static boolean dryRun(SubmitDryRun.Arguments args,
      CodeReviewCommit mergeTip, CodeReviewCommit toMerge)
      throws IntegrationException {
    return args.mergeUtil.canFastForward(args.mergeSorter, mergeTip, args.rw,
        toMerge);
  }
}
