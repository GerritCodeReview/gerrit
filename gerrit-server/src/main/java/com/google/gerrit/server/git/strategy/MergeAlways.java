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

import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.MergeException;

import java.util.List;

public class MergeAlways extends SubmitStrategy {
  MergeAlways(SubmitStrategy.Arguments args) {
    super(args);
  }

  @Override
  protected CodeReviewCommit _run(CodeReviewCommit mergeTip,
      List<CodeReviewCommit> toMerge) throws MergeException {
    args.mergeUtil.reduceToMinimalMerge(args.mergeSorter, toMerge);

    if (mergeTip == null) {
      // The branch is unborn. Take a fast-forward resolution to
      // create the branch.
      mergeTip = toMerge.remove(0);
    }
    while (!toMerge.isEmpty()) {
      mergeTip = args.mergeUtil.mergeOneCommit(args.serverIdent.get(),
          args.repo, args.rw, args.inserter, args.canMergeFlag, args.destBranch,
          mergeTip, toMerge.remove(0));
    }

    PatchSetApproval submitApproval =
        args.mergeUtil.markCleanMerges(args.rw, args.canMergeFlag, mergeTip,
            args.alreadyAccepted);
    setRefLogIdent(submitApproval);

    return mergeTip;
  }

  @Override
  public boolean dryRun(CodeReviewCommit mergeTip, CodeReviewCommit toMerge)
      throws MergeException {
    return args.mergeUtil.canMerge(args.mergeSorter, args.repo, mergeTip,
        toMerge);
  }
}
