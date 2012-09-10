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

import static com.google.gerrit.server.git.MergeUtil.commit;
import static com.google.gerrit.server.git.MergeUtil.hasMissingDependencies;
import static com.google.gerrit.server.git.MergeUtil.markCleanMerges;
import static com.google.gerrit.server.git.MergeUtil.mergeOneCommit;
import static com.google.gerrit.server.git.MergeUtil.newThreeWayMerger;

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.ApprovalCategory;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetAncestor;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.Merger;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CherryPick extends SubmitStrategy {
  private static final Logger log = LoggerFactory.getLogger(CherryPick.class);

  private static final ApprovalCategory.Id CRVW =
      new ApprovalCategory.Id("CRVW");
  private static final ApprovalCategory.Id VRIF =
      new ApprovalCategory.Id("VRIF");
  private static final FooterKey REVIEWED_ON = new FooterKey("Reviewed-on");
  private static final FooterKey CHANGE_ID = new FooterKey("Change-Id");

  private final PatchSetInfoFactory patchSetInfoFactory;
  private final Provider<String> urlProvider;
  private final ApprovalTypes approvalTypes;
  private final GitReferenceUpdated replication;
  private final Map<Change.Id, CodeReviewCommit> newCommits;

  CherryPick(final GenericFactory identifiedUserFactory,
      final PersonIdent myIdent, final PatchSetInfoFactory patchSetInfoFactory,
      final Provider<String> urlProvider, final ApprovalTypes approvalTypes,
      final GitReferenceUpdated replication, final ReviewDb db,
      final Repository repo, final RevWalk rw, final ObjectInserter inserter,
      final RevFlag canMergeFlag, final Set<RevCommit> alreadyAccepted,
      final Branch.NameKey destBranch, final boolean useContentMerge) {
    super(identifiedUserFactory, myIdent, db, repo, rw, inserter, canMergeFlag,
        alreadyAccepted, destBranch, useContentMerge);

    this.patchSetInfoFactory = patchSetInfoFactory;
    this.urlProvider = urlProvider;
    this.approvalTypes = approvalTypes;
    this.replication = replication;
    this.newCommits = new HashMap<Change.Id, CodeReviewCommit>();
  }

  @Override
  protected CodeReviewCommit _run(final CodeReviewCommit mergeTip,
      final List<CodeReviewCommit> toMerge) throws MergeException {
    CodeReviewCommit newMergeTip = mergeTip;
    while (!toMerge.isEmpty()) {
      final CodeReviewCommit n = toMerge.remove(0);
      final ThreeWayMerger m =
          newThreeWayMerger(repo, inserter, useContentMerge);
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
          m.setBase(n.getParent(0));
          if (m.merge(newMergeTip, n)) {
            newMergeTip = writeCherryPickCommit(m, newMergeTip, n);

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
          if (!hasMissingDependencies(mergeSorter, n)) {
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

  @Override
  public Map<Change.Id, CodeReviewCommit> getNewCommits() {
    return newCommits;
  }

  private CodeReviewCommit writeCherryPickCommit(final Merger m,
      final CodeReviewCommit mergeTip, final CodeReviewCommit n)
      throws IOException, OrmException {
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
    List<PatchSetApproval> approvalList = null;
    try {
      approvalList = db.patchSetApprovals().byPatchSet(n.patchsetId).toList();
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

    final ObjectId id = commit(inserter, mergeCommit);
    final CodeReviewCommit newCommit = (CodeReviewCommit) rw.parseCommit(id);

    n.change.nextPatchSetId();

    final PatchSet ps = new PatchSet(n.change.currPatchSetId());
    ps.setCreatedOn(new Timestamp(System.currentTimeMillis()));
    ps.setUploader(submitAudit.getAccountId());
    ps.setRevision(new RevId(id.getName()));
    insertAncestors(ps.getId(), newCommit);
    db.patchSets().insert(Collections.singleton(ps));

    n.change.setCurrentPatchSet(patchSetInfoFactory.get(newCommit, ps.getId()));
    db.changes().update(Collections.singletonList(n.change));

    if (approvalList != null) {
      for (PatchSetApproval a : approvalList) {
        db.patchSetApprovals().insert(
            Collections.singleton(new PatchSetApproval(ps.getId(), a)));
      }
    }

    final RefUpdate ru = repo.updateRef(ps.getRefName());
    ru.setExpectedOldObjectId(ObjectId.zeroId());
    ru.setNewObjectId(newCommit);
    ru.disableRefLog();
    if (ru.update(rw) != RefUpdate.Result.NEW) {
      throw new IOException(String.format(
          "Failed to create ref %s in %s: %s", ps.getRefName(),
          n.change.getDest().getParentKey().get(), ru.getResult()));
    }
    replication.fire(n.change.getProject(), ru.getName());

    newCommit.copyFrom(n);
    newCommit.statusCode = CommitMergeStatus.CLEAN_PICK;
    newCommits.put(newCommit.patchsetId.getParentKey(), newCommit);
    setRefLogIdent(submitAudit);
    return newCommit;
  }

  private void insertAncestors(PatchSet.Id id, RevCommit src)
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
