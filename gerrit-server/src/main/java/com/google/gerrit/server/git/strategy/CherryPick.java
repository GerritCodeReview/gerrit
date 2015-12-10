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
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.RepoContext;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CommitMergeStatus;
import com.google.gerrit.server.git.GroupCollector;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.MergeIdenticalTreeException;
import com.google.gerrit.server.git.MergeTip;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CherryPick extends SubmitStrategy {
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final Map<Change.Id, CodeReviewCommit> newCommits;

  CherryPick(SubmitStrategy.Arguments args,
      PatchSetInfoFactory patchSetInfoFactory) {
    super(args);
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.newCommits = new HashMap<>();
  }

  @Override
  protected MergeTip _run(CodeReviewCommit branchTip,
      Collection<CodeReviewCommit> toMerge) throws IntegrationException {
    MergeTip mergeTip = new MergeTip(branchTip, toMerge);
    List<CodeReviewCommit> sorted = CodeReviewCommit.ORDER.sortedCopy(toMerge);
    boolean first = true;
    try (BatchUpdate u = args.newBatchUpdate(TimeUtil.nowTs())) {
      while (!sorted.isEmpty()) {
        CodeReviewCommit n = sorted.remove(0);
        Change.Id cid = n.change().getId();
        if (first && branchTip == null) {
          u.addOp(cid, new CherryPickUnbornRootOp(mergeTip, n));
        } else if (n.getParentCount() == 0) {
          u.addOp(cid, new CherryPickRootOp(n));
        } else if (n.getParentCount() == 1) {
          u.addOp(cid, new CherryPickOneOp(mergeTip, n));
        } else {
          u.addOp(cid, new CherryPickMultipleParentsOp(mergeTip, n));
        }
        first = false;
      }
      u.execute();
    } catch (UpdateException | RestApiException e) {
      throw new IntegrationException(
          "Cannot cherry-pick onto " + args.destBranch);
    }
    // TODO(dborowitz): When BatchUpdate is hoisted out of CherryPick,
    // SubmitStrategy should probably no longer return MergeTip, instead just
    // mutating a single shared MergeTip passed in from the caller.
    return mergeTip;
  }

  private static class CherryPickUnbornRootOp extends BatchUpdate.Op {
    private final MergeTip mergeTip;
    private final CodeReviewCommit toMerge;

    private CherryPickUnbornRootOp(MergeTip mergeTip,
        CodeReviewCommit toMerge) {
      this.mergeTip = mergeTip;
      this.toMerge = toMerge;
    }

    @Override
    public void updateRepo(RepoContext ctx) {
      // The branch is unborn. Take fast-forward resolution to create the
      // branch.
      mergeTip.moveTipTo(toMerge, toMerge);
      toMerge.setStatusCode(CommitMergeStatus.CLEAN_MERGE);
    }
  }

  private static class CherryPickRootOp extends BatchUpdate.Op {
    private final CodeReviewCommit toMerge;

    private CherryPickRootOp(CodeReviewCommit toMerge) {
      this.toMerge = toMerge;
    }

    @Override
    public void updateRepo(RepoContext ctx) {
      // Refuse to merge a root commit into an existing branch, we cannot obtain
      // a delta for the cherry-pick to apply.
      toMerge.setStatusCode(CommitMergeStatus.CANNOT_CHERRY_PICK_ROOT);
    }
  }

  private class CherryPickOneOp extends BatchUpdate.Op {
    private final MergeTip mergeTip;
    private final CodeReviewCommit toMerge;

    private PatchSet.Id psId;
    private CodeReviewCommit newCommit;
    private PatchSetInfo patchSetInfo;

    private CherryPickOneOp(MergeTip mergeTip, CodeReviewCommit n) {
      this.mergeTip = mergeTip;
      this.toMerge = n;
    }

    @Override
    public void updateRepo(RepoContext ctx) throws IOException {
      // If there is only one parent, a cherry-pick can be done by taking the
      // delta relative to that one parent and redoing that on the current merge
      // tip.
      args.rw.parseBody(toMerge);
      psId = ChangeUtil.nextPatchSetId(
          args.repo, toMerge.change().currentPatchSetId());
      String cherryPickCmtMsg =
          args.mergeUtil.createCherryPickCommitMessage(toMerge);

      PersonIdent committer = args.caller.newCommitterIdent(
          ctx.getWhen(), args.serverIdent.get().getTimeZone());
      try {
        newCommit = args.mergeUtil.createCherryPickFromCommit(
            args.repo, args.inserter, mergeTip.getCurrentTip(), toMerge,
            committer, cherryPickCmtMsg, args.rw);
        ctx.addRefUpdate(
            new ReceiveCommand(ObjectId.zeroId(), newCommit, psId.toRefName()));
        patchSetInfo =
            patchSetInfoFactory.get(ctx.getRevWalk(), newCommit, psId);
      } catch (MergeConflictException mce) {
        // Keep going in the case of a single merge failure; the goal is to
        // cherry-pick as many commits as possible.
        toMerge.setStatusCode(CommitMergeStatus.PATH_CONFLICT);
      } catch (MergeIdenticalTreeException mie) {
        toMerge.setStatusCode(CommitMergeStatus.ALREADY_MERGED);
      }
    }

    @Override
    public void updateChange(ChangeContext ctx) throws OrmException,
         NoSuchChangeException {
      if (newCommit == null) {
        // Merge conflict; don't update change.
        return;
      }
      ctx.getChangeUpdate().setPatchSetId(psId);
      PatchSet ps = new PatchSet(psId);
      ps.setCreatedOn(ctx.getWhen());
      ps.setUploader(args.caller.getAccountId());
      ps.setRevision(new RevId(newCommit.getId().getName()));

      Change c = toMerge.change();
      ps.setGroups(GroupCollector.getCurrentGroups(args.db, c));
      args.db.patchSets().insert(Collections.singleton(ps));
      c.setCurrentPatchSet(patchSetInfo);
      args.db.changes().update(Collections.singletonList(c));

      List<PatchSetApproval> approvals = Lists.newArrayList();
      for (PatchSetApproval a : args.approvalsUtil.byPatchSet(
          args.db, toMerge.getControl(), toMerge.getPatchsetId())) {
        approvals.add(new PatchSetApproval(ps.getId(), a));
        ctx.getChangeUpdate().putApproval(a.getLabel(), a.getValue());
      }
      args.db.patchSetApprovals().insert(approvals);

      newCommit.copyFrom(toMerge);
      newCommit.setStatusCode(CommitMergeStatus.CLEAN_PICK);
      newCommit.setControl(
          args.changeControlFactory.controlFor(toMerge.change(), args.caller));
      mergeTip.moveTipTo(newCommit, newCommit);
      newCommits.put(c.getId(), newCommit);
    }
  }

  private class CherryPickMultipleParentsOp extends BatchUpdate.Op {
    private final MergeTip mergeTip;
    private final CodeReviewCommit toMerge;

    private CherryPickMultipleParentsOp(MergeTip mergeTip,
        CodeReviewCommit toMerge) {
      this.mergeTip = mergeTip;
      this.toMerge = toMerge;
    }

    @Override
    public void updateRepo(RepoContext ctx)
        throws IntegrationException, IOException {
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
      if (args.rw.isMergedInto(mergeTip.getCurrentTip(), toMerge)) {
        mergeTip.moveTipTo(toMerge, toMerge);
      } else {
        PersonIdent myIdent =
            new PersonIdent(args.serverIdent.get(), ctx.getWhen());
        CodeReviewCommit result = args.mergeUtil.mergeOneCommit(myIdent,
            myIdent, args.repo, args.rw, args.inserter,
            args.canMergeFlag, args.destBranch, mergeTip.getCurrentTip(),
            toMerge);
        mergeTip.moveTipTo(result, toMerge);
      }
      args.mergeUtil.markCleanMerges(args.rw, args.canMergeFlag,
          mergeTip.getCurrentTip(), args.alreadyAccepted);
    }
  }

  @Override
  public Map<Change.Id, CodeReviewCommit> getNewCommits() {
    return newCommits;
  }

  @Override
  public boolean dryRun(CodeReviewCommit mergeTip, CodeReviewCommit toMerge)
      throws IntegrationException {
    return args.mergeUtil.canCherryPick(args.mergeSorter, args.repo,
        mergeTip, args.rw, toMerge);
  }
}
