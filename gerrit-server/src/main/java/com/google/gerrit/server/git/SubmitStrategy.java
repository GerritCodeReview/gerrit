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

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.index.ChangeIndexer;

import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class that submit strategies must extend. A submit strategy for a
 * certain {@link SubmitType} defines how the submitted commits should be
 * merged.
 */
public abstract class SubmitStrategy {

  private PersonIdent refLogIdent;

  static class Arguments {
    protected final IdentifiedUser.GenericFactory identifiedUserFactory;
    protected final PersonIdent myIdent;
    protected final ReviewDb db;

    protected final Repository repo;
    protected final RevWalk rw;
    protected final ObjectInserter inserter;
    protected final RevFlag canMergeFlag;
    protected final Set<RevCommit> alreadyAccepted;
    protected final Branch.NameKey destBranch;
    protected final MergeUtil mergeUtil;
    protected final ChangeIndexer indexer;
    protected final MergeSorter mergeSorter;

    Arguments(final IdentifiedUser.GenericFactory identifiedUserFactory,
        final PersonIdent myIdent, final ReviewDb db, final Repository repo,
        final RevWalk rw, final ObjectInserter inserter,
        final RevFlag canMergeFlag, final Set<RevCommit> alreadyAccepted,
        final Branch.NameKey destBranch, final MergeUtil mergeUtil,
        final ChangeIndexer indexer) {
      this.identifiedUserFactory = identifiedUserFactory;
      this.myIdent = myIdent;
      this.db = db;

      this.repo = repo;
      this.rw = rw;
      this.inserter = inserter;
      this.canMergeFlag = canMergeFlag;
      this.alreadyAccepted = alreadyAccepted;
      this.destBranch = destBranch;
      this.mergeUtil = mergeUtil;
      this.indexer = indexer;
      this.mergeSorter = new MergeSorter(rw, alreadyAccepted, canMergeFlag);
    }
  }

  protected final Arguments args;

  SubmitStrategy(final Arguments args) {
    this.args = args;
  }

  /**
   * Runs this submit strategy. If possible the provided commits will be merged
   * with this submit strategy.
   *
   * @param mergeTip the mergeTip
   * @param toMerge the list of submitted commits that should be merged using
   *        this submit strategy
   * @return the new mergeTip
   * @throws MergeException
   */
  public final CodeReviewCommit run(final CodeReviewCommit mergeTip,
      final List<CodeReviewCommit> toMerge) throws MergeException {
    refLogIdent = null;
    return _run(mergeTip, toMerge);
  }

  /**
   * Runs this submit strategy. If possible the provided commits will be merged
   * with this submit strategy.
   *
   * @param mergeTip the mergeTip
   * @param toMerge the list of submitted commits that should be merged using
   *        this submit strategy
   * @return the new mergeTip
   * @throws MergeException
   */
  protected abstract CodeReviewCommit _run(CodeReviewCommit mergeTip,
      List<CodeReviewCommit> toMerge) throws MergeException;

  /**
   * Checks whether the given commit can be merged.
   *
   * Subclasses must ensure that invoking this method does neither modify the
   * git repository nor the Gerrit database.
   *
   * @param mergeTip the mergeTip
   * @param toMerge the commit for which it should be checked whether it can be
   *        merged or not
   * @return <code>true</code> if the given commit can be merged, otherwise
   *         <code>false</code>
   * @throws MergeException
   */
  public abstract boolean dryRun(CodeReviewCommit mergeTip,
      CodeReviewCommit toMerge) throws MergeException;

  /**
   * Returns the PersonIdent that should be used for the ref log entries when
   * updating the destination branch. The ref log identity may be set after the
   * {@link #run(CodeReviewCommit, List)} method finished.
   *
   * Do only call this method after the {@link #run(CodeReviewCommit, List)}
   * method has been invoked.
   *
   * @return the ref log identity, may be <code>null</code>
   */
  public final PersonIdent getRefLogIdent() {
    return refLogIdent;
  }

  /**
   * Returns all commits that have been newly created for the changes that are
   * getting merged.
   *
   * By default this method is returning an empty map, but subclasses may
   * overwrite this method to provide newly created commits.
   *
   * Do only call this method after the {@link #run(CodeReviewCommit, List)}
   * method has been invoked.
   *
   * @return new commits created for changes that are getting merged
   */
  public Map<Change.Id, CodeReviewCommit> getNewCommits() {
    return Collections.emptyMap();
  }

  /**
   * Returns whether a merge that failed with
   * {@link RefUpdate.Result#LOCK_FAILURE} should be retried.
   *
   * May be overwritten by subclasses.
   *
   * @return <code>true</code> if a merge that failed with
   *         {@link RefUpdate.Result#LOCK_FAILURE} should be retried, otherwise
   *         <code>false</code>
   */
  public boolean retryOnLockFailure() {
    return true;
  }

  /**
   * Sets the ref log identity if it wasn't set yet.
   *
   * @param submitApproval the approval that submitted the patch set
   */
  protected final void setRefLogIdent(final PatchSetApproval submitApproval) {
    if (refLogIdent == null && submitApproval != null) {
      refLogIdent =
          args.identifiedUserFactory.create(submitApproval.getAccountId())
              .newRefLogIdent();
    }
  }
}
