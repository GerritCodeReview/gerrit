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
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetAncestor;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeOp;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CommitMergeStatus;
import com.google.gerrit.server.git.MergeConflictException;
import com.google.gerrit.server.git.MergeException;
import com.google.gerrit.server.git.MergeIdenticalTreeException;
import com.google.gerrit.server.git.MergeTip;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.io.IOException;
import java.util.ArrayList;
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
      Collection<CodeReviewCommit> toMerge) throws MergeException {
    MergeTip mergeTip = new MergeTip(branchTip, toMerge);
    List<CodeReviewCommit> sorted = CodeReviewCommit.ORDER.sortedCopy(toMerge);
    while (!sorted.isEmpty()) {
      CodeReviewCommit n = sorted.remove(0);
      try (BatchUpdate u = args.newBatchUpdate(TimeUtil.nowTs())) {
        if (mergeTip.getCurrentTip() == null) {
          cherryPickUnbornRoot(n, mergeTip);
        } else if (n.getParentCount() == 0) {
          cherryPickRootOntoBranch(n);
        } else if (n.getParentCount() == 1) {
          cherryPickOne(u, n, mergeTip);
        } else {
          cherryPickMultipleParents(u, n, mergeTip);
        }
        u.execute();
      } catch (IOException | UpdateException | RestApiException e) {
        throw new MergeException("Cannot merge " + n.name(), e);
      }
    }
    return mergeTip;
  }

  private void cherryPickUnbornRoot(CodeReviewCommit n, MergeTip mergeTip) {
    // The branch is unborn. Take fast-forward resolution to create the branch.
    mergeTip.moveTipTo(n, n);
    n.setStatusCode(CommitMergeStatus.CLEAN_MERGE);
  }

  private void cherryPickRootOntoBranch(CodeReviewCommit n) {
    // Refuse to merge a root commit into an existing branch, we cannot obtain a
    // delta for the cherry-pick to apply.
    n.setStatusCode(CommitMergeStatus.CANNOT_CHERRY_PICK_ROOT);
  }

  private void cherryPickOne(BatchUpdate u, CodeReviewCommit n,
      MergeTip mergeTip) throws IOException {
    // If there is only one parent, a cherry-pick can be done by taking the
    // delta relative to that one parent and redoing that on the current merge
    // tip.
    //
    // Keep going in the case of a single merge failure; the goal is to
    // cherry-pick as many commits as possible.
    try {
      CodeReviewCommit merge =
          writeCherryPickCommit(u, mergeTip.getCurrentTip(), n);
      mergeTip.moveTipTo(merge, merge);
    } catch (MergeConflictException mce) {
      n.setStatusCode(CommitMergeStatus.PATH_CONFLICT);
    } catch (MergeIdenticalTreeException mie) {
      n.setStatusCode(CommitMergeStatus.ALREADY_MERGED);
    }
  }

  private void cherryPickMultipleParents(BatchUpdate u, CodeReviewCommit n,
      MergeTip mergeTip) throws IOException, MergeException {
    // There are multiple parents, so this is a merge commit. We don't want
    // to cherry-pick the merge as clients can't easily rebase their history
    // with that merge present and replaced by an equivalent merge with a
    // different first parent. So instead behave as though MERGE_IF_NECESSARY
    // was configured.
    if (!args.mergeUtil.hasMissingDependencies(args.mergeSorter, n)) {
      if (args.rw.isMergedInto(mergeTip.getCurrentTip(), n)) {
        mergeTip.moveTipTo(n, n);
      } else {
        CodeReviewCommit result = args.mergeUtil.mergeOneCommit(
            newServerIdent(u), args.repo, args.rw, args.inserter,
            args.canMergeFlag, args.destBranch, mergeTip.getCurrentTip(), n);
        mergeTip.moveTipTo(result, n);
      }
      PatchSetApproval submitApproval = args.mergeUtil.markCleanMerges(args.rw,
          args.canMergeFlag, mergeTip.getCurrentTip(), args.alreadyAccepted);
      setRefLogIdent(submitApproval);
    } else {
      // One or more dependencies were not met. The status was already marked on
      // the commit so we have nothing further to perform at this time.
    }
  }

  private PersonIdent newServerIdent(BatchUpdate u) {
    return new PersonIdent(args.serverIdent.get(), u.getWhen());
  }

  private CodeReviewCommit writeCherryPickCommit(BatchUpdate u,
      CodeReviewCommit mergeTip, final CodeReviewCommit n) throws IOException,
      MergeConflictException, MergeIdenticalTreeException {
    // TODO(dborowitz): Add a repo-modifying phase to BatchUpdate, preferably
    // reading in a transaction.
    args.rw.parseBody(n);

    final PatchSet.Id id =
        ChangeUtil.nextPatchSetId(args.repo, n.change().currentPatchSetId());
    final PatchSetApproval submitAudit = args.mergeUtil.getSubmitter(n);

    final IdentifiedUser cherryPickUser;
    PersonIdent serverNow = newServerIdent(u);
    PersonIdent cherryPickCommitterIdent;
    if (submitAudit != null) {
      cherryPickUser =
          args.identifiedUserFactory.create(submitAudit.getAccountId());
      cherryPickCommitterIdent = cherryPickUser.newCommitterIdent(
          serverNow.getWhen(), serverNow.getTimeZone());
    } else {
      cherryPickUser = args.identifiedUserFactory.create(n.change().getOwner());
      cherryPickCommitterIdent = serverNow;
    }

    String cherryPickCmtMsg = args.mergeUtil.createCherryPickCommitMessage(n);

    // TODO(dborowitz): Still flushes the commit immediately; remove flush when
    // we no longer pass in inserter.
    final CodeReviewCommit newCommit =
        (CodeReviewCommit) args.mergeUtil.createCherryPickFromCommit(args.repo,
            args.inserter, mergeTip, n, cherryPickCommitterIdent,
            cherryPickCmtMsg, args.rw);
    newCommits.put(id.getParentKey(), newCommit);

    u.addRefUpdate(
        new ReceiveCommand(ObjectId.zeroId(), newCommit, id.toRefName()));

    u.addChangeOp(new ChangeOp(n.getControl()) {
      @Override
      public void call(ReviewDb db, ChangeUpdate update) throws Exception {
        PatchSet ps = new PatchSet(id);
        ps.setCreatedOn(TimeUtil.nowTs());
        ps.setUploader(cherryPickUser.getAccountId());
        ps.setRevision(new RevId(newCommit.getId().getName()));

        insertAncestors(args.db, ps.getId(), newCommit);
        args.db.patchSets().insert(Collections.singleton(ps));
        n.change()
            .setCurrentPatchSet(patchSetInfoFactory.get(newCommit, ps.getId()));
        args.db.changes().update(Collections.singletonList(n.change()));

        List<PatchSetApproval> approvals = Lists.newArrayList();
        for (PatchSetApproval a : args.approvalsUtil.byPatchSet(
            args.db, n.getControl(), n.getPatchsetId())) {
          approvals.add(new PatchSetApproval(ps.getId(), a));
        }
        args.db.patchSetApprovals().insert(approvals);

        newCommit.copyFrom(n);
        newCommit.setStatusCode(CommitMergeStatus.CLEAN_PICK);
        newCommit.setControl(
            args.changeControlFactory.controlFor(n.change(), cherryPickUser));
        setRefLogIdent(submitAudit);
      }
    });
    return newCommit;
  }

  private static void insertAncestors(ReviewDb db, PatchSet.Id id,
      RevCommit src) throws OrmException {
    int cnt = src.getParentCount();
    List<PatchSetAncestor> toInsert = new ArrayList<>(cnt);
    for (int p = 0; p < cnt; p++) {
      PatchSetAncestor a;

      a = new PatchSetAncestor(new PatchSetAncestor.Id(id, p + 1));
      a.setAncestorRevision(new RevId(src.getParent(p).getId().name()));
      toInsert.add(a);
    }
    db.patchSetAncestors().insert(toInsert);
  }

  @Override
  public Map<Change.Id, CodeReviewCommit> getNewCommits() {
    return newCommits;
  }

  @Override
  public boolean dryRun(CodeReviewCommit mergeTip, CodeReviewCommit toMerge)
      throws MergeException {
    return args.mergeUtil.canCherryPick(args.mergeSorter, args.repo,
        mergeTip, args.rw, toMerge);
  }
}
