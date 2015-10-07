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
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.RepoContext;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CommitMergeStatus;
import com.google.gerrit.server.git.GroupCollector;
import com.google.gerrit.server.git.MergeConflictException;
import com.google.gerrit.server.git.MergeException;
import com.google.gerrit.server.git.MergeIdenticalTreeException;
import com.google.gerrit.server.git.MergeTip;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.io.IOException;
import java.sql.Timestamp;
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
      Timestamp now = TimeUtil.nowTs();
      try (BatchUpdate u = args.newBatchUpdate(now)) {
        // TODO(dborowitz): This won't work when mergeTip is updated only at the
        // end of the batch.
        if (mergeTip.getCurrentTip() == null) {
          cherryPickUnbornRoot(n, mergeTip);
        } else if (n.getParentCount() == 0) {
          cherryPickRootOntoBranch(n);
        } else if (n.getParentCount() == 1) {
          u.addOp(n.getControl(), new CherryPickOneOp(mergeTip, n));
        } else {
          cherryPickMultipleParents(n, mergeTip, now);
        }
        u.execute();
      } catch (IOException | UpdateException | RestApiException e) {
        throw new MergeException("Cannot merge " + n.name(), e);
      }
    }
    // TODO(dborowitz): When BatchUpdate is hoisted out of CherryPick,
    // SubmitStrategy should probably no longer return MergeTip, instead just
    // mutating a single shared MergeTip passed in from the caller.
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

  private void cherryPickMultipleParents(CodeReviewCommit n, MergeTip mergeTip,
      Timestamp when) throws IOException, MergeException {
    // There are multiple parents, so this is a merge commit. We don't want
    // to cherry-pick the merge as clients can't easily rebase their history
    // with that merge present and replaced by an equivalent merge with a
    // different first parent. So instead behave as though MERGE_IF_NECESSARY
    // was configured.
    if (!args.mergeUtil.hasMissingDependencies(args.mergeSorter, n)) {
      if (args.rw.isMergedInto(mergeTip.getCurrentTip(), n)) {
        mergeTip.moveTipTo(n, n);
      } else {
        PersonIdent myIdent = new PersonIdent(args.serverIdent.get(), when);
        CodeReviewCommit result = args.mergeUtil.mergeOneCommit(myIdent,
            myIdent, args.repo, args.rw, args.inserter,
            args.canMergeFlag, args.destBranch, mergeTip.getCurrentTip(), n);
        mergeTip.moveTipTo(result, n);
      }
      args.mergeUtil.markCleanMerges(args.rw, args.canMergeFlag,
          mergeTip.getCurrentTip(), args.alreadyAccepted);
      setRefLogIdent();
    } else {
      // One or more dependencies were not met. The status was already marked on
      // the commit so we have nothing further to perform at this time.
    }
  }

  private class CherryPickOneOp extends BatchUpdate.Op {
    private final MergeTip mergeTip;
    private final CodeReviewCommit toMerge;

    private PatchSet.Id psId;
    private CodeReviewCommit newCommit;

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
      PatchSet ps = new PatchSet(psId);
      ps.setCreatedOn(ctx.getWhen());
      ps.setUploader(args.caller.getAccountId());
      ps.setRevision(new RevId(newCommit.getId().getName()));

      Change c = toMerge.change();
      ps.setGroups(GroupCollector.getCurrentGroups(args.db, c));
      args.db.patchSets().insert(Collections.singleton(ps));
      insertAncestors(args.db, ps.getId(), newCommit);
      c.setCurrentPatchSet(patchSetInfoFactory.get(newCommit, ps.getId()));
      args.db.changes().update(Collections.singletonList(c));

      List<PatchSetApproval> approvals = Lists.newArrayList();
      for (PatchSetApproval a : args.approvalsUtil.byPatchSet(
          args.db, toMerge.getControl(), toMerge.getPatchsetId())) {
        approvals.add(new PatchSetApproval(ps.getId(), a));
      }
      args.db.patchSetApprovals().insert(approvals);

      newCommit.copyFrom(toMerge);
      newCommit.setStatusCode(CommitMergeStatus.CLEAN_PICK);
      newCommit.setControl(
          args.changeControlFactory.controlFor(toMerge.change(), args.caller));
      mergeTip.moveTipTo(newCommit, newCommit);
      newCommits.put(c.getId(), newCommit);
      setRefLogIdent();
    }
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
