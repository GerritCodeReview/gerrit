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
import com.google.gerrit.server.change.RebaseChangeOp;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CommitMergeStatus;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.MergeTip;
import com.google.gerrit.server.git.RebaseSorter;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RebaseIfNecessary extends SubmitStrategy {
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final RebaseChangeOp.Factory rebaseFactory;
  private final Map<Change.Id, CodeReviewCommit> newCommits;

  RebaseIfNecessary(SubmitStrategy.Arguments args,
      PatchSetInfoFactory patchSetInfoFactory,
      RebaseChangeOp.Factory rebaseFactory) {
    super(args);
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.rebaseFactory = rebaseFactory;
    this.newCommits = new HashMap<>();
  }

  @Override
  protected MergeTip _run(final CodeReviewCommit branchTip,
      final Collection<CodeReviewCommit> toMerge) throws IntegrationException {
    MergeTip mergeTip = new MergeTip(branchTip, toMerge);
    List<CodeReviewCommit> sorted = sort(toMerge);
    while (!sorted.isEmpty()) {
      CodeReviewCommit n = sorted.remove(0);

      if (mergeTip.getCurrentTip() == null) {
        // The branch is unborn. Take a fast-forward resolution to
        // create the branch.
        //
        n.setStatusCode(CommitMergeStatus.CLEAN_MERGE);
        mergeTip.moveTipTo(n, n);

      } else if (n.getParentCount() == 0) {
        // Refuse to merge a root commit into an existing branch,
        // we cannot obtain a delta for the rebase to apply.
        //
        n.setStatusCode(CommitMergeStatus.CANNOT_REBASE_ROOT);

      } else if (n.getParentCount() == 1) {
        if (args.mergeUtil.canFastForward(args.mergeSorter,
            mergeTip.getCurrentTip(), args.rw, n)) {
          n.setStatusCode(CommitMergeStatus.CLEAN_MERGE);
          mergeTip.moveTipTo(n, n);

        } else {
          try {
            PatchSet newPatchSet = rebase(n, mergeTip);
            List<PatchSetApproval> approvals = Lists.newArrayList();
            for (PatchSetApproval a : args.approvalsUtil.byPatchSet(args.db,
                n.getControl(), n.getPatchsetId())) {
              approvals.add(new PatchSetApproval(newPatchSet.getId(), a));
            }
            // rebaseChange.rebase() may already have copied some approvals,
            // use upsert, not insert, to avoid constraint violation on database
            args.db.patchSetApprovals().upsert(approvals);
            CodeReviewCommit newTip = args.rw.parseCommit(
                ObjectId.fromString(newPatchSet.getRevision().get()));
            mergeTip.moveTipTo(newTip, newTip);
            n.change().setCurrentPatchSet(
                patchSetInfoFactory.get(args.rw, mergeTip.getCurrentTip(),
                    newPatchSet.getId()));
            mergeTip.getCurrentTip().copyFrom(n);
            mergeTip.getCurrentTip().setControl(
                args.changeControlFactory.controlFor(n.change(), args.caller));
            mergeTip.getCurrentTip().setPatchsetId(newPatchSet.getId());
            mergeTip.getCurrentTip().setStatusCode(
                CommitMergeStatus.CLEAN_REBASE);
            newCommits.put(newPatchSet.getId().getParentKey(),
                mergeTip.getCurrentTip());
          } catch (MergeConflictException e) {
            n.setStatusCode(CommitMergeStatus.REBASE_MERGE_CONFLICT);
            throw new IntegrationException(
                "Cannot rebase " + n.name() + ": " + e.getMessage(), e);
          } catch (NoSuchChangeException | OrmException | IOException
              | RestApiException | UpdateException e) {
            throw new IntegrationException("Cannot rebase " + n.name(), e);
          }
        }

      } else if (n.getParentCount() > 1) {
        // There are multiple parents, so this is a merge commit. We
        // don't want to rebase the merge as clients can't easily
        // rebase their history with that merge present and replaced
        // by an equivalent merge with a different first parent. So
        // instead behave as though MERGE_IF_NECESSARY was configured.
        //
        try {
          if (args.rw.isMergedInto(mergeTip.getCurrentTip(), n)) {
            mergeTip.moveTipTo(n, n);
          } else {
            PersonIdent myIdent = args.serverIdent.get();
            mergeTip.moveTipTo(
                args.mergeUtil.mergeOneCommit(myIdent, myIdent,
                    args.repo, args.rw, args.inserter, args.canMergeFlag,
                    args.destBranch, mergeTip.getCurrentTip(), n), n);
          }
          args.mergeUtil.markCleanMerges(args.rw, args.canMergeFlag,
              mergeTip.getCurrentTip(), args.alreadyAccepted);
        } catch (IOException e) {
          throw new IntegrationException("Cannot merge " + n.name(), e);
        }
      }

      args.alreadyAccepted.add(mergeTip.getCurrentTip());
    }

    return mergeTip;
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

  private PatchSet rebase(CodeReviewCommit n, MergeTip mergeTip)
      throws RestApiException, UpdateException, OrmException {
    RebaseChangeOp op = rebaseFactory.create(
          n.getControl(),
          args.db.patchSets().get(n.getPatchsetId()),
          mergeTip.getCurrentTip().name())
        .setCommitterIdent(args.serverIdent.get())
        .setRunHooks(false)
        .setValidatePolicy(CommitValidators.Policy.NONE);
    try (BatchUpdate bu = args.newBatchUpdate(TimeUtil.nowTs())) {
      bu.addOp(n.change().getId(), op);
      bu.execute();
    }
    return op.getPatchSet();
  }

  @Override
  public Map<Change.Id, CodeReviewCommit> getNewCommits() {
    return newCommits;
  }

  @Override
  public boolean dryRun(CodeReviewCommit mergeTip, CodeReviewCommit toMerge)
      throws IntegrationException {
    return !args.mergeUtil.hasMissingDependencies(args.mergeSorter, toMerge)
        && args.mergeUtil.canCherryPick(args.mergeSorter, args.repo, mergeTip,
            args.rw, toMerge);
  }
}
