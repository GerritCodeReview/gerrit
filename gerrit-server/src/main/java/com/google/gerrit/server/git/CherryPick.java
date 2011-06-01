// Copyright (C) 2008 The Android Open Source Project
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

import com.google.gerrit.common.ChangeHookRunner;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.mail.MergeFailSender;
import com.google.gerrit.server.mail.MergedSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.Merger;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.FooterLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CherryPick extends MergeOp {

  private static final ApprovalCategory.Id CRVW =
      new ApprovalCategory.Id("CRVW");
  private static final ApprovalCategory.Id VRIF =
      new ApprovalCategory.Id("VRIF");
  private static final FooterKey REVIEWED_ON = new FooterKey("Reviewed-on");
  private static final FooterKey CHANGE_ID = new FooterKey("Change-Id");

  private final Provider<String> urlProvider;

  private static final Logger log = LoggerFactory.getLogger(CherryPick.class);

  CherryPick(final GitRepositoryManager grm, final SchemaFactory<ReviewDb> sf,
      final ProjectCache pc, final FunctionState.Factory fs,
      final ReplicationQueue rq, final MergedSender.Factory msf,
      final MergeFailSender.Factory mfsf, final Provider<String> cwu,
      final ApprovalTypes approvalTypes, final PatchSetInfoFactory psif,
      final IdentifiedUser.GenericFactory iuf, final PersonIdent myIdent,
      final MergeQueue mergeQueue, final Branch.NameKey branch,
      final ChangeHookRunner hooks, final AccountCache accountCache,
      final CreateCodeReviewNotes.Factory crnf, final Project destProject) {

    super(grm, sf, pc, fs, rq, msf, mfsf, approvalTypes, psif, iuf, myIdent,
        mergeQueue, branch, hooks, accountCache, crnf, destProject);
    this.urlProvider = cwu;
  }

  public void runMerge() throws MergeException {
    while (!toMerge.isEmpty()) {
      final CodeReviewCommit n = toMerge.remove(0);
      final ThreeWayMerger m;

      if (destProject.isUseContentMerge()) {
        // Settings for this project allow us to try and
        // automatically resolve conflicts within files if needed.
        // Use ResolveMerge and instruct to operate in core.
        m = MergeStrategy.RESOLVE.newMerger(db, true);
      } else {
        // No auto conflict resolving allowed. If any of the
        // affected files was modified, merge will fail.
        m = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
      }

      try {
        if (mergeTip == null) {
          // The branch is unborn. Take a fast-forward resolution to
          // create the branch.
          //
          mergeTip = n;
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
          m.setBase(n.getParent(0));
          if (m.merge(mergeTip, n)) {
            writeCherryPickCommit(m, n);

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
          if (hasDependenciesMet(n)) {
            if (rw.isMergedInto(mergeTip, n)) {
              mergeTip = n;
            } else {
              mergeOneCommit(n);
            }
            markCleanMerges();

          } else {
            // One or more dependencies were not met. The status was
            // already marked on the commit so we have nothing further
            // to perform at this time.
            //
          }
        }

      } catch (IOException e) {
        throw new MergeException("Cannot merge " + n.name(), e);
      }
    }
  }

  private boolean hasDependenciesMet(final CodeReviewCommit n)
      throws IOException {
    // Oddly we can determine this by running the merge sorter and
    // look for the one commit to come out as a result. This works
    // as the merge sorter checks the dependency chain as part of
    // its logic trying to find a minimal merge path.
    //
    return new MergeSorter(rw, alreadyAccepted, CAN_MERGE).sort(
        Collections.singleton(n)).contains(n);
  }

  private void writeCherryPickCommit(final Merger m, final CodeReviewCommit n)
      throws IOException {
    rw.parseBody(n);

    final List<FooterLine> footers = n.getFooterLines();
    final StringBuilder msgbuf = new StringBuilder();
    msgbuf.append(n.getFullMessage());

    if (msgbuf.length() == 0) {
      // WTF, an empty commit message?
      msgbuf.append("<no commit message provided>");
    }
    if (msgbuf.charAt(msgbuf.length() - 1) != '\n') {
      // Missing a trailing LF? Correct it (perhaps the editor was broken).
      msgbuf.append('\n');
    }
    if (footers.isEmpty()) {
      // Doesn't end in a "Signed-off-by: ..." style line? Add another line
      // break to start a new paragraph for the reviewed-by tag lines.
      //
      msgbuf.append('\n');
    }

    if (!contains(footers, CHANGE_ID, n.change.getKey().get())) {
      msgbuf.append(CHANGE_ID.getName());
      msgbuf.append(": ");
      msgbuf.append(n.change.getKey().get());
      msgbuf.append('\n');
    }

    final String siteUrl = urlProvider.get();
    if (siteUrl != null) {
      final String url = siteUrl + n.patchsetId.getParentKey().get();
      if (!contains(footers, REVIEWED_ON, url)) {
        msgbuf.append(REVIEWED_ON.getName());
        msgbuf.append(": ");
        msgbuf.append(url);
        msgbuf.append('\n');
      }
    }

    PatchSetApproval submitAudit = null;
    try {
      final List<PatchSetApproval> approvalList =
          schema.patchSetApprovals().byPatchSet(n.patchsetId).toList();
      Collections.sort(approvalList, new Comparator<PatchSetApproval>() {
        public int compare(final PatchSetApproval a, final PatchSetApproval b) {
          return a.getGranted().compareTo(b.getGranted());
        }
      });

      for (final PatchSetApproval a : approvalList) {
        if (a.getValue() <= 0) {
          // Negative votes aren't counted.
          continue;
        }

        if (ApprovalCategory.SUBMIT.equals(a.getCategoryId())) {
          // Submit is treated specially, below (becomes committer)
          //
          if (submitAudit == null
              || a.getGranted().compareTo(submitAudit.getGranted()) > 0) {
            submitAudit = a;
          }
          continue;
        }

        final Account acc =
            identifiedUserFactory.create(a.getAccountId()).getAccount();
        final StringBuilder identbuf = new StringBuilder();
        if (acc.getFullName() != null && acc.getFullName().length() > 0) {
          if (identbuf.length() > 0) {
            identbuf.append(' ');
          }
          identbuf.append(acc.getFullName());
        }
        if (acc.getPreferredEmail() != null
            && acc.getPreferredEmail().length() > 0) {
          if (isSignedOffBy(footers, acc.getPreferredEmail())) {
            continue;
          }
          if (identbuf.length() > 0) {
            identbuf.append(' ');
          }
          identbuf.append('<');
          identbuf.append(acc.getPreferredEmail());
          identbuf.append('>');
        }
        if (identbuf.length() == 0) {
          // Nothing reasonable to describe them by? Ignore them.
          continue;
        }

        final String tag;
        if (CRVW.equals(a.getCategoryId())) {
          tag = "Reviewed-by";
        } else if (VRIF.equals(a.getCategoryId())) {
          tag = "Tested-by";
        } else {
          final ApprovalType at = approvalTypes.byId(a.getCategoryId());
          if (at == null) {
            // A deprecated/deleted approval type, ignore it.
            continue;
          }
          tag = at.getCategory().getName().replace(' ', '-');
        }

        if (!contains(footers, new FooterKey(tag), identbuf.toString())) {
          msgbuf.append(tag);
          msgbuf.append(": ");
          msgbuf.append(identbuf);
          msgbuf.append('\n');
        }
      }
    } catch (OrmException e) {
      log.error("Can't read approval records for " + n.patchsetId, e);
    }

    final CommitBuilder mergeCommit = new CommitBuilder();
    mergeCommit.setTreeId(m.getResultTreeId());
    mergeCommit.setParentId(mergeTip);
    mergeCommit.setAuthor(n.getAuthorIdent());
    mergeCommit.setCommitter(toCommitterIdent(submitAudit));
    mergeCommit.setMessage(msgbuf.toString());

    final ObjectId id = commit(m, mergeCommit);
    final CodeReviewCommit newCommit = (CodeReviewCommit) rw.parseCommit(id);
    newCommit.copyFrom(n);
    newCommit.statusCode = CommitMergeStatus.CLEAN_PICK;
    commits.put(newCommit.patchsetId.getParentKey(), newCommit);

    mergeTip = newCommit;
    setRefLogIdent(submitAudit);
  }

  private boolean contains(List<FooterLine> footers, FooterKey key, String val) {
    for (final FooterLine line : footers) {
      if (line.matches(key) && val.equals(line.getValue())) {
        return true;
      }
    }
    return false;
  }

  private boolean isSignedOffBy(List<FooterLine> footers, String email) {
    for (final FooterLine line : footers) {
      if (line.matches(FooterKey.SIGNED_OFF_BY)
          && email.equals(line.getEmailAddress())) {
        return true;
      }
    }
    return false;
  }

  private PersonIdent toCommitterIdent(final PatchSetApproval audit) {
    if (audit != null) {
      return identifiedUserFactory.create(audit.getAccountId())
          .newCommitterIdent(audit.getGranted(), myIdent.getTimeZone());
    }
    return myIdent;
  }

}
