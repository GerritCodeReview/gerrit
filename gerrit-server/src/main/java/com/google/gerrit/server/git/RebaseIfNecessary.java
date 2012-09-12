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

import static com.google.gerrit.server.git.MergeUtil.canCherryPick;
import static com.google.gerrit.server.git.MergeUtil.canFastForward;
import static com.google.gerrit.server.git.MergeUtil.getSubmitter;
import static com.google.gerrit.server.git.MergeUtil.hasMissingDependencies;
import static com.google.gerrit.server.git.MergeUtil.markCleanMerges;
import static com.google.gerrit.server.git.MergeUtil.mergeOneCommit;

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;
import com.google.gerrit.server.changedetail.PathConflictException;
import com.google.gerrit.server.changedetail.RebaseChange;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RebaseIfNecessary extends SubmitStrategy {

  private final RebaseChange rebaseChange;
  private final Map<Change.Id, CodeReviewCommit> newCommits;

  RebaseIfNecessary(final GenericFactory identifiedUserFactory,
      final PersonIdent myIdent, final ReviewDb db, final Repository repo,
      final RevWalk rw, final ObjectInserter inserter, final RevFlag canMergeFlag,
      final Set<RevCommit> alreadyAccepted, final Branch.NameKey destBranch,
      final boolean useContentMerge,
      final RebaseChange.Factory rebaseChangeFactory) {
    super(identifiedUserFactory, myIdent, db, repo, rw, inserter, canMergeFlag,
        alreadyAccepted, destBranch, useContentMerge);
    this.rebaseChange = rebaseChangeFactory.create();
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
        if (canFastForward(mergeSorter, newMergeTip, rw, n)) {
          newMergeTip = n;
          n.statusCode = CommitMergeStatus.CLEAN_MERGE;

        } else {
          try {
            final PatchSet newPatchSet =
                rebaseChange.rebase(repo, rw, inserter, n.patchsetId, n.change,
                    getSubmitter(db, n.patchsetId).getAccountId(), newMergeTip,
                    useContentMerge);
            newMergeTip =
                (CodeReviewCommit) rw.parseCommit(ObjectId.fromString(newPatchSet
                    .getRevision().get()));
            newMergeTip.copyFrom(n);
            newMergeTip.patchsetId = newPatchSet.getId();
            newMergeTip.change = db.changes().get(newPatchSet.getId().getParentKey());
            newMergeTip.statusCode = CommitMergeStatus.CLEAN_REBASE;
            newCommits.put(newPatchSet.getId().getParentKey(), newMergeTip);
            setRefLogIdent(getSubmitter(db, n.patchsetId));
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
          if (rw.isMergedInto(newMergeTip, n)) {
            newMergeTip = n;
          } else {
            newMergeTip =
                mergeOneCommit(db, identifiedUserFactory, myIdent, repo, rw,
                    inserter, useContentMerge, destBranch, newMergeTip, n);
          }
          final PatchSetApproval submitApproval =
              markCleanMerges(db, rw, canMergeFlag, newMergeTip, alreadyAccepted);
          setRefLogIdent(submitApproval);
        } catch (IOException e) {
          throw new MergeException("Cannot merge " + n.name(), e);
        }
      }

      alreadyAccepted.add(newMergeTip);
    }

    return newMergeTip;
  }

  private void sort(final List<CodeReviewCommit> toSort) throws MergeException {
    try {
      final List<CodeReviewCommit> sorted =
          new RebaseSorter(rw, alreadyAccepted, canMergeFlag).sort(toSort);
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
    return !hasMissingDependencies(mergeSorter, toMerge)
        && canCherryPick(mergeSorter, repo, useContentMerge, mergeTip, rw,
            toMerge);
  }
}
