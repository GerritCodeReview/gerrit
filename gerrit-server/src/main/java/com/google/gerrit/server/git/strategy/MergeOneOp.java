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

import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.RepoContext;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.MergeTip;

import org.eclipse.jgit.lib.PersonIdent;

import java.io.IOException;

class MergeOneOp extends BatchUpdate.Op {
  private final SubmitStrategy.Arguments args;
  private final MergeTip mergeTip;
  private final CodeReviewCommit toMerge;

  MergeOneOp(SubmitStrategy.Arguments args, MergeTip mergeTip,
      CodeReviewCommit toMerge) {
    this.args = args;
    this.mergeTip = mergeTip;
    this.toMerge = toMerge;
  }

  @Override
  public void updateRepo(RepoContext ctx)
      throws IntegrationException, IOException {
    PersonIdent caller = ctx.getUser().asIdentifiedUser().newCommitterIdent(
        ctx.getWhen(), ctx.getTimeZone());
    if (mergeTip.getCurrentTip() == null) {
      throw new IllegalStateException("cannot merge commit " + toMerge.name()
          + " onto a null tip; expected at least one fast-forward prior to"
          + " this operation");
    }
    // TODO(dborowitz): args.rw is needed because it's a CodeReviewRevWalk.
    // When hoisting BatchUpdate into MergeOp, we will need to teach
    // BatchUpdate how to produce CodeReviewRevWalks.
    CodeReviewCommit merged =
        args.mergeUtil.mergeOneCommit(caller, args.serverIdent,
            ctx.getRepository(), args.rw, ctx.getInserter(), args.canMergeFlag,
            args.destBranch, mergeTip.getCurrentTip(), toMerge);
    mergeTip.moveTipTo(merged, toMerge);
  }
}
