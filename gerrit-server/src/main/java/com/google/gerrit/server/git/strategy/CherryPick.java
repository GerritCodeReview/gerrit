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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.gerrit.server.git.strategy.CommitMergeStatus.SKIPPED_IDENTICAL_TREE;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.RepoContext;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.MergeIdenticalTreeException;
import com.google.gerrit.server.git.MergeTip;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.ReceiveCommand;

public class CherryPick extends SubmitStrategy {

  CherryPick(SubmitStrategy.Arguments args) {
    super(args);
  }

  @Override
  public List<SubmitStrategyOp> buildOps(Collection<CodeReviewCommit> toMerge)
      throws IntegrationException {
    List<CodeReviewCommit> sorted = CodeReviewCommit.ORDER.sortedCopy(toMerge);
    List<SubmitStrategyOp> ops = new ArrayList<>(sorted.size());
    boolean first = true;
    while (!sorted.isEmpty()) {
      CodeReviewCommit n = sorted.remove(0);
      if (first && args.mergeTip.getInitialTip() == null) {
        ops.add(new FastForwardOp(args, n));
      } else if (n.getParentCount() == 0) {
        ops.add(new CherryPickRootOp(n));
      } else if (n.getParentCount() == 1) {
        ops.add(new CherryPickOneOp(n));
      } else {
        ops.add(new CherryPickMultipleParentsOp(n));
      }
      first = false;
    }
    return ops;
  }

  private class CherryPickRootOp extends SubmitStrategyOp {
    private CherryPickRootOp(CodeReviewCommit toMerge) {
      super(CherryPick.this.args, toMerge);
    }

    @Override
    public void updateRepoImpl(RepoContext ctx) {
      // Refuse to merge a root commit into an existing branch, we cannot obtain
      // a delta for the cherry-pick to apply.
      toMerge.setStatusCode(CommitMergeStatus.CANNOT_CHERRY_PICK_ROOT);
    }
  }

  private class CherryPickOneOp extends SubmitStrategyOp {
    private PatchSet.Id psId;
    private CodeReviewCommit newCommit;
    private PatchSetInfo patchSetInfo;

    private CherryPickOneOp(CodeReviewCommit toMerge) {
      super(CherryPick.this.args, toMerge);
    }

    @Override
    protected void updateRepoImpl(RepoContext ctx) throws IntegrationException, IOException {
      // If there is only one parent, a cherry-pick can be done by taking the
      // delta relative to that one parent and redoing that on the current merge
      // tip.
      args.rw.parseBody(toMerge);
      psId = ChangeUtil.nextPatchSetId(args.repo, toMerge.change().currentPatchSetId());
      String cherryPickCmtMsg = args.mergeUtil.createCherryPickCommitMessage(toMerge);

      PersonIdent committer =
          args.caller.newCommitterIdent(ctx.getWhen(), args.serverIdent.getTimeZone());
      try {
        newCommit =
            args.mergeUtil.createCherryPickFromCommit(
                args.repo,
                args.inserter,
                args.mergeTip.getCurrentTip(),
                toMerge,
                committer,
                cherryPickCmtMsg,
                args.rw,
                0);
      } catch (MergeConflictException mce) {
        // Keep going in the case of a single merge failure; the goal is to
        // cherry-pick as many commits as possible.
        toMerge.setStatusCode(CommitMergeStatus.PATH_CONFLICT);
        return;
      } catch (MergeIdenticalTreeException mie) {
        toMerge.setStatusCode(SKIPPED_IDENTICAL_TREE);
        return;
      }
      // Initial copy doesn't have new patch set ID since change hasn't been
      // updated yet.
      newCommit = amendGitlink(newCommit);
      newCommit.copyFrom(toMerge);
      newCommit.setPatchsetId(psId);
      newCommit.setStatusCode(CommitMergeStatus.CLEAN_PICK);
      args.mergeTip.moveTipTo(newCommit, newCommit);
      args.commits.put(newCommit);

      ctx.addRefUpdate(new ReceiveCommand(ObjectId.zeroId(), newCommit, psId.toRefName()));
      patchSetInfo = args.patchSetInfoFactory.get(ctx.getRevWalk(), newCommit, psId);
    }

    @Override
    public PatchSet updateChangeImpl(ChangeContext ctx)
        throws OrmException, NoSuchChangeException, IOException {
      if (newCommit == null && toMerge.getStatusCode() == SKIPPED_IDENTICAL_TREE) {
        return null;
      }
      checkNotNull(
          newCommit,
          "no new commit produced by CherryPick of %s, expected to fail fast",
          toMerge.change().getId());
      PatchSet prevPs = args.psUtil.current(ctx.getDb(), ctx.getNotes());
      PatchSet newPs =
          args.psUtil.insert(
              ctx.getDb(),
              ctx.getRevWalk(),
              ctx.getUpdate(psId),
              psId,
              newCommit,
              false,
              prevPs != null ? prevPs.getGroups() : ImmutableList.<String>of(),
              null);
      ctx.getChange().setCurrentPatchSet(patchSetInfo);

      // Don't copy approvals, as this is already taken care of by
      // SubmitStrategyOp.

      newCommit.setControl(ctx.getControl());
      return newPs;
    }
  }

  private class CherryPickMultipleParentsOp extends SubmitStrategyOp {
    private CherryPickMultipleParentsOp(CodeReviewCommit toMerge) {
      super(CherryPick.this.args, toMerge);
    }

    @Override
    public void updateRepoImpl(RepoContext ctx) throws IntegrationException, IOException {
      if (args.mergeUtil.hasMissingDependencies(args.mergeSorter, toMerge)) {
        // One or more dependencies were not met. The status was already marked
        // on the commit so we have nothing further to perform at this time.
        return;
      }
      // There are multiple parents, so this is a merge commit. We don't want
      // to cherry-pick the merge as clients can't easily rebase their history
      // with that merge present and replaced by an equivalent merge with a
      // different first parent. So instead behave as though MERGE_IF_NECESSARY
      // was configured.
      MergeTip mergeTip = args.mergeTip;
      if (args.rw.isMergedInto(mergeTip.getCurrentTip(), toMerge)
          && !args.submoduleOp.hasSubscription(args.destBranch)) {
        mergeTip.moveTipTo(toMerge, toMerge);
      } else {
        PersonIdent myIdent = new PersonIdent(args.serverIdent, ctx.getWhen());
        CodeReviewCommit result =
            args.mergeUtil.mergeOneCommit(
                myIdent,
                myIdent,
                args.repo,
                args.rw,
                args.inserter,
                args.destBranch,
                mergeTip.getCurrentTip(),
                toMerge);
        result = amendGitlink(result);
        mergeTip.moveTipTo(result, toMerge);
        args.mergeUtil.markCleanMerges(
            args.rw, args.canMergeFlag, mergeTip.getCurrentTip(), args.alreadyAccepted);
      }
    }
  }

  static boolean dryRun(
      SubmitDryRun.Arguments args, CodeReviewCommit mergeTip, CodeReviewCommit toMerge)
      throws IntegrationException {
    return args.mergeUtil.canCherryPick(args.mergeSorter, args.repo, mergeTip, args.rw, toMerge);
  }
}
