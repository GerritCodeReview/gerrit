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
import com.google.gerrit.reviewdb.client.PatchSetAncestor;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CherryPick extends SubmitStrategy {
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final GitReferenceUpdated gitRefUpdated;
  private final Map<Change.Id, CodeReviewCommit> newCommits;

  CherryPick(final SubmitStrategy.Arguments args,
      final PatchSetInfoFactory patchSetInfoFactory,
      final GitReferenceUpdated gitRefUpdated) {
    super(args);

    this.patchSetInfoFactory = patchSetInfoFactory;
    this.gitRefUpdated = gitRefUpdated;
    this.newCommits = new HashMap<Change.Id, CodeReviewCommit>();
  }

  @Override
  protected CodeReviewCommit _run(final CodeReviewCommit mergeTip,
      final List<CodeReviewCommit> toMerge) throws MergeException {
    CodeReviewCommit newMergeTip = mergeTip;
    while (!toMerge.isEmpty()) {
      final CodeReviewCommit n = toMerge.remove(0);

      try {
        if (newMergeTip == null) {
          // The branch is unborn. Take a fast-forward resolution to
          // create the branch.
          //
          newMergeTip = n;
          n.statusCode = CommitMergeStatus.CLEAN_MERGE;

        } else if (n.getParentCount() == 0) {
          // Refuse to merge a root commit into an existing branch,
          // we cannot obtain a delta for the cherry-pick to apply.
          //
          n.statusCode = CommitMergeStatus.CANNOT_CHERRY_PICK_ROOT;

        } else if (n.getParentCount() == 1) {
          // If there is only one parent, a cherry-pick can be done by
          // taking the delta relative to that one parent and redoing
          // that on the current merge tip.
          //

          newMergeTip = writeCherryPickCommit(mergeTip, n);

          if (newMergeTip != null) {
            newCommits.put(newMergeTip.patchsetId.getParentKey(), newMergeTip);
          } else {
            n.statusCode = CommitMergeStatus.PATH_CONFLICT;
          }

        } else {
          // There are multiple parents, so this is a merge commit. We
          // don't want to cherry-pick the merge as clients can't easily
          // rebase their history with that merge present and replaced
          // by an equivalent merge with a different first parent. So
          // instead behave as though MERGE_IF_NECESSARY was configured.
          //
          if (!args.mergeUtil.hasMissingDependencies(args.mergeSorter, n)) {
            if (args.rw.isMergedInto(newMergeTip, n)) {
              newMergeTip = n;
            } else {
              newMergeTip =
                  args.mergeUtil.mergeOneCommit(args.myIdent, args.repo,
                      args.rw, args.inserter, args.canMergeFlag,
                      args.destBranch, newMergeTip, n);
           }
            final PatchSetApproval submitApproval =
                args.mergeUtil.markCleanMerges(args.rw, args.canMergeFlag,
                    newMergeTip, args.alreadyAccepted);
            setRefLogIdent(submitApproval);

          } else {
            // One or more dependencies were not met. The status was
            // already marked on the commit so we have nothing further
            // to perform at this time.
            //
          }
        }

      } catch (IOException e) {
        throw new MergeException("Cannot merge " + n.name(), e);
      } catch (OrmException e) {
        throw new MergeException("Cannot merge " + n.name(), e);
      }
    }
    return newMergeTip;
  }

  private CodeReviewCommit writeCherryPickCommit(final CodeReviewCommit mergeTip, final CodeReviewCommit n)
      throws IOException, OrmException {

    args.rw.parseBody(n);

    final PatchSetApproval submitAudit =
        args.mergeUtil.getSubmitter(n.change.currentPatchSetId());

    PersonIdent cherryPickCommitterIdent = null;
    if (submitAudit != null) {
      cherryPickCommitterIdent =
          args.identifiedUserFactory.create(submitAudit.getAccountId())
              .newCommitterIdent(submitAudit.getGranted(),
                  args.myIdent.getTimeZone());
    } else {
      cherryPickCommitterIdent = args.myIdent;
    }

    final String cherryPickCmtMsg = args.mergeUtil.createCherryPickCommitMessage(n);

    final CodeReviewCommit newCommit =
        (CodeReviewCommit) args.mergeUtil.createCherryPickFromCommit(args.repo,
            args.inserter, mergeTip, n, cherryPickCommitterIdent,
            cherryPickCmtMsg, args.rw);

    if (newCommit == null) {
        return null;
    }

    PatchSet.Id id =
        ChangeUtil.nextPatchSetId(args.repo, n.change.currentPatchSetId());
    final PatchSet ps = new PatchSet(id);
    ps.setCreatedOn(new Timestamp(System.currentTimeMillis()));
    ps.setUploader(submitAudit.getAccountId());
    ps.setRevision(new RevId(newCommit.getId().getName()));
    insertAncestors(args.db, ps.getId(), newCommit);
    args.db.patchSets().insert(Collections.singleton(ps));

    n.change.setCurrentPatchSet(patchSetInfoFactory.get(newCommit, ps.getId()));
    args.db.changes().update(Collections.singletonList(n.change));

    final List<PatchSetApproval> approvals = Lists.newArrayList();
    for (PatchSetApproval a : args.mergeUtil.getApprovalsForCommit(n)) {
      approvals.add(new PatchSetApproval(ps.getId(), a));
    }
    args.db.patchSetApprovals().insert(approvals);

    final RefUpdate ru = args.repo.updateRef(ps.getRefName());
    ru.setExpectedOldObjectId(ObjectId.zeroId());
    ru.setNewObjectId(newCommit);
    ru.disableRefLog();
    if (ru.update(args.rw) != RefUpdate.Result.NEW) {
      throw new IOException(String.format("Failed to create ref %s in %s: %s",
          ps.getRefName(), n.change.getDest().getParentKey().get(),
          ru.getResult()));
    }

    gitRefUpdated.fire(n.change.getProject(), ru);

    newCommit.copyFrom(n);
    newCommit.statusCode = CommitMergeStatus.CLEAN_PICK;
    newCommits.put(newCommit.patchsetId.getParentKey(), newCommit);
    setRefLogIdent(submitAudit);
    return newCommit;
  }

  private static void insertAncestors(ReviewDb db, PatchSet.Id id, RevCommit src)
      throws OrmException {
    final int cnt = src.getParentCount();
    List<PatchSetAncestor> toInsert = new ArrayList<PatchSetAncestor>(cnt);
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
  public boolean dryRun(final CodeReviewCommit mergeTip,
      final CodeReviewCommit toMerge) throws MergeException {
    return args.mergeUtil.canCherryPick(args.mergeSorter, args.repo,
        mergeTip, args.rw, toMerge);
  }
}
