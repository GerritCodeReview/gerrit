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

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.server.change.RebaseChangeOp;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.git.BatchUpdate.RepoContext;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.MergeTip;
import com.google.gerrit.server.git.RebaseSorter;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RebaseIfNecessary extends SubmitStrategy {

  RebaseIfNecessary(SubmitStrategy.Arguments args) {
    super(args);
  }

  @Override
  public List<SubmitStrategyOp> buildOps(
      Collection<CodeReviewCommit> toMerge) throws IntegrationException {
    List<CodeReviewCommit> sorted = sort(toMerge);
    List<SubmitStrategyOp> ops = new ArrayList<>(sorted.size());
    boolean first = true;

    while (!sorted.isEmpty()) {
      CodeReviewCommit n = sorted.remove(0);
      if (first && args.mergeTip.getInitialTip() == null) {
        ops.add(new RebaseUnbornRootOp(n));
      } else if (n.getParentCount() == 0) {
        ops.add(new RebaseRootOp(n));
      } else if (n.getParentCount() == 1) {
        ops.add(new RebaseOneOp(n));
      } else {
        ops.add(new RebaseMultipleParentsOp(n));
      }
      first = false;
    }
    return ops;
  }

  private class RebaseUnbornRootOp extends SubmitStrategyOp {
    private RebaseUnbornRootOp(CodeReviewCommit toMerge) {
      super(RebaseIfNecessary.this.args, toMerge);
    }

    @Override
    public void updateRepoImpl(RepoContext ctx) {
      // The branch is unborn. Take fast-forward resolution to create the
      // branch.
      toMerge.setStatusCode(CommitMergeStatus.CLEAN_MERGE);
      args.mergeTip.moveTipTo(toMerge, toMerge);
      acceptMergeTip(args.mergeTip);
    }
  }

  private class RebaseRootOp extends SubmitStrategyOp {
    private RebaseRootOp(CodeReviewCommit toMerge) {
      super(RebaseIfNecessary.this.args, toMerge);
    }

    @Override
    public void updateRepoImpl(RepoContext ctx) {
      // Refuse to merge a root commit into an existing branch, we cannot obtain
      // a delta for the cherry-pick to apply.
      toMerge.setStatusCode(CommitMergeStatus.CANNOT_REBASE_ROOT);
    }
  }

  private class RebaseOneOp extends SubmitStrategyOp {
    private RebaseChangeOp rebaseOp;
    private CodeReviewCommit newCommit;

    private RebaseOneOp(CodeReviewCommit toMerge) {
      super(RebaseIfNecessary.this.args, toMerge);
    }

    @Override
    public void updateRepoImpl(RepoContext ctx)
        throws IntegrationException, InvalidChangeOperationException,
        RestApiException, IOException, OrmException {
      // TODO(dborowitz): args.rw is needed because it's a CodeReviewRevWalk.
      // When hoisting BatchUpdate into MergeOp, we will need to teach
      // BatchUpdate how to produce CodeReviewRevWalks.
      if (args.mergeUtil.canFastForward(args.mergeSorter,
          args.mergeTip.getCurrentTip(), args.rw, toMerge)) {
        toMerge.setStatusCode(CommitMergeStatus.CLEAN_MERGE);
        args.mergeTip.moveTipTo(toMerge, toMerge);
        acceptMergeTip(args.mergeTip);
        return;
      }

      rebaseOp = args.rebaseFactory.create(
            toMerge.getControl(),
            // Racy read of patch set is ok; see comments in RebaseChangeOp.
            args.db.patchSets().get(toMerge.getPatchsetId()),
            args.mergeTip.getCurrentTip().name())
          .setRunHooks(false)
          .setValidatePolicy(CommitValidators.Policy.NONE);
      try {
        rebaseOp.updateRepo(ctx);
      } catch (MergeConflictException e) {
        toMerge.setStatusCode(CommitMergeStatus.REBASE_MERGE_CONFLICT);
        throw new IntegrationException(
            "Cannot rebase " + toMerge.name() + ": " + e.getMessage(), e);
      }
      newCommit = (CodeReviewCommit) rebaseOp.getRebasedCommit();
      // Initial copy doesn't have new patch set ID since change hasn't been
      // updated yet.
      newCommit.copyFrom(toMerge);
      newCommit.setStatusCode(CommitMergeStatus.CLEAN_REBASE);
      args.mergeTip.moveTipTo(newCommit, newCommit);
      args.commits.put(newCommit);
    }

    @Override
    public void updateChangeImpl(ChangeContext ctx) throws NoSuchChangeException,
        InvalidChangeOperationException, OrmException, IOException  {
      if (rebaseOp == null) {
        // Took the fast-forward option, nothing to do.
        return;
      }

      rebaseOp.updateChange(ctx);
      PatchSet newPatchSet = rebaseOp.getPatchSet();
      List<PatchSetApproval> approvals = Lists.newArrayList();
      // Copy approvals from original patch set.
      for (PatchSetApproval a : args.approvalsUtil.byPatchSet(ctx.getDb(),
          ctx.getControl(), toMerge.getPatchsetId())) {
        approvals.add(new PatchSetApproval(newPatchSet.getId(), a));
      }
      // rebaseOp may already have copied some approvals; use upsert, not
      // insert, to avoid constraint violation on database.
      args.db.patchSetApprovals().upsert(approvals);

      ctx.getChange().setCurrentPatchSet(
          args.patchSetInfoFactory.get(
              args.rw, newCommit, newPatchSet.getId()));
      newCommit.setControl(ctx.getControl());
      newCommit.setPatchsetId(newPatchSet.getId());
      acceptMergeTip(args.mergeTip);
    }

    @Override
    public void postUpdateImpl(Context ctx) throws OrmException {
      if (rebaseOp != null) {
        rebaseOp.postUpdate(ctx);
      }
    }
  }

  private class RebaseMultipleParentsOp extends SubmitStrategyOp {
    private RebaseMultipleParentsOp(CodeReviewCommit toMerge) {
      super(RebaseIfNecessary.this.args, toMerge);
    }

    @Override
    public void updateRepoImpl(RepoContext ctx)
        throws IntegrationException, IOException {
      // There are multiple parents, so this is a merge commit. We don't want
      // to rebase the merge as clients can't easily rebase their history with
      // that merge present and replaced by an equivalent merge with a different
      // first parent. So instead behave as though MERGE_IF_NECESSARY was
      // configured.
      MergeTip mergeTip = args.mergeTip;
      if (args.rw.isMergedInto(mergeTip.getCurrentTip(), toMerge)) {
        mergeTip.moveTipTo(toMerge, toMerge);
        acceptMergeTip(mergeTip);
      } else {
        // TODO(dborowitz): Can't use repo from ctx due to canMergeFlag.
        CodeReviewCommit newTip = args.mergeUtil.mergeOneCommit(
            args.serverIdent, args.serverIdent, args.repo, args.rw,
            args.inserter, args.canMergeFlag, args.destBranch,
            mergeTip.getCurrentTip(), toMerge);
        mergeTip.moveTipTo(newTip, toMerge);
      }
      args.mergeUtil.markCleanMerges(args.rw, args.canMergeFlag,
          mergeTip.getCurrentTip(), args.alreadyAccepted);
      acceptMergeTip(mergeTip);
    }
  }

  private void acceptMergeTip(MergeTip mergeTip) {
    args.alreadyAccepted.add(mergeTip.getCurrentTip());
  }

  private List<CodeReviewCommit> sort(Collection<CodeReviewCommit> toSort)
      throws IntegrationException {
    try {
      List<CodeReviewCommit> result = new RebaseSorter(
          args.rw, args.alreadyAccepted, args.canMergeFlag).sort(toSort);
      Collections.sort(result, CodeReviewCommit.ORDER);
      return result;
    } catch (IOException e) {
      throw new IntegrationException("Commit sorting failed", e);
    }
  }

  static boolean dryRun(SubmitDryRun.Arguments args,
      CodeReviewCommit mergeTip, CodeReviewCommit toMerge)
      throws IntegrationException {
    return !args.mergeUtil.hasMissingDependencies(args.mergeSorter, toMerge)
        && args.mergeUtil.canCherryPick(args.mergeSorter, args.repo, mergeTip,
            args.rw, toMerge);
  }
}
