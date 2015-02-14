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

import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.MergeException;
import com.google.gerrit.server.git.MergeSorter;
import com.google.gerrit.server.git.MergeTip;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.project.ChangeControl;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Base class that submit strategies must extend.
 * <p>
 * A submit strategy for a certain {@link SubmitType} defines how the submitted
 * commits should be merged.
 */
public abstract class SubmitStrategy {
  static class Arguments {
    protected final IdentifiedUser.GenericFactory identifiedUserFactory;
    protected final Provider<PersonIdent> serverIdent;
    protected final ReviewDb db;
    protected final ChangeControl.GenericFactory changeControlFactory;

    protected final Repository repo;
    protected final RevWalk rw;
    protected final ObjectInserter inserter;
    protected final RevFlag canMergeFlag;
    protected final Set<RevCommit> alreadyAccepted;
    protected final Branch.NameKey destBranch;
    protected final ApprovalsUtil approvalsUtil;
    protected final MergeUtil mergeUtil;
    protected final ChangeIndexer indexer;
    protected final MergeSorter mergeSorter;

    private final BatchUpdate.Factory batchUpdateFactory;

    Arguments(IdentifiedUser.GenericFactory identifiedUserFactory,
        Provider<PersonIdent> serverIdent, ReviewDb db,
        BatchUpdate.Factory batchUpdateFactory,
        ChangeControl.GenericFactory changeControlFactory, Repository repo,
        RevWalk rw, ObjectInserter inserter, RevFlag canMergeFlag,
        Set<RevCommit> alreadyAccepted, Branch.NameKey destBranch,
        ApprovalsUtil approvalsUtil, MergeUtil mergeUtil,
        ChangeIndexer indexer) {
      this.identifiedUserFactory = identifiedUserFactory;
      this.serverIdent = serverIdent;
      this.db = db;
      this.batchUpdateFactory = batchUpdateFactory;
      this.changeControlFactory = changeControlFactory;

      this.repo = repo;
      this.rw = rw;
      this.inserter = inserter;
      this.canMergeFlag = canMergeFlag;
      this.alreadyAccepted = alreadyAccepted;
      this.destBranch = destBranch;
      this.approvalsUtil = approvalsUtil;
      this.mergeUtil = mergeUtil;
      this.indexer = indexer;
      this.mergeSorter = new MergeSorter(rw, alreadyAccepted, canMergeFlag);
    }

    BatchUpdate newBatchUpdate(Timestamp when) {
      return batchUpdateFactory.create(db, repo, rw, inserter,
          destBranch.getParentKey(), when);
    }
  }

  protected final Arguments args;

  private PersonIdent refLogIdent;

  SubmitStrategy(Arguments args) {
    this.args = args;
  }

  /**
   * Runs this submit strategy.
   * <p>
   * If possible, the provided commits will be merged with this submit strategy.
   *
   * @param currentTip the mergeTip
   * @param toMerge the list of submitted commits that should be merged using
   *        this submit strategy. Implementations are responsible for ordering
   *        of commits, and should not modify the input in place.
   * @return the new merge tip.
   * @throws MergeException
   */
  public final MergeTip run(final CodeReviewCommit currentTip,
      final Collection<CodeReviewCommit> toMerge) throws MergeException {
    refLogIdent = null;
    return _run(currentTip, toMerge);
  }

  /** @see #run(CodeReviewCommit, Collection) */
  protected abstract MergeTip _run(CodeReviewCommit currentTip,
      Collection<CodeReviewCommit> toMerge) throws MergeException;

  /**
   * Checks whether the given commit can be merged.
   *
   * Implementations must ensure that invoking this method modifies neither the
   * git repository nor the Gerrit database.
   *
   * @param mergeTip the merge tip.
   * @param toMerge the commit that should be checked.
   * @return {@code true} if the given commit can be merged, otherwise
   *         {@code false}
   * @throws MergeException
   */
  public abstract boolean dryRun(CodeReviewCommit mergeTip,
      CodeReviewCommit toMerge) throws MergeException;

  /**
   * Returns the identity that should be used for reflog entries when updating
   * the destination branch.
   * <p>
   * The reflog identity may only be set during {@link #run(CodeReviewCommit,
   * Collection)}, and this method is invalid to call beforehand.
   *
   * @return the ref log identity, which may be {@code null}.
   */
  public final PersonIdent getRefLogIdent() {
    return refLogIdent;
  }

  /**
   * Returns all commits that have been newly created for the changes that are
   * getting merged.
   * <p>
   * By default this method returns an empty map, but subclasses may override
   * this method to provide any newly created commits.
   *
   * This method may only be called after {@link #run(CodeReviewCommit,
   * Collection)}.
   *
   * @return new commits created for changes that were merged.
   */
  public Map<Change.Id, CodeReviewCommit> getNewCommits() {
    return Collections.emptyMap();
  }

  /**
   * Returns whether a merge that failed with {@link Result#LOCK_FAILURE} should
   * be retried.
   * <p>
   * May be overridden by subclasses.
   *
   * @return {@code true} if a merge that failed with
   *         {@link Result#LOCK_FAILURE} should be retried, otherwise
   *         {@code false}
   */
  public boolean retryOnLockFailure() {
    return true;
  }

  /**
   * Set the ref log identity if it wasn't set yet.
   *
   * @param submitApproval the approval that submitted the patch set
   */
  protected final void setRefLogIdent(PatchSetApproval submitApproval) {
    if (refLogIdent == null && submitApproval != null) {
      refLogIdent = args.identifiedUserFactory.create(
          submitApproval.getAccountId()) .newRefLogIdent();
    }
  }
}
