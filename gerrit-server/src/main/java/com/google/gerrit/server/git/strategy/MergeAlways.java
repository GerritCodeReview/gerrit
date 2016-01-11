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
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.MergeTip;
import com.google.gerrit.server.git.UpdateException;

import java.util.Collection;
import java.util.List;

public class MergeAlways extends SubmitStrategy {
  MergeAlways(SubmitStrategy.Arguments args) {
    super(args);
  }

  @Override
  public MergeTip run(CodeReviewCommit branchTip,
      Collection<CodeReviewCommit> toMerge) throws IntegrationException {
    List<CodeReviewCommit> sorted =
        args.mergeUtil.reduceToMinimalMerge(args.mergeSorter, toMerge);
    MergeTip mergeTip = new MergeTip(branchTip, toMerge);
    try (BatchUpdate u = args.newBatchUpdate(TimeUtil.nowTs())) {
      if (branchTip == null) {
        // The branch is unborn. Take a fast-forward resolution to
        // create the branch.
        CodeReviewCommit first = sorted.remove(0);
        u.addOp(first.change().getId(), new FastForwardOp(mergeTip, first));
      }
      while (!sorted.isEmpty()) {
        CodeReviewCommit n = sorted.remove(0);
        u.addOp(n.change().getId(), new MergeOneOp(args, mergeTip, n));
      }
      u.addOp(anyChangeId(toMerge), new MarkCleanMergesOp(args, mergeTip));

      u.execute();
    } catch (UpdateException e) {
      if (e.getCause() instanceof IntegrationException) {
        throw new IntegrationException(e.getCause().getMessage(), e);
      }
      throw new IntegrationException(
          "Cannot merge into " + args.destBranch);
    } catch (RestApiException e) {
      throw new IntegrationException(
          "Cannot merge into " + args.destBranch);
    }
    return mergeTip;
  }

  static boolean dryRun(SubmitDryRun.Arguments args,
      CodeReviewCommit mergeTip, CodeReviewCommit toMerge)
      throws IntegrationException {
    return args.mergeUtil.canMerge(args.mergeSorter, args.repo, mergeTip,
        toMerge);
  }
}
