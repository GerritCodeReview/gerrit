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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.gerrit.common.ChangeHookRunner;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeMessage;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.mail.MergeFailSender;
import com.google.gerrit.server.mail.MergedSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.workflow.CategoryFunction;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gwtorm.client.AtomicUpdate;
import com.google.gwtorm.client.OrmConcurrencyException;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.Merger;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.Timestamp;
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
import java.util.TimeZone;

import javax.annotation.Nullable;

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

  /** Amount of time to wait between submit and checking for missing deps. */
  private static final long DEPENDENCY_DELAY =
      MILLISECONDS.convert(15, MINUTES);

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
  private final MergeQueue mergeQueue;

  private final PersonIdent myIdent;
  private final Branch.NameKey destBranch;
  private Project destProject;
  private final List<CodeReviewCommit> toMerge;
  private List<Change> submitted;
  private final Map<Change.Id, CodeReviewCommit> commits;
  private ReviewDb schema;
  private Repository db;
  private RevWalk rw;
  private RevFlag CAN_MERGE;
  private CodeReviewCommit branchTip;
  private CodeReviewCommit mergeTip;
  private Set<RevCommit> alreadyAccepted;
  private RefUpdate branchUpdate;

  private final ChangeHookRunner hooks;
  private final AccountCache accountCache;
  private final CreateCodeReviewNotes.Factory codeReviewNotesFactory;
  private Change changeForTest;
  private Change.TestMergeStatus changeMergeTestStatus;
  private boolean submitStillPossible;
  private final ChangeTestMergeQueue changeTestMergeQueue;

  @Inject
  MergeOp(final GitRepositoryManager grm, final SchemaFactory<ReviewDb> sf,
      final ProjectCache pc, final FunctionState.Factory fs,
      final ReplicationQueue rq, final MergedSender.Factory msf,
      final MergeFailSender.Factory mfsf,
      @CanonicalWebUrl @Nullable final Provider<String> cwu,
      final ApprovalTypes approvalTypes, final PatchSetInfoFactory psif,
      final IdentifiedUser.GenericFactory iuf,
      @GerritPersonIdent final PersonIdent myIdent,
      final MergeQueue mergeQueue, @Assisted final Branch.NameKey branch,
      final ChangeHookRunner hooks, final AccountCache accountCache,
      final CreateCodeReviewNotes.Factory crnf,
      final ChangeTestMergeQueue testMergeQueue) {
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
    this.mergeQueue = mergeQueue;
    this.hooks = hooks;
    this.accountCache = accountCache;
    codeReviewNotesFactory = crnf;

    this.myIdent = myIdent;
    destBranch = branch;
    toMerge = new ArrayList<CodeReviewCommit>();
    commits = new HashMap<Change.Id, CodeReviewCommit>();
    changeTestMergeQueue = testMergeQueue;
    changeMergeTestStatus = Change.TestMergeStatus.TEST_PENDING;
  }

  public void runTestMerge(Change changeForTest) {
    this.changeForTest = changeForTest;

    try {
      merge();
      if (schema == null) {
        schema = schemaFactory.open();
      }
      changeForTest.setMergeTestStatus(changeMergeTestStatus);
      schema.changes().update(Collections.singleton(changeForTest));

    } catch (MergeException e) {
      log.error("Test merge attempt for change: " + changeForTest.getId()
          + " failed", e);
    } catch (OrmException e) {
      log.error("Test merge attempt for change: " + changeForTest.getId()
          + " failed: Not able to query the database", e);
    } finally {
      this.changeForTest = null;
      changeMergeTestStatus = Change.TestMergeStatus.TEST_PENDING;
      if (schema != null) {
        schema.close();
      }
      schema = null;
    }
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
      if (rw != null) {
        rw.release();
      }
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
    if (changeForTest == null) {
      updateBranch();
      updateChangeStatus();
    } else {
      try {
        updateChangeMessage();
      } catch (OrmException e) {
        log.warn("Change message could not be updated");
      }
    }
  }

  private void openRepository() throws MergeException {
    final Project.NameKey name = destBranch.getParentKey();
    try {
      db = repoManager.openRepository(name);
    } catch (RepositoryNotFoundException notGit) {
      final String m = "Repository \"" + name.get() + "\" unknown.";
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
    CAN_MERGE = rw.newFlag("CAN_MERGE");
  }

  private void openBranch() throws MergeException {
    alreadyAccepted = new HashSet<RevCommit>();

    try {
      branchUpdate = db.updateRef(destBranch.get());
      if (branchUpdate.getOldObjectId() != null) {
        branchTip =
            (CodeReviewCommit) rw.parseCommit(branchUpdate.getOldObjectId());
        alreadyAccepted.add(branchTip);
      } else {
        branchTip = null;
      }

      for (final Ref r : db.getAllRefs().values()) {
        if (r.getName().startsWith(Constants.R_HEADS)
            || r.getName().startsWith(Constants.R_TAGS)) {
          try {
            alreadyAccepted.add(rw.parseCommit(r.getObjectId()));
          } catch (IncorrectObjectTypeException iote) {
            // Not a commit? Skip over it.
          }
        }
      }
    } catch (IOException e) {
      throw new MergeException("Cannot open branch", e);
    }
  }

  private void listPendingSubmits() throws MergeException {
    try {
      if (changeForTest == null) {
        submitted = schema.changes().submitted(destBranch).toList();
      } else {
        submitted = new ArrayList<Change>();
        submitted.add(changeForTest);
      }
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
      final Change.Id changeId = chg.getId();
      if (chg.currentPatchSetId() == null) {
        commits.put(changeId, CodeReviewCommit
            .error(CommitMergeStatus.NO_PATCH_SET));
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
        commits.put(changeId, CodeReviewCommit
            .error(CommitMergeStatus.NO_PATCH_SET));
        continue;
      }

      final String idstr = ps.getRevision().get();
      final ObjectId id;
      try {
        id = ObjectId.fromString(idstr);
      } catch (IllegalArgumentException iae) {
        commits.put(changeId, CodeReviewCommit
            .error(CommitMergeStatus.NO_PATCH_SET));
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
        commits.put(changeId, CodeReviewCommit
            .error(CommitMergeStatus.REVISION_GONE));
        continue;
      }

      final CodeReviewCommit commit;
      try {
        commit = (CodeReviewCommit) rw.parseCommit(id);
      } catch (IOException e) {
        log.error("Invalid commit " + id.name() + " on " + chg.getKey(), e);
        commits.put(changeId, CodeReviewCommit
            .error(CommitMergeStatus.REVISION_GONE));
        continue;
      }

      commit.change = chg;
      commit.patchsetId = ps.getId();
      commit.originalOrder = commitOrder++;
      commits.put(changeId, commit);

      if (branchTip != null) {
        // If this commit is already merged its a bug in the queuing code
        // that we got back here. Just mark it complete and move on. Its
        // merged and that is all that mattered to the requestor.
        //
        try {
          if (rw.isMergedInto(commit, branchTip)) {
            commit.statusCode = CommitMergeStatus.ALREADY_MERGED;
            continue;
          }
        } catch (IOException err) {
          throw new MergeException("Cannot perform merge base test", err);
        }
      }

      commit.add(CAN_MERGE);
      toMerge.add(commit);
    }
  }

  private void reduceToMinimalMerge() throws MergeException {
    final Collection<CodeReviewCommit> heads;
    try {
      heads = new MergeSorter(rw, alreadyAccepted, CAN_MERGE).sort(toMerge);
    } catch (IOException e) {
      throw new MergeException("Branch head sorting failed", e);
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

    if (destProject.getSubmitType() == Project.SubmitType.FAST_FORWARD_ONLY) {
      // If this project only permits fast-forwards, abort everything else.
      //
      while (!toMerge.isEmpty()) {
        final CodeReviewCommit n = toMerge.remove(0);
        n.statusCode = CommitMergeStatus.NOT_FAST_FORWARD;
      }

    } else {
      // For every other commit do a pair-wise merge.
      //
      while (!toMerge.isEmpty()) {
        mergeOneCommit(toMerge.remove(0));
      }
    }
  }

  private void mergeOneCommit(final CodeReviewCommit n) throws MergeException {
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

  private CodeReviewCommit failed(final CodeReviewCommit n,
      final CommitMergeStatus failure) throws MissingObjectException,
      IncorrectObjectTypeException, IOException {
    rw.reset();
    rw.markStart(n);
    rw.markUninteresting(mergeTip);
    CodeReviewCommit failed;
    while ((failed = (CodeReviewCommit) rw.next()) != null) {
      failed.statusCode = failure;
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
      rw.parseBody(c);
      msgbuf.append("Merge \"");
      msgbuf.append(c.getShortMessage());
      msgbuf.append("\"");

    } else {
      msgbuf.append("Merge changes ");
      for (final Iterator<CodeReviewCommit> i = merged.iterator(); i.hasNext();) {
        msgbuf.append(i.next().change.getKey().abbreviate());
        if (i.hasNext()) {
          msgbuf.append(',');
        }
      }
    }

    if (!R_HEADS_MASTER.equals(destBranch.get())) {
      msgbuf.append(" into ");
      msgbuf.append(destBranch.getShortName());
    }

    if (merged.size() > 1) {
      msgbuf.append("\n\n* changes:\n");
      for (final CodeReviewCommit c : merged) {
        rw.parseBody(c);
        msgbuf.append("  ");
        msgbuf.append(c.getShortMessage());
        msgbuf.append("\n");
      }
    }

    PersonIdent authorIdent = computeAuthor(merged);

    final CommitBuilder mergeCommit = new CommitBuilder();
    mergeCommit.setTreeId(m.getResultTreeId());
    mergeCommit.setParentIds(mergeTip, n);
    mergeCommit.setAuthor(authorIdent);
    mergeCommit.setCommitter(myIdent);
    mergeCommit.setMessage(msgbuf.toString());

    mergeTip = (CodeReviewCommit) rw.parseCommit(commit(m, mergeCommit));
  }

  private PersonIdent computeAuthor(
      final List<CodeReviewCommit> codeReviewCommits) {
    PatchSetApproval submitter = null;
    for (final CodeReviewCommit c : codeReviewCommits) {
      PatchSetApproval s = getSubmitter(c.patchsetId);
      if (submitter == null
          || (s != null && s.getGranted().compareTo(submitter.getGranted()) > 0)) {
        submitter = s;
      }
    }

    // Try to use the submitter's identity for the merge commit author.
    // If all of the commits being merged are created by the submitter,
    // prefer the identity line they used in the commits rather than the
    // preferred identity stored in the user account. This way the Git
    // commit records are more consistent internally.
    //
    PersonIdent authorIdent;
    if (submitter != null) {
      IdentifiedUser who =
          identifiedUserFactory.create(submitter.getAccountId());
      Set<String> emails = new HashSet<String>();
      for (RevCommit c : codeReviewCommits) {
        emails.add(c.getAuthorIdent().getEmailAddress());
      }

      final Timestamp dt = submitter.getGranted();
      final TimeZone tz = myIdent.getTimeZone();
      if (emails.size() == 1
          && who.getEmailAddresses().contains(emails.iterator().next())) {
        authorIdent =
            new PersonIdent(codeReviewCommits.get(0).getAuthorIdent(), dt, tz);
      } else {
        authorIdent = who.newCommitterIdent(dt, tz);
      }
    } else {
      authorIdent = myIdent;
    }
    return authorIdent;
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
      for (RevCommit c : alreadyAccepted) {
        rw.markUninteresting(c);
      }

      CodeReviewCommit c;
      while ((c = (CodeReviewCommit) rw.next()) != null) {
        if (c.patchsetId != null) {
          c.statusCode = CommitMergeStatus.CLEAN_MERGE;
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
          submitAudit.getAccountId()).newRefLogIdent());
    }
  }

  private void cherryPickChanges() throws MergeException {
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
          final ApprovalType at =
              approvalTypes.byId(a.getCategoryId());
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

  private ObjectId commit(final Merger m, final CommitBuilder mergeCommit)
      throws IOException, UnsupportedEncodingException {
    ObjectInserter oi = m.getObjectInserter();
    try {
      ObjectId id = oi.insert(mergeCommit);
      oi.flush();
      return id;
    } finally {
      oi.release();
    }
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

  private void updateBranch() throws MergeException {
    if (mergeTip != null && (branchTip == null || branchTip != mergeTip)) {
      if (GitRepositoryManager.REF_CONFIG.equals(branchUpdate.getName())) {
        try {
          ProjectConfig cfg = new ProjectConfig(destProject.getNameKey());
          cfg.load(db, mergeTip);
        } catch (Exception e) {
          throw new MergeException("Submit would store invalid"
              + " project configuration " + mergeTip.name() + " for "
              + destProject.getName(), e);
        }
      }

      branchUpdate.setForceUpdate(false);
      branchUpdate.setNewObjectId(mergeTip);
      branchUpdate.setRefLogMessage("merged", true);
      try {
        switch (branchUpdate.update(rw)) {
          case NEW:
          case FAST_FORWARD:
            if (GitRepositoryManager.REF_CONFIG.equals(branchUpdate.getName())) {
              projectCache.evict(destProject);
              ProjectState ps = projectCache.get(destProject.getNameKey());
              repoManager.setProjectDescription(destProject.getNameKey(), //
                  ps.getProject().getDescription());
            }

            replication.scheduleUpdate(destBranch.getParentKey(), branchUpdate
                .getName());

            Account account = null;
            final PatchSetApproval submitter = getSubmitter(mergeTip.patchsetId);
            if (submitter != null) {
              account = accountCache.get(submitter.getAccountId()).getAccount();
            }
            hooks.doRefUpdatedHook(destBranch, branchUpdate, account);
            break;

          default:
            throw new IOException(branchUpdate.getResult().name());
        }
      } catch (IOException e) {
        throw new MergeException("Cannot update " + branchUpdate.getName(), e);
      }
    }
  }

  public void testMergeabilityOfChangesbyBranch() {
    try {
      List<Change> changes =
          schema.changes().allNewNotMergeable(destBranch).toList();

      for (Change change : changes) {
        changeTestMergeQueue.add(change);
      }
    } catch (OrmException e) {
      log.error("Test merge attempt for branch: " + destBranch.get()
          + " failed: Not able to query database", e);
    }
  }

  private void updateChangeMessage() throws OrmException {
    final Change c = submitted.get(0);
    final CodeReviewCommit commit = commits.get(c.getId());
    final CommitMergeStatus s = commit != null ? commit.statusCode : null;

    if (s == null) {
      this.changeMergeTestStatus = Change.TestMergeStatus.NOT_MERGEABLE;
      ChangeMessage msg =
          message(c, "Merge Test Fail: Unspecified merge failure");
      schema.changeMessages().insert(Collections.singleton(msg));
      return;
    }

    ChangeMessage msg = null;

    if (s.equals(s.CLEAN_MERGE) || s.equals(s.CLEAN_PICK)
        || s.equals(s.ALREADY_MERGED)) {
      this.changeMergeTestStatus = Change.TestMergeStatus.IS_MERGEABLE;
      if (c.getMergeTestStatus() == Change.TestMergeStatus.NOT_MERGEABLE) {
        msg = message(c, "Successful Merge Test");
      }
    } else {

      this.changeMergeTestStatus = Change.TestMergeStatus.NOT_MERGEABLE;
      if (s.equals(s.MISSING_DEPENDENCY)) {
        final String str = dependencyError(commit);
        if (str != null) {
          msg = message(c, "Merge Test Fail: " + str);
        } else {
          msg = message(c, "Merge Test Fail: missing dependency.");
        }
      } else {
        msg = message(c, "Merge Test Fail: " + s.getMessage());
      }
    }

    if (msg != null) {
      schema.changeMessages().insert(Collections.singleton(msg));
    }
  }

  private void updateChangeStatus() throws MergeException {
    List<CodeReviewCommit> merged = new ArrayList<CodeReviewCommit>();

    for (final Change c : submitted) {
      final CodeReviewCommit commit = commits.get(c.getId());
      final CommitMergeStatus s = commit != null ? commit.statusCode : null;
      if (s == null) {
        // Shouldn't ever happen, but leave the change alone. We'll pick
        // it up on the next pass.
        //
        continue;
      }

      final String txt = s.getMessage();

      switch (s) {
        case CLEAN_MERGE: {
          setMerged(c, message(c, txt));
          merged.add(commit);
          this.testMergeabilityOfChangesbyBranch();
          break;
        }

        case CLEAN_PICK: {
          setMerged(c, message(c, txt));
          merged.add(commit);
          this.testMergeabilityOfChangesbyBranch();
          break;
        }

        case ALREADY_MERGED:
          setMerged(c, null);
          merged.add(commit);
          break;

        case PATH_CONFLICT:
        case CRISS_CROSS_MERGE:
        case CANNOT_CHERRY_PICK_ROOT:
        case NOT_FAST_FORWARD: {
          setNew(c, message(c, txt));
          break;
        }

        case MISSING_DEPENDENCY: {
          String str = dependencyError(commit);
          if (submitStillPossible) {
            if (str != null) {
              sendMergeFail(c, message(c, str), false);
            }
          } else {
            setNew(c, message(c, str));
          }
          break;
        }

        default:
          setNew(c, message(c, "Unspecified merge failure: " + s.name()));
          break;
      }
    }

    CreateCodeReviewNotes codeReviewNotes =
        codeReviewNotesFactory.create(schema, db);
    try {
      codeReviewNotes.create(merged, computeAuthor(merged));
    } catch (CodeReviewNoteCreationException e) {
      log.error(e.getMessage());
    }
    replication.scheduleUpdate(destBranch.getParentKey(),
        GitRepositoryManager.REFS_NOTES_REVIEW);
  }

  private String dependencyError(final CodeReviewCommit commit) {
    final Change c = commit.change;
    if (commit.missing == null) {
      commit.missing = new ArrayList<CodeReviewCommit>();
    }

    this.submitStillPossible = commit.missing.size() > 0;
    for (CodeReviewCommit missingCommit : commit.missing) {
      loadChangeInfo(missingCommit);

      if (missingCommit.patchsetId == null) {
        // The commit doesn't have a patch set, so it cannot be
        // submitted to the branch.
        //
        submitStillPossible = false;
        break;
      }

      if (!missingCommit.change.currentPatchSetId().equals(
          missingCommit.patchsetId)) {
        // If the missing commit is not the current patch set,
        // the change must be rebased to use the proper parent.
        //
        submitStillPossible = false;
        break;
      }
    }

    final long now = System.currentTimeMillis();
    final long waitUntil = c.getLastUpdatedOn().getTime() + DEPENDENCY_DELAY;
    if (submitStillPossible && now < waitUntil) {
      // If we waited a short while we might still be able to get
      // this change submitted. Reschedule an attempt in a bit.
      //
      mergeQueue.recheckAfter(destBranch, waitUntil - now, MILLISECONDS);
      return null;

    } else if (submitStillPossible) {
      // It would be possible to submit the change if the missing
      // dependencies are also submitted. Perhaps the user just
      // forgot to submit those.
      //
      String txt =
          "Change could not be merged because of a missing dependency.";
      if (!isAlreadySent(c, txt)) {
        StringBuilder m = new StringBuilder();
        m.append(txt);
        m.append("\n");

        m.append("\n");

        m.append("The following changes must also be submitted:\n");
        m.append("\n");
        for (CodeReviewCommit missingCommit : commit.missing) {
          m.append("* ");
          m.append(missingCommit.change.getKey().get());
          m.append("\n");
        }
        txt = m.toString();
      }

      return txt;

    } else {
      // It is impossible to submit this change as-is. The author
      // needs to rebase it in order to work around the missing
      // dependencies.
      //
      StringBuilder m = new StringBuilder();
      m.append("Change cannot be merged due"
          + " to unsatisfiable dependencies.\n");
      m.append("\n");
      m.append("The following dependency errors were found:\n");
      m.append("\n");
      for (CodeReviewCommit missingCommit : commit.missing) {
        if (missingCommit.patchsetId != null) {
          m.append("* Depends on patch set ");
          m.append(missingCommit.patchsetId.get());
          m.append(" of ");
          m.append(missingCommit.change.getKey().abbreviate());
          m.append(", however the current patch set is ");
          m.append(missingCommit.change.currentPatchSetId().get());
          m.append(".\n");

        } else {
          m.append("* Depends on commit ");
          m.append(missingCommit.name());
          m.append(" which has no change associated with it.\n");
        }
      }
      m.append("\n");
      m.append("Please rebase the change and upload a replacement commit.");
      return m.toString();
    }
  }

  private void loadChangeInfo(final CodeReviewCommit commit) {
    if (commit.patchsetId == null) {
      try {
        List<PatchSet> matches =
            schema.patchSets().byRevision(new RevId(commit.name())).toList();
        if (matches.size() == 1) {
          final PatchSet ps = matches.get(0);
          commit.patchsetId = ps.getId();
          commit.change = schema.changes().get(ps.getId().getParentKey());
        }
      } catch (OrmException e) {
      }
    }
  }

  private boolean isAlreadySent(final Change c, final String prefix) {
    try {
      final List<ChangeMessage> msgList =
          schema.changeMessages().byChange(c.getId()).toList();
      if (msgList.size() > 0) {
        final ChangeMessage last = msgList.get(msgList.size() - 1);
        if (last.getAuthor() == null && last.getMessage().startsWith(prefix)) {
          // The last message was written by us, and it said this
          // same message already. Its unlikely anything has changed
          // that would cause us to need to repeat ourselves.
          //
          return true;
        }
      }

      // The last message was not sent by us, or doesn't match the text
      // we are about to send.
      //
      return false;
    } catch (OrmException e) {
      return true;
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
    final Change.Id changeId = c.getId();
    final PatchSet.Id merged = c.currentPatchSetId();

    try {
      schema.changes().atomicUpdate(changeId, new AtomicUpdate<Change>() {
        @Override
        public Change update(Change c) {
          c.setStatus(Change.Status.MERGED);
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
          ChangeUtil.updated(c);
          return c;
        }
      });
    } catch (OrmConcurrencyException err) {
    } catch (OrmException err) {
      log.warn("Cannot update change status", err);
    }

    // Flatten out all existing approvals based upon the current
    // permissions. Once the change is closed the approvals are
    // not updated at presentation view time, so we need to make.
    // sure they are accurate now. This way if permissions get
    // modified in the future, historical records stay accurate.
    //
    PatchSetApproval submitter = null;
    try {
      c.setStatus(Change.Status.MERGED);
      final List<PatchSetApproval> approvals =
          schema.patchSetApprovals().byChange(changeId).toList();
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
      schema.patchSetApprovals().update(approvals);
    } catch (OrmException err) {
      log.warn("Cannot normalize approvals for change " + changeId, err);
    }

    if (msg != null) {
      if (submitter != null && msg.getAuthor() == null) {
        msg.setAuthor(submitter.getAccountId());
      }
      try {
        schema.changeMessages().insert(Collections.singleton(msg));
      } catch (OrmException err) {
        log.warn("Cannot store message on change", err);
      }
    }

    try {
      final MergedSender cm = mergedSenderFactory.create(c);
      if (submitter != null) {
        cm.setFrom(submitter.getAccountId());
      }
      cm.setPatchSet(schema.patchSets().get(c.currentPatchSetId()));
      cm.send();
    } catch (OrmException e) {
      log.error("Cannot send email for submitted patch set " + c.getId(), e);
    } catch (EmailException e) {
      log.error("Cannot send email for submitted patch set " + c.getId(), e);
    }

    try {
      hooks.doChangeMergedHook(c, //
          accountCache.get(submitter.getAccountId()).getAccount(), //
          schema.patchSets().get(c.currentPatchSetId()));
    } catch (OrmException ex) {
      log.error("Cannot run hook for submitted patch set " + c.getId(), ex);
    }
  }

  private void setNew(Change c, ChangeMessage msg) {
    sendMergeFail(c, msg, true);
  }

  private void sendMergeFail(Change c, ChangeMessage msg, final boolean makeNew) {
    try {
      schema.changeMessages().insert(Collections.singleton(msg));
    } catch (OrmException err) {
      log.warn("Cannot record merge failure message", err);
    }

    if (makeNew) {
      try {
        schema.changes().atomicUpdate(c.getId(), new AtomicUpdate<Change>() {
          @Override
          public Change update(Change c) {
            if (c.getStatus().isOpen()) {
              c.setStatus(Change.Status.NEW);
              ChangeUtil.updated(c);
            }
            return c;
          }
        });
      } catch (OrmConcurrencyException err) {
      } catch (OrmException err) {
        log.warn("Cannot update change status", err);
      }
    } else {
      try {
        ChangeUtil.touch(c, schema);
      } catch (OrmException err) {
        log.warn("Cannot update change timestamp", err);
      }
    }

    try {
      final MergeFailSender cm = mergeFailSenderFactory.create(c);
      final PatchSetApproval submitter = getSubmitter(c.currentPatchSetId());
      if (submitter != null) {
        cm.setFrom(submitter.getAccountId());
      }
      cm.setPatchSet(schema.patchSets().get(c.currentPatchSetId()));
      cm.setChangeMessage(msg);
      cm.send();
    } catch (OrmException e) {
      log.error("Cannot send email notifications about merge failure", e);
    } catch (EmailException e) {
      log.error("Cannot send email notifications about merge failure", e);
    }
  }
}
