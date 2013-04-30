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

package com.google.gerrit.server.git;

import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.changedetail.PathConflictException;
import com.google.gerrit.server.changedetail.RebaseChange;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.lib.ObjectId;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RebaseIfNecessary extends SubmitStrategy {

  private final RebaseChange rebaseChange;
  private final Map<Change.Id, CodeReviewCommit> newCommits;

  RebaseIfNecessary(final SubmitStrategy.Arguments args,
      final RebaseChange rebaseChange) {
    super(args);
    this.rebaseChange = rebaseChange;
    this.newCommits = new HashMap<Change.Id, CodeReviewCommit>();
  }

  @Override
  protected CodeReviewCommit _run(final CodeReviewCommit mergeTip,
      final List<CodeReviewCommit> toMerge) throws MergeException {
    CodeReviewCommit newMergeTip = mergeTip;
    sort(toMerge);

    while (!toMerge.isEmpty()) {
      final CodeReviewCommit n = toMerge.remove(0);

      if (newMergeTip == null) {
        // The branch is unborn. Take a fast-forward resolution to
        // create the branch.
        //
        newMergeTip = n;
        n.statusCode = CommitMergeStatus.CLEAN_MERGE;

      } else if (n.getParentCount() == 0) {
        // Refuse to merge a root commit into an existing branch,
        // we cannot obtain a delta for the rebase to apply.
        //
        n.statusCode = CommitMergeStatus.CANNOT_REBASE_ROOT;

      } else if (n.getParentCount() == 1) {
        if (args.mergeUtil.canFastForward(
            args.mergeSorter, newMergeTip, args.rw, n)) {
          newMergeTip = n;
          n.statusCode = CommitMergeStatus.CLEAN_MERGE;

        } else {
          try {
            final IdentifiedUser submitter =
                args.identifiedUserFactory.create(args.mergeUtil.getSubmitter(
                    n.patchsetId).getAccountId());
            final PatchSet newPatchSet =
                rebaseChange.rebase(args.repo, args.rw, args.inserter,
                    n.patchsetId, n.change, submitter,
                    newMergeTip, args.mergeUtil);
            List<PatchSetApproval> approvals = Lists.newArrayList();
            for (PatchSetApproval a : args.mergeUtil.getApprovalsForCommit(n)) {
              approvals.add(new PatchSetApproval(newPatchSet.getId(), a));
            }
            args.db.patchSetApprovals().insert(approvals);
            newMergeTip =
                (CodeReviewCommit) args.rw.parseCommit(ObjectId
                    .fromString(newPatchSet.getRevision().get()));
            newMergeTip.copyFrom(n);
            newMergeTip.patchsetId = newPatchSet.getId();
            newMergeTip.change =
                args.db.changes().get(newPatchSet.getId().getParentKey());
            newMergeTip.statusCode = CommitMergeStatus.CLEAN_REBASE;
            newCommits.put(newPatchSet.getId().getParentKey(), newMergeTip);
            setRefLogIdent(args.mergeUtil.getSubmitter(n.patchsetId));
          } catch (PathConflictException e) {
            n.statusCode = CommitMergeStatus.PATH_CONFLICT;
          } catch (NoSuchChangeException e) {
            throw new MergeException("Cannot rebase " + n.name(), e);
          } catch (OrmException e) {
            throw new MergeException("Cannot rebase " + n.name(), e);
          } catch (IOException e) {
            throw new MergeException("Cannot rebase " + n.name(), e);
          } catch (InvalidChangeOperationException e) {
            throw new MergeException("Cannot rebase " + n.name(), e);
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
          if (args.rw.isMergedInto(newMergeTip, n)) {
            newMergeTip = n;
          } else {
            newMergeTip = args.mergeUtil.mergeOneCommit(
                args.myIdent, args.repo, args.rw, args.inserter,
                args.canMergeFlag, args.destBranch, newMergeTip, n);
          }
          final PatchSetApproval submitApproval = args.mergeUtil.markCleanMerges(
              args.rw, args.canMergeFlag, newMergeTip, args.alreadyAccepted);
          setRefLogIdent(submitApproval);
        } catch (IOException e) {
          throw new MergeException("Cannot merge " + n.name(), e);
        }
      }

      args.alreadyAccepted.add(newMergeTip);
    }

    return newMergeTip;
  }

  private void sort(final List<CodeReviewCommit> toSort) throws MergeException {
    try {
      final List<CodeReviewCommit> sorted =
          new RebaseSorter(args.rw, args.alreadyAccepted, args.canMergeFlag)
              .sort(toSort);
      toSort.clear();
      toSort.addAll(sorted);
    } catch (IOException e) {
      throw new MergeException("Commit sorting failed", e);
    }
  }

  @Override
  public Map<Change.Id, CodeReviewCommit> getNewCommits() {
    return newCommits;
  }

  @Override
  public boolean dryRun(final CodeReviewCommit mergeTip,
      final CodeReviewCommit toMerge) throws MergeException {
    return !args.mergeUtil.hasMissingDependencies(args.mergeSorter, toMerge)
        && args.mergeUtil.canCherryPick(args.mergeSorter, args.repo, mergeTip,
            args.rw, toMerge);
  }
}
