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

package com.google.gerrit.server.submit;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.git.CodeReviewCommit;
import java.util.Collection;
import java.util.List;

public class MergeIfNecessary extends SubmitStrategy {
  MergeIfNecessary(SubmitStrategy.Arguments args) {
    super(args);
  }

  @Override
  public ImmutableList<SubmitStrategyOp> buildOps(Collection<CodeReviewCommit> toMerge) {
    List<CodeReviewCommit> sorted = args.mergeUtil.reduceToMinimalMerge(args.mergeSorter, toMerge);
    ImmutableList.Builder<SubmitStrategyOp> ops =
        ImmutableList.builderWithExpectedSize(sorted.size());

    if (args.mergeTip.getInitialTip() == null
        || !args.subscriptionGraph.hasSubscription(args.destBranch)) {
      CodeReviewCommit firstFastForward =
          args.mergeUtil.getFirstFastForward(args.mergeTip.getInitialTip(), args.rw, sorted);
      if (firstFastForward != null && !firstFastForward.equals(args.mergeTip.getInitialTip())) {
        ops.add(new FastForwardOp(args, firstFastForward));
      }
    }

    // For every other commit do a pair-wise merge.
    while (!sorted.isEmpty()) {
      CodeReviewCommit n = sorted.remove(0);
      ops.add(new MergeOneOp(args, n));
    }
    return ops.build();
  }

  static boolean dryRun(
      SubmitDryRun.Arguments args, CodeReviewCommit mergeTip, CodeReviewCommit toMerge) {
    return args.mergeUtil.canFastForwardOrMerge(
        args.mergeSorter, mergeTip, args.rw, args.repo, toMerge);
  }
}
