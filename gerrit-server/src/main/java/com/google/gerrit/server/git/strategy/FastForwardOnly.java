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

import static com.google.gerrit.server.git.strategy.MarkCleanMergesOp.anyChangeId;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.RepoContext;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.MergeTip;
import com.google.gerrit.server.git.UpdateException;

import java.util.Collection;
import java.util.List;

public class FastForwardOnly extends SubmitStrategy {
  FastForwardOnly(SubmitStrategy.Arguments args) {
    super(args);
  }

  @Override
  public MergeTip run(CodeReviewCommit branchTip,
      Collection<CodeReviewCommit> toMerge) throws IntegrationException {
    List<CodeReviewCommit> sorted =
        args.mergeUtil.reduceToMinimalMerge(args.mergeSorter, toMerge);
    MergeTip mergeTip = new MergeTip(branchTip, toMerge);
    try (BatchUpdate u = args.newBatchUpdate(TimeUtil.nowTs())) {
      CodeReviewCommit newTipCommit =
          args.mergeUtil.getFirstFastForward(branchTip, args.rw, sorted);
      if (!newTipCommit.equals(branchTip)) {
        u.addOp(newTipCommit.change().getId(),
            new FastForwardOp(mergeTip, newTipCommit));
      }
      while (!sorted.isEmpty()) {
        CodeReviewCommit n = sorted.remove(0);
        u.addOp(n.change().getId(), new NotFastForwardOp(n));
      }
      u.addOp(anyChangeId(toMerge), new MarkCleanMergesOp(args, mergeTip));

      u.execute();
    } catch (RestApiException | UpdateException e) {
      if (e.getCause() instanceof IntegrationException) {
        throw new IntegrationException(e.getCause().getMessage(), e);
      }
      throw new IntegrationException(
          "Cannot fast-forward into " + args.destBranch);
    }
    return mergeTip;
  }

  private static class NotFastForwardOp extends BatchUpdate.Op {
    private final CodeReviewCommit toMerge;

    private NotFastForwardOp(CodeReviewCommit toMerge) {
      this.toMerge = toMerge;
    }

    @Override
    public void updateRepo(RepoContext ctx) {
      toMerge.setStatusCode(CommitMergeStatus.NOT_FAST_FORWARD);
    }
  }

  static boolean dryRun(SubmitDryRun.Arguments args,
      CodeReviewCommit mergeTip, CodeReviewCommit toMerge)
      throws IntegrationException {
    return args.mergeUtil.canFastForward(args.mergeSorter, mergeTip, args.rw,
        toMerge);
  }
}
