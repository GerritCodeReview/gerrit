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
import com.google.gerrit.server.git.CommitMergeStatus;
import com.google.gerrit.server.git.MergeException;

import java.util.Collection;
import java.util.List;

public class FastForwardOnly extends SubmitStrategy {
  FastForwardOnly(SubmitStrategy.Arguments args) {
    super(args);
  }

  @Override
  protected CodeReviewCommit _run(CodeReviewCommit mergeTip,
      Collection<CodeReviewCommit> toMerge) throws MergeException {
    List<CodeReviewCommit> sorted = args.mergeUtil.reduceToMinimalMerge(
        args.mergeSorter, toMerge);
    CodeReviewCommit newMergeTip =
        args.mergeUtil.getFirstFastForward(mergeTip, args.rw, sorted);

    while (!sorted.isEmpty()) {
      CodeReviewCommit n = sorted.remove(0);
      n.setStatusCode(CommitMergeStatus.NOT_FAST_FORWARD);
    }

    PatchSetApproval submitApproval = args.mergeUtil.markCleanMerges(args.rw,
        args.canMergeFlag, newMergeTip, args.alreadyAccepted);
    setRefLogIdent(submitApproval);

    return newMergeTip;
  }

  @Override
  public boolean retryOnLockFailure() {
    return false;
  }

  @Override
  public boolean dryRun(CodeReviewCommit mergeTip,
      CodeReviewCommit toMerge) throws MergeException {
    return args.mergeUtil.canFastForward(args.mergeSorter, mergeTip, args.rw,
        toMerge);
  }
}
