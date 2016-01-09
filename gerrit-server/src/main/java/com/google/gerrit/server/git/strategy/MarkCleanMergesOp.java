// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.MergeTip;

class MarkCleanMergesOp extends BatchUpdate.Op {
  static Change.Id anyChangeId(Iterable<CodeReviewCommit> commits) {
    for (CodeReviewCommit c : commits) {
      if (c.change() != null) {
        return c.change().getId();
      }
    }
    throw new IllegalArgumentException(
        "no CodeReviewCommits have changes: " + commits);
  }
  private final SubmitStrategy.Arguments args;
  private final MergeTip mergeTip;

  MarkCleanMergesOp(SubmitStrategy.Arguments args,
      MergeTip mergeTip) {
    this.args = args;
    this.mergeTip = mergeTip;
  }

  @Override
  public void postUpdate(Context ctx) throws IntegrationException {
    // TODO(dborowitz): args.rw is needed because it's a CodeReviewRevWalk.
    // When hoisting BatchUpdate into MergeOp, we will need to teach
    // BatchUpdate how to produce CodeReviewRevWalks.
    args.mergeUtil.markCleanMerges(args.rw, args.canMergeFlag,
        mergeTip.getCurrentTip(), args.alreadyAccepted);
  }
}
