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
import com.google.gerrit.server.git.IntegrationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MergeAlways extends SubmitStrategy {
  MergeAlways(SubmitStrategy.Arguments args) {
    super(args);
  }

  @Override
  public List<SubmitStrategyOp> buildOps(Collection<CodeReviewCommit> toMerge)
      throws IntegrationException {
    List<CodeReviewCommit> sorted = args.mergeUtil.reduceToMinimalMerge(args.mergeSorter, toMerge);
    List<SubmitStrategyOp> ops = new ArrayList<>(sorted.size());
    if (args.mergeTip.getInitialTip() == null && !sorted.isEmpty()) {
      // The branch is unborn. Take a fast-forward resolution to
      // create the branch.
      CodeReviewCommit first = sorted.remove(0);
      ops.add(new FastForwardOp(args, first));
    }
    while (!sorted.isEmpty()) {
      CodeReviewCommit n = sorted.remove(0);
      ops.add(new MergeOneOp(args, n));
    }
    return ops;
  }

  static boolean dryRun(
      SubmitDryRun.Arguments args, CodeReviewCommit mergeTip, CodeReviewCommit toMerge)
      throws IntegrationException {
    return args.mergeUtil.canMerge(args.mergeSorter, args.repo, mergeTip, toMerge);
  }
}
