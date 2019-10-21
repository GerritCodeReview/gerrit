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

package com.google.gerrit.server.submit;

import static com.google.gerrit.server.submit.CommitMergeStatus.EMPTY_COMMIT;
import static com.google.gerrit.server.submit.CommitMergeStatus.SKIPPED_IDENTICAL_TREE;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetInfo;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.MergeTip;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.RepoContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

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
    protected void updateRepoImpl(RepoContext ctx)
        throws IntegrationException, IOException, MethodNotAllowedException {
      // If there is only one parent, a cherry-pick can be done by taking the
      // delta relative to that one parent and redoing that on the current merge
      // tip.
      args.rw.parseBody(toMerge);
      psId =
          ChangeUtil.nextPatchSetIdFromChangeRefs(
              ctx.getRepoView().getRefs(getId().toRefPrefix()).keySet(),
              toMerge.change().currentPatchSetId());
      RevCommit mergeTip = args.mergeTip.getCurrentTip();
      args.rw.parseBody(mergeTip);
      String cherryPickCmtMsg = args.mergeUtil.createCommitMessageOnSubmit(toMerge, mergeTip);

      PersonIdent committer =
          args.caller.newCommitterIdent(ctx.getWhen(), args.serverIdent.getTimeZone());
      try {
        newCommit =
            args.mergeUtil.createCherryPickFromCommit(
                ctx.getInserter(),
                ctx.getRepoView().getConfig(),
                args.mergeTip.getCurrentTip(),
                toMerge,
                committer,
                cherryPickCmtMsg,
                args.rw,
                0,
                false,
                false);
      } catch (MergeConflictException mce) {
        // Keep going in the case of a single merge failure; the goal is to
        // cherry-pick as many commits as possible.
        toMerge.setStatusCode(CommitMergeStatus.PATH_CONFLICT);
        return;
      } catch (MergeIdenticalTreeException mie) {
        if (args.project.is(BooleanProjectConfig.REJECT_EMPTY_COMMIT)) {
          toMerge.setStatusCode(EMPTY_COMMIT);
          return;
        }
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
      args.commitStatus.put(newCommit);

      ctx.addRefUpdate(ObjectId.zeroId(), newCommit, psId.toRefName());
      patchSetInfo = args.patchSetInfoFactory.get(ctx.getRevWalk(), newCommit, psId);
    }

    @Override
    public PatchSet updateChangeImpl(ChangeContext ctx) throws NoSuchChangeException, IOException {
      if (newCommit == null && toMerge.getStatusCode() == SKIPPED_IDENTICAL_TREE) {
        return null;
      }
      requireNonNull(
          newCommit,
          () ->
              String.format(
                  "no new commit produced by CherryPick of %s, expected to fail fast",
                  toMerge.change().getId()));
      PatchSet prevPs = args.psUtil.current(ctx.getNotes());
      PatchSet newPs =
          args.psUtil.insert(
              ctx.getRevWalk(),
              ctx.getUpdate(psId),
              psId,
              newCommit,
              prevPs != null ? prevPs.groups() : ImmutableList.of(),
              null,
              null);
      ctx.getChange().setCurrentPatchSet(patchSetInfo);

      // Don't copy approvals, as this is already taken care of by
      // SubmitStrategyOp.

      newCommit.setNotes(ctx.getNotes());
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
                args.rw,
                ctx.getInserter(),
                ctx.getRepoView().getConfig(),
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
