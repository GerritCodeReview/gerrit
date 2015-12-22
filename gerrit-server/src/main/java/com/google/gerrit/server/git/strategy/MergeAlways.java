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
import com.google.gerrit.server.git.MergeTip;

import org.eclipse.jgit.lib.PersonIdent;

import java.util.Collection;
import java.util.List;

public class MergeAlways extends SubmitStrategy {
  MergeAlways(SubmitStrategy.Arguments args) {
    super(args);
  }

  @Override
  public MergeTip run(CodeReviewCommit branchTip,
      Collection<CodeReviewCommit> toMerge) throws IntegrationException {
  List<CodeReviewCommit> sorted = args.mergeUtil.reduceToMinimalMerge(args.mergeSorter, toMerge);
    MergeTip mergeTip;
    if (branchTip == null) {
      // The branch is unborn. Take a fast-forward resolution to
      // create the branch.
      mergeTip = new MergeTip(sorted.get(0), toMerge);
      sorted.remove(0);
    } else {
      mergeTip = new MergeTip(branchTip, toMerge);
    }
    while (!sorted.isEmpty()) {
      CodeReviewCommit mergedFrom = sorted.remove(0);
      PersonIdent serverIdent = args.serverIdent.get();
      PersonIdent caller = args.caller.newCommitterIdent(
          serverIdent.getWhen(), serverIdent.getTimeZone());
      CodeReviewCommit newTip =
          args.mergeUtil.mergeOneCommit(caller, serverIdent,
              args.repo, args.rw, args.inserter, args.canMergeFlag,
              args.destBranch, mergeTip.getCurrentTip(), mergedFrom);
      mergeTip.moveTipTo(newTip, mergedFrom);
    }

    args.mergeUtil.markCleanMerges(args.rw, args.canMergeFlag,
        mergeTip.getCurrentTip(), args.alreadyAccepted);

    return mergeTip;
  }

  static boolean dryRun(SubmitDryRun.Arguments args,
      CodeReviewCommit mergeTip, CodeReviewCommit toMerge)
      throws IntegrationException {
    return args.mergeUtil.canMerge(args.mergeSorter, args.repo, mergeTip,
        toMerge);
  }
}
