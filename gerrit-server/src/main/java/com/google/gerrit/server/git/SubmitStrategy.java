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

import static com.google.gerrit.server.git.MergeUtil.getSubmitter;

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;

import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
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
  protected final IdentifiedUser.GenericFactory identifiedUserFactory;
  protected final PersonIdent myIdent;
  protected final ReviewDb db;

  protected final Repository repo;
  protected final RevWalk rw;
  protected final ObjectInserter inserter;
  protected final RevFlag CAN_MERGE;
  protected final Set<RevCommit> alreadyAccepted;
  protected final Branch.NameKey destBranch;
  protected final boolean useContentMerge;

  private PersonIdent refLogIdent;

  SubmitStrategy(final IdentifiedUser.GenericFactory identifiedUserFactory,
      final PersonIdent myIdent, final ReviewDb db, final Repository repo,
      final RevWalk rw, final ObjectInserter inserter, final RevFlag CAN_MERGE,
      final Set<RevCommit> alreadyAccepted, final Branch.NameKey destBranch,
      final boolean useContentMerge) {
    this.identifiedUserFactory = identifiedUserFactory;
    this.myIdent = myIdent;
    this.db = db;

    this.repo = repo;
    this.rw = rw;
    this.inserter = inserter;
    this.CAN_MERGE = CAN_MERGE;
    this.alreadyAccepted = alreadyAccepted;
    this.destBranch = destBranch;
    this.useContentMerge = useContentMerge;
  }

  /**
   * Runs this submit strategy. If possible the provided commits will be merged
   * with this submit strategy.
   *
   * @param mergeTip the mergeTip
   * @param toMerge the list of submitted commits that should be merged with
   *        this submit strategy
   * @return the new mergeTip
   * @throws MergeException
   */
  public final CodeReviewCommit run(final CodeReviewCommit mergeTip, final List<CodeReviewCommit> toMerge)
      throws MergeException {
    refLogIdent = null;
    return _run(mergeTip, toMerge);
  }

  /**
   * Runs this submit strategy. If possible the provided commits will be merged
   * with this submit strategy.
   *
   * @param mergeTip the mergeTip
   * @param toMerge the list of submitted commits that should be merged with
   *        this submit strategy
   * @return the new mergeTip
   * @throws MergeException
   */
  protected abstract CodeReviewCommit _run(CodeReviewCommit mergeTip,
      List<CodeReviewCommit> toMerge) throws MergeException;

  /**
   * Returns the PersonIdent that should be used for the ref log entries when
   * updating the destination branch. The ref log identity may be set by running
   * the submit strategy.
   *
   * @return the ref log identity
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
   * @return new commits created for changes that are getting merged
   */
  public Map<Change.Id, CodeReviewCommit> getNewCommits() {
    return Collections.emptyMap();
  }

  protected final void markCleanMerges(final CodeReviewCommit mergeTip)
      throws MergeException {
    if (mergeTip == null) {
      // If mergeTip is null here, branchTip was null, indicating a new branch
      // at the start of the merge process. We also elected to merge nothing,
      // probably due to missing dependencies. Nothing was cleanly merged.
      //
      return;
    }

    try {
      rw.reset();
      rw.sort(RevSort.TOPO);
      rw.sort(RevSort.REVERSE, true);
      rw.markStart(mergeTip);
      for (RevCommit c : alreadyAccepted) {
        rw.markUninteresting(c);
      }

      CodeReviewCommit c;
      while ((c = (CodeReviewCommit) rw.next()) != null) {
        if (c.patchsetId != null) {
          c.statusCode = CommitMergeStatus.CLEAN_MERGE;
          if (refLogIdent == null) {
            setRefLogIdent(getSubmitter(db, c.patchsetId));
          }
        }
      }
    } catch (IOException e) {
      throw new MergeException("Cannot mark clean merges", e);
    }
  }

  protected final void setRefLogIdent(final PatchSetApproval submitAudit) {
    if (submitAudit != null) {
      refLogIdent =
          identifiedUserFactory.create(submitAudit.getAccountId())
              .newRefLogIdent();
    }
  }
}
