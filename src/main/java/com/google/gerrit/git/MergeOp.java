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

package com.google.gerrit.git;

import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.data.ApprovalTypes;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.Branch;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeMessage;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.PatchSetApproval;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.Nullable;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.mail.MergeFailSender;
import com.google.gerrit.server.mail.MergedSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.workflow.CategoryFunction;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.gwtorm.client.Transaction;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spearce.jgit.errors.IncorrectObjectTypeException;
import org.spearce.jgit.errors.MissingObjectException;
import org.spearce.jgit.errors.RepositoryNotFoundException;
import org.spearce.jgit.lib.AnyObjectId;
import org.spearce.jgit.lib.Commit;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.lib.RefUpdate;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.merge.MergeStrategy;
import org.spearce.jgit.merge.Merger;
import org.spearce.jgit.merge.ThreeWayMerger;
import org.spearce.jgit.revwalk.FooterKey;
import org.spearce.jgit.revwalk.FooterLine;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevSort;
import org.spearce.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges changes in submission order into a single branch.
 * <p>
 * Branches are reduced to the minimum number of heads needed to merge
 * everything. This allows commits to be entered into the queue in any order
 * (such as ancestors before descendants) and only the most recent commit on any
 * line of development will be merged. All unmerged commits along a line of
 * development must be in the submission queue in order to merge the tip of that
 * line.
 * <p>
 * Conflicts are handled by discarding the entire line of development and
 * marking it as conflicting, even if an earlier commit along that same line can
 * be merged cleanly.
 */
public class MergeOp {
  public interface Factory {
    MergeOp create(Branch.NameKey branch);
  }

  private static final Logger log = LoggerFactory.getLogger(MergeOp.class);
  private static final String R_HEADS_MASTER =
      Constants.R_HEADS + Constants.MASTER;
  private static final ApprovalCategory.Id CRVW =
      new ApprovalCategory.Id("CRVW");
  private static final ApprovalCategory.Id VRIF =
      new ApprovalCategory.Id("VRIF");
  private static final FooterKey REVIEWED_ON = new FooterKey("Reviewed-on");
  private static final FooterKey CHANGE_ID = new FooterKey("Change-Id");

  private final GitRepositoryManager repoManager;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final ProjectCache projectCache;
  private final FunctionState.Factory functionState;
  private final ReplicationQueue replication;
  private final MergedSender.Factory mergedSenderFactory;
  private final MergeFailSender.Factory mergeFailSenderFactory;
  private final Provider<String> urlProvider;
  private final ApprovalTypes approvalTypes;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;

  private final PersonIdent myIdent;
  private final Branch.NameKey destBranch;
  private Project destProject;
  private final List<CodeReviewCommit> toMerge;
  private List<Change> submitted;
  private final Map<Change.Id, CommitMergeStatus> status;
  private final Map<Change.Id, CodeReviewCommit> newCommits;
  private ReviewDb schema;
  private Repository db;
  private RevWalk rw;
  private CodeReviewCommit branchTip;
  private CodeReviewCommit mergeTip;
  private RefUpdate branchUpdate;

  @Inject
  MergeOp(final GitRepositoryManager grm, final SchemaFactory<ReviewDb> sf,
      final ProjectCache pc, final FunctionState.Factory fs,
      final ReplicationQueue rq, final MergedSender.Factory msf,
      final MergeFailSender.Factory mfsf,
      @CanonicalWebUrl @Nullable final Provider<String> cwu,
      final ApprovalTypes approvalTypes, final PatchSetInfoFactory psif,
      final IdentifiedUser.GenericFactory iuf,
      @GerritPersonIdent final PersonIdent myIdent,
      @Assisted final Branch.NameKey branch) {
    repoManager = grm;
    schemaFactory = sf;
    functionState = fs;
    projectCache = pc;
    replication = rq;
    mergedSenderFactory = msf;
    mergeFailSenderFactory = mfsf;
    urlProvider = cwu;
    this.approvalTypes = approvalTypes;
    patchSetInfoFactory = psif;
    identifiedUserFactory = iuf;

    this.myIdent = myIdent;
    destBranch = branch;
    toMerge = new ArrayList<CodeReviewCommit>();
    status = new HashMap<Change.Id, CommitMergeStatus>();
    newCommits = new HashMap<Change.Id, CodeReviewCommit>();
  }

  public void merge() throws MergeException {
    final ProjectState pe = projectCache.get(destBranch.getParentKey());
    if (pe == null) {
      throw new MergeException("No such project: " + destBranch.getParentKey());
    }
    destProject = pe.getProject();

    try {
      schema = schemaFactory.open();
    } catch (OrmException e) {
      throw new MergeException("Cannot open database", e);
    }
    try {
      mergeImpl();
    } finally {
      if (db != null) {
        db.close();
      }
      schema.close();
      schema = null;
    }
  }

  private void mergeImpl() throws MergeException {
    openRepository();
    openBranch();
    listPendingSubmits();
    validateChangeList();
    mergeTip = branchTip;
    switch (destProject.getSubmitType()) {
      case CHERRY_PICK:
        cherryPickChanges();
        break;

      case FAST_FORWARD_ONLY:
      case MERGE_ALWAYS:
      case MERGE_IF_NECESSARY:
      default:
        reduceToMinimalMerge();
        mergeTopics();
        markCleanMerges();
        break;
    }
    updateBranch();
    updateChangeStatus();
  }

  private void openRepository() throws MergeException {
    final String name = destBranch.getParentKey().get();
    try {
      db = repoManager.openRepository(name);
    } catch (RepositoryNotFoundException notGit) {
      final String m = "Repository \"" + name + "\" unknown.";
      throw new MergeException(m, notGit);
    }

    rw = new RevWalk(db) {
      @Override
      protected RevCommit createCommit(final AnyObjectId id) {
        return new CodeReviewCommit(id);
      }
    };
    rw.sort(RevSort.TOPO);
    rw.sort(RevSort.COMMIT_TIME_DESC, true);
  }

  private void openBranch() throws MergeException {
    try {
      branchUpdate = db.updateRef(destBranch.get());
      if (branchUpdate.getOldObjectId() != null) {
        branchTip =
            (CodeReviewCommit) rw.parseCommit(branchUpdate.getOldObjectId());
      } else {
        branchTip = null;
      }
    } catch (IOException e) {
      throw new MergeException("Cannot open branch", e);
    }
  }

  private void listPendingSubmits() throws MergeException {
    try {
      submitted = schema.changes().submitted(destBranch).toList();
    } catch (OrmException e) {
      throw new MergeException("Cannot query the database", e);
    }
  }

  private void validateChangeList() throws MergeException {
    final Set<ObjectId> tips = new HashSet<ObjectId>();
    for (final Ref r : db.getAllRefs().values()) {
      tips.add(r.getObjectId());
    }

    int commitOrder = 0;
    for (final Change chg : submitted) {
      if (chg.currentPatchSetId() == null) {
        status.put(chg.getId(), CommitMergeStatus.NO_PATCH_SET);
        continue;
      }

      final PatchSet ps;
      try {
        ps = schema.patchSets().get(chg.currentPatchSetId());
      } catch (OrmException e) {
        throw new MergeException("Cannot query the database", e);
      }
      if (ps == null || ps.getRevision() == null
          || ps.getRevision().get() == null) {
        status.put(chg.getId(), CommitMergeStatus.NO_PATCH_SET);
        continue;
      }

      final String idstr = ps.getRevision().get();
      final ObjectId id;
      try {
        id = ObjectId.fromString(idstr);
      } catch (IllegalArgumentException iae) {
        status.put(chg.getId(), CommitMergeStatus.NO_PATCH_SET);
        continue;
      }

      if (!tips.contains(id)) {
        // TODO Technically the proper way to do this test is to use a
        // RevWalk on "$id --not --all" and test for an empty set. But
        // that is way slower than looking for a ref directly pointing
        // at the desired tip. We should always have a ref available.
        //
        // TODO this is actually an error, the branch is gone but we
        // want to merge the issue. We can't safely do that if the
        // tip is not reachable.
        //
        status.put(chg.getId(), CommitMergeStatus.REVISION_GONE);
        continue;
      }

      final CodeReviewCommit commit;
      try {
        commit = (CodeReviewCommit) rw.parseCommit(id);
      } catch (IOException e) {
        throw new MergeException("Invalid issue commit " + id, e);
      }
      commit.changeKey = chg.getKey();
      commit.patchsetId = ps.getId();
      commit.originalOrder = commitOrder++;

      if (branchTip != null) {
        // If this commit is already merged its a bug in the queuing code
        // that we got back here. Just mark it complete and move on. Its
        // merged and that is all that mattered to the requestor.
        //
        try {
          if (rw.isMergedInto(commit, branchTip)) {
            commit.statusCode = CommitMergeStatus.ALREADY_MERGED;
            status.put(chg.getId(), commit.statusCode);
            continue;
          }
        } catch (IOException err) {
          throw new MergeException("Cannot perform merge base test", err);
        }
      }

      toMerge.add(commit);
    }
  }

  private void reduceToMinimalMerge() throws MergeException {
    final Collection<CodeReviewCommit> heads;
    try {
      heads = new MergeSorter(rw, branchTip).sort(toMerge);
    } catch (IOException e) {
      throw new MergeException("Branch head sorting failed", e);
    }

    for (final CodeReviewCommit c : toMerge) {
      if (c.statusCode != null) {
        status.put(c.patchsetId.getParentKey(), c.statusCode);
      }
    }

    toMerge.clear();
    toMerge.addAll(heads);
    Collections.sort(toMerge, new Comparator<CodeReviewCommit>() {
      public int compare(final CodeReviewCommit a, final CodeReviewCommit b) {
        return a.originalOrder - b.originalOrder;
      }
    });
  }

  private void mergeTopics() throws MergeException {
    // Take the first fast-forward available, if any is available in the set.
    //
    if (destProject.getSubmitType() != Project.SubmitType.MERGE_ALWAYS) {
      for (final Iterator<CodeReviewCommit> i = toMerge.iterator(); i.hasNext();) {
        try {
          final CodeReviewCommit n = i.next();
          if (mergeTip == null || rw.isMergedInto(mergeTip, n)) {
            mergeTip = n;
            i.remove();
            break;
          }
        } catch (IOException e) {
          throw new MergeException("Cannot fast-forward test during merge", e);
        }
      }
    }

    // If this project only permits fast-forwards, abort everything else.
    //
    if (destProject.getSubmitType() == Project.SubmitType.FAST_FORWARD_ONLY) {
      while (!toMerge.isEmpty()) {
        final CodeReviewCommit n = toMerge.remove(0);
        n.statusCode = CommitMergeStatus.PATH_CONFLICT;
        status.put(n.patchsetId.getParentKey(), n.statusCode);
      }
      return;
    }

    // For every other commit do a pair-wise merge.
    //
    while (!toMerge.isEmpty()) {
      final CodeReviewCommit n = toMerge.remove(0);
      final Merger m = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
      try {
        if (m.merge(new AnyObjectId[] {mergeTip, n})) {
          writeMergeCommit(m, n);

        } else {
          failed(n, CommitMergeStatus.PATH_CONFLICT);
        }
      } catch (IOException e) {
        if (e.getMessage().startsWith("Multiple merge bases for")) {
          try {
            failed(n, CommitMergeStatus.CRISS_CROSS_MERGE);
          } catch (IOException e2) {
            throw new MergeException("Cannot merge " + n.name(), e);
          }
        } else {
          throw new MergeException("Cannot merge " + n.name(), e);
        }
      }
    }
  }

  private CodeReviewCommit failed(final CodeReviewCommit n,
      final CommitMergeStatus failure) throws MissingObjectException,
      IncorrectObjectTypeException, IOException {
    rw.reset();
    rw.markStart(n);
    rw.markUninteresting(mergeTip);
    CodeReviewCommit failed;
    while ((failed = (CodeReviewCommit) rw.next()) != null) {
      if (failed.patchsetId == null) {
        continue;
      }

      failed.statusCode = failure;
      status.put(failed.patchsetId.getParentKey(), failed.statusCode);
    }
    return failed;
  }

  private void writeMergeCommit(final Merger m, final CodeReviewCommit n)
      throws IOException, MissingObjectException, IncorrectObjectTypeException {
    final List<CodeReviewCommit> merged = new ArrayList<CodeReviewCommit>();
    rw.reset();
    rw.markStart(n);
    rw.markUninteresting(mergeTip);
    for (final RevCommit c : rw) {
      final CodeReviewCommit crc = (CodeReviewCommit) c;
      if (crc.patchsetId != null) {
        merged.add(crc);
      }
    }

    final StringBuilder msgbuf = new StringBuilder();
    if (merged.size() == 1) {
      final CodeReviewCommit c = merged.get(0);
      final Change.Id changeId = c.patchsetId.getParentKey();
      msgbuf.append("Merge change ");
      msgbuf.append(changeId);
    } else {
      final ArrayList<CodeReviewCommit> o;
      o = new ArrayList<CodeReviewCommit>(merged);
      Collections.sort(o, new Comparator<CodeReviewCommit>() {
        public int compare(final CodeReviewCommit a, final CodeReviewCommit b) {
          final Change.Id aId = a.patchsetId.getParentKey();
          final Change.Id bId = b.patchsetId.getParentKey();
          return aId.get() - bId.get();
        }
      });

      msgbuf.append("Merge changes ");
      for (final Iterator<CodeReviewCommit> i = o.iterator(); i.hasNext();) {
        final Change.Id id = i.next().patchsetId.getParentKey();
        msgbuf.append(id);
        if (i.hasNext()) {
          msgbuf.append(',');
        }
      }
    }

    if (!R_HEADS_MASTER.equals(destBranch.get())) {
      msgbuf.append(" into ");
      msgbuf.append(destBranch.getShortName());
    }
    msgbuf.append("\n\n* changes:\n");
    for (final CodeReviewCommit c : merged) {
      msgbuf.append("  ");
      msgbuf.append(c.getShortMessage());
      msgbuf.append("\n");
    }

    final Commit mergeCommit = new Commit(db);
    mergeCommit.setTreeId(m.getResultTreeId());
    mergeCommit.setParentIds(new ObjectId[] {mergeTip, n});
    mergeCommit.setAuthor(myIdent);
    mergeCommit.setCommitter(mergeCommit.getAuthor());
    mergeCommit.setMessage(msgbuf.toString());

    final ObjectId id = m.getObjectWriter().writeCommit(mergeCommit);
    mergeTip = (CodeReviewCommit) rw.parseCommit(id);
  }

  private void markCleanMerges() throws MergeException {
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
      if (branchTip != null) {
        rw.markUninteresting(branchTip);
      } else {
        for (final Ref r : db.getAllRefs().values()) {
          if (r.getName().startsWith(Constants.R_HEADS)
              || r.getName().startsWith(Constants.R_TAGS)) {
            try {
              rw.markUninteresting(rw.parseCommit(r.getObjectId()));
            } catch (IncorrectObjectTypeException iote) {
              // Not a commit? Skip over it.
            }
          }
        }
      }

      CodeReviewCommit c;
      while ((c = (CodeReviewCommit) rw.next()) != null) {
        if (c.patchsetId != null) {
          c.statusCode = CommitMergeStatus.CLEAN_MERGE;
          status.put(c.patchsetId.getParentKey(), c.statusCode);

          if (branchUpdate.getRefLogIdent() == null) {
            setRefLogIdent(getSubmitter(c.patchsetId));
          }
        }
      }
    } catch (IOException e) {
      throw new MergeException("Cannot mark clean merges", e);
    }
  }

  private void setRefLogIdent(final PatchSetApproval submitAudit) {
    if (submitAudit != null) {
      branchUpdate.setRefLogIdent(identifiedUserFactory.create(
          submitAudit.getAccountId()).newPersonIdent());
    }
  }

  private void cherryPickChanges() throws MergeException {
    while (!toMerge.isEmpty()) {
      final CodeReviewCommit n = toMerge.remove(0);
      final ThreeWayMerger m;

      m = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
      try {
        if (n.getParentCount() != 1) {
          // We don't support cherry-picking a merge commit.
          //
          n.statusCode = CommitMergeStatus.PATH_CONFLICT;
          status.put(n.patchsetId.getParentKey(), n.statusCode);
          continue;
        }

        m.setBase(n.getParent(0));
        if (m.merge(mergeTip, n)) {
          writeCherryPickCommit(m, n);

        } else {
          n.statusCode = CommitMergeStatus.PATH_CONFLICT;
          status.put(n.patchsetId.getParentKey(), n.statusCode);
        }
      } catch (IOException e) {
        throw new MergeException("Cannot merge " + n.name(), e);
      }
    }
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

    if (!contains(footers, CHANGE_ID, n.changeKey.get())) {
      msgbuf.append(CHANGE_ID.getName());
      msgbuf.append(": ");
      msgbuf.append(n.changeKey.get());
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
          final ApprovalType at =
              approvalTypes.getApprovalType(a.getCategoryId());
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

    final PersonIdent submitIdent = toPersonIdent(submitAudit);
    final Commit mergeCommit = new Commit(db);
    mergeCommit.setTreeId(m.getResultTreeId());
    mergeCommit.setParentIds(new ObjectId[] {mergeTip});
    mergeCommit.setAuthor(n.getAuthorIdent());
    mergeCommit.setCommitter(submitIdent != null ? submitIdent : myIdent);
    mergeCommit.setMessage(msgbuf.toString());

    final ObjectId id = m.getObjectWriter().writeCommit(mergeCommit);
    mergeTip = (CodeReviewCommit) rw.parseCommit(id);
    n.statusCode = CommitMergeStatus.CLEAN_PICK;
    status.put(n.patchsetId.getParentKey(), n.statusCode);
    newCommits.put(n.patchsetId.getParentKey(), mergeTip);
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

  private PersonIdent toPersonIdent(final PatchSetApproval audit) {
    if (audit == null) {
      return null;
    }
    return identifiedUserFactory.create(audit.getAccountId()).newPersonIdent(
        audit.getGranted(), myIdent.getTimeZone());
  }

  private void updateBranch() throws MergeException {
    if (branchTip == null || branchTip != mergeTip) {
      branchUpdate.setForceUpdate(false);
      branchUpdate.setNewObjectId(mergeTip);
      branchUpdate.setRefLogMessage("merged", true);
      try {
        switch (branchUpdate.update(rw)) {
          case NEW:
          case FAST_FORWARD:
            replication.scheduleUpdate(destBranch.getParentKey(), branchUpdate
                .getName());
            break;

          default:
            throw new IOException(branchUpdate.getResult().name());
        }
      } catch (IOException e) {
        throw new MergeException("Cannot update " + branchUpdate.getName(), e);
      }
    }
  }

  private void updateChangeStatus() {
    for (final Change c : submitted) {
      final CommitMergeStatus s = status.get(c.getId());
      if (s == null) {
        // Shouldn't ever happen, but leave the change alone. We'll pick
        // it up on the next pass.
        //
        continue;
      }

      switch (s) {
        case CLEAN_MERGE: {
          final String txt =
              "Change has been successfully merged into the git repository.";
          setMerged(c, message(c, txt));
          break;
        }

        case CLEAN_PICK: {
          final CodeReviewCommit commit = newCommits.get(c.getId());
          final String txt =
              "Change has been successfully cherry-picked as " + commit.name()
                  + ".";
          setMerged(c, message(c, txt));
          break;
        }

        case ALREADY_MERGED:
          setMerged(c, null);
          break;

        case PATH_CONFLICT: {
          final String txt =
              "Your change could not be merged due to a path conflict.\n"
                  + "\n"
                  + "Please merge (or rebase) the change locally and upload the resolution for review.";
          setNew(c, message(c, txt));
          break;
        }

        case CRISS_CROSS_MERGE: {
          final String txt =
              "Your change requires a recursive merge to resolve.\n"
                  + "\n"
                  + "Please merge (or rebase) the change locally and upload the resolution for review.";
          setNew(c, message(c, txt));
          break;
        }

        case MISSING_DEPENDENCY: {
          ChangeMessage msg = null;
          try {
            final String txt =
                "Change could not be merged because of a missing dependency. As soon as its dependencies are submitted, the change will be submitted.";
            final List<ChangeMessage> msgList =
                schema.changeMessages().byChange(c.getId()).toList();
            if (msgList.size() > 0) {
              final ChangeMessage last = msgList.get(msgList.size() - 1);
              if (last.getAuthor() == null && txt.equals(last.getMessage())) {
                // The last message was written by us, and it said this
                // same message already. Its unlikely anything has changed
                // that would cause us to need to repeat ourselves.
                //
                break;
              }
            }

            msg = message(c, txt);
            schema.changeMessages().insert(Collections.singleton(msg));
          } catch (OrmException e) {
          }

          try {
            final MergeFailSender cm = mergeFailSenderFactory.create(c);
            final PatchSetApproval submitter =
                getSubmitter(c.currentPatchSetId());
            if (submitter != null) {
              cm.setFrom(submitter.getAccountId());
            }
            cm.setReviewDb(schema);
            cm.setPatchSet(schema.patchSets().get(c.currentPatchSetId()));
            cm.setChangeMessage(msg);
            cm.send();
          } catch (OrmException e) {
            log.error("Cannot submit patch set for Change " + c.getId()
                + " due to a missing dependency.", e);
          } catch (EmailException e) {
            log.error("Cannot submit patch set for Change " + c.getId()
                + " due to a missing dependency.", e);
          }

          break;
        }

        default:
          setNew(c, message(c, "Unspecified merge failure: " + s.name()));
          break;
      }
    }
  }

  private ChangeMessage message(final Change c, final String body) {
    final String uuid;
    try {
      uuid = ChangeUtil.messageUUID(schema);
    } catch (OrmException e) {
      return null;
    }
    final ChangeMessage m =
        new ChangeMessage(new ChangeMessage.Key(c.getId(), uuid), null);
    m.setMessage(body);
    return m;
  }

  private PatchSetApproval getSubmitter(PatchSet.Id c) {
    if (c == null) {
      return null;
    }
    PatchSetApproval submitter = null;
    try {
      final List<PatchSetApproval> approvals =
          schema.patchSetApprovals().byPatchSet(c).toList();
      for (PatchSetApproval a : approvals) {
        if (a.getValue() > 0
            && ApprovalCategory.SUBMIT.equals(a.getCategoryId())) {
          if (submitter == null
              || a.getGranted().compareTo(submitter.getGranted()) > 0) {
            submitter = a;
          }
        }
      }
    } catch (OrmException e) {
    }
    return submitter;
  }

  private void setMerged(Change c, ChangeMessage msg) {
    final PatchSet.Id merged = c.currentPatchSetId();
    PatchSetApproval submitter = null;
    for (int attempts = 0; attempts < 10; attempts++) {
      c.setStatus(Change.Status.MERGED);
      ChangeUtil.updated(c);
      try {
        final Transaction txn = schema.beginTransaction();

        // Flatten out all existing approvals based upon the current
        // permissions. Once the change is closed the approvals are
        // not updated at presentation view time, so we need to make.
        // sure they are accurate now. This way if permissions get
        // modified in the future, historical records stay accurate.
        //
        final List<PatchSetApproval> approvals =
            schema.patchSetApprovals().byChange(c.getId()).toList();
        final FunctionState fs = functionState.create(c, merged, approvals);
        for (ApprovalType at : approvalTypes.getApprovalTypes()) {
          CategoryFunction.forCategory(at.getCategory()).run(at, fs);
        }
        for (PatchSetApproval a : approvals) {
          if (a.getValue() > 0
              && ApprovalCategory.SUBMIT.equals(a.getCategoryId())
              && a.getPatchSetId().equals(merged)) {
            if (submitter == null
                || a.getGranted().compareTo(submitter.getGranted()) > 0) {
              submitter = a;
            }
          }
          a.cache(c);
        }
        schema.patchSetApprovals().update(approvals, txn);

        if (msg != null) {
          if (submitter != null && msg.getAuthor() == null) {
            msg.setAuthor(submitter.getAccountId());
          }
          schema.changeMessages().insert(Collections.singleton(msg), txn);
        }
        schema.changes().update(Collections.singleton(c), txn);
        txn.commit();
        break;
      } catch (OrmException e) {
        final Change.Id id = c.getId();
        try {
          c = schema.changes().get(id);
          if (!merged.equals(c.currentPatchSetId())) {
            // Uncool; the patch set changed after we merged it.
            // Go back to the patch set that was actually merged.
            //
            try {
              c.setCurrentPatchSet(patchSetInfoFactory.get(merged));
            } catch (PatchSetInfoNotAvailableException e1) {
              log.error("Cannot read merged patch set " + merged, e1);
            }
          }
        } catch (OrmException e2) {
          log.error("Cannot set change " + id + " to merged " + merged, e2);
        }
      }
    }

    try {
      final MergedSender cm = mergedSenderFactory.create(c);
      if (submitter != null) {
        cm.setFrom(submitter.getAccountId());
      }
      cm.setReviewDb(schema);
      cm.setPatchSet(schema.patchSets().get(c.currentPatchSetId()));
      cm.send();
    } catch (OrmException e) {
      log.error("Cannot send email for submitted patch set " + c.getId(), e);
    } catch (EmailException e) {
      log.error("Cannot send email for submitted patch set " + c.getId(), e);
    }
  }

  private void setNew(Change c, ChangeMessage msg) {
    for (int attempts = 0; attempts < 10; attempts++) {
      c.setStatus(Change.Status.NEW);
      ChangeUtil.updated(c);
      try {
        final Transaction txn = schema.beginTransaction();
        schema.changes().update(Collections.singleton(c), txn);
        if (msg != null) {
          schema.changeMessages().insert(Collections.singleton(msg), txn);
        }
        txn.commit();
        break;
      } catch (OrmException e) {
        try {
          c = schema.changes().get(c.getId());
          if (c.getStatus().isClosed()) {
            // Someone else marked it close while we noticed a failure.
            // That's fine, leave it closed.
            //
            break;
          }
        } catch (OrmException e2) {
        }
      }
    }

    try {
      final PatchSetApproval submitter = getSubmitter(c.currentPatchSetId());
      final MergeFailSender cm = mergeFailSenderFactory.create(c);
      if (submitter != null) {
        cm.setFrom(submitter.getAccountId());
      }
      cm.setReviewDb(schema);
      cm.setPatchSet(schema.patchSets().get(c.currentPatchSetId()));
      cm.setChangeMessage(msg);
      cm.send();
    } catch (OrmException e) {
      log.error("Cannot submit patch set for Change " + c.getId()
          + " due to a path conflict.", e);
    } catch (EmailException e) {
      log.error("Cannot submit patch set for Change " + c.getId()
          + " due to a path conflict.", e);
    }
  }
}
