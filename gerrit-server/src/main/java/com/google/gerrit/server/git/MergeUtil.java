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

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.ApprovalCategory;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PackParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

public class MergeUtil {
  private static final Logger log = LoggerFactory.getLogger(MergeUtil.class);

  private static final String R_HEADS_MASTER =
      Constants.R_HEADS + Constants.MASTER;

  private static final ApprovalCategory.Id CRVW = //
      new ApprovalCategory.Id("CRVW");
  private static final ApprovalCategory.Id VRIF = //
      new ApprovalCategory.Id("VRIF");

  private static final FooterKey REVIEWED_ON = new FooterKey("Reviewed-on");
  private static final FooterKey CHANGE_ID = new FooterKey("Change-Id");

  public static CodeReviewCommit getFirstFastForward(
      final CodeReviewCommit mergeTip, final RevWalk rw,
      final List<CodeReviewCommit> toMerge) throws MergeException {
    for (final Iterator<CodeReviewCommit> i = toMerge.iterator(); i.hasNext();) {
      try {
        final CodeReviewCommit n = i.next();
        if (mergeTip == null || rw.isMergedInto(mergeTip, n)) {
          i.remove();
          return n;
        }
      } catch (IOException e) {
        throw new MergeException("Cannot fast-forward test during merge", e);
      }
    }
    return mergeTip;
  }

  public static void reduceToMinimalMerge(final MergeSorter mergeSorter,
      final List<CodeReviewCommit> toSort) throws MergeException {
    final Collection<CodeReviewCommit> heads;
    try {
      heads = mergeSorter.sort(toSort);
    } catch (IOException e) {
      throw new MergeException("Branch head sorting failed", e);
    }

    toSort.clear();
    toSort.addAll(heads);
    Collections.sort(toSort, new Comparator<CodeReviewCommit>() {
      @Override
      public int compare(final CodeReviewCommit a, final CodeReviewCommit b) {
        return a.originalOrder - b.originalOrder;
      }
    });
  }

  public static PatchSetApproval getSubmitter(final ReviewDb reviewDb,
      final PatchSet.Id c) {
    if (c == null) {
      return null;
    }
    PatchSetApproval submitter = null;
    try {
      final List<PatchSetApproval> approvals =
          reviewDb.patchSetApprovals().byPatchSet(c).toList();
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

  public static CodeReviewCommit createCherryPickFromCommit(Repository repo,
      ObjectInserter inserter, CodeReviewCommit mergeTip, CodeReviewCommit originalCommit,
      PersonIdent cherryPickCommitterIdent, String commitMsg, RevWalk rw,
      Boolean useContentMerge) throws MissingObjectException, IncorrectObjectTypeException, IOException {

    final ThreeWayMerger m =
        newThreeWayMerger(repo, inserter, useContentMerge);

    m.setBase(originalCommit.getParent(0));
    if (m.merge(mergeTip, originalCommit)) {

      final CommitBuilder mergeCommit = new CommitBuilder();

      mergeCommit.setTreeId(m.getResultTreeId());
      mergeCommit.setParentId(mergeTip);
      mergeCommit.setAuthor(originalCommit.getAuthorIdent());
      mergeCommit.setCommitter(cherryPickCommitterIdent);
      mergeCommit.setMessage(commitMsg);

      final ObjectId id = commit(inserter, mergeCommit);
      final CodeReviewCommit newCommit =
          (CodeReviewCommit) rw.parseCommit(id);

      return newCommit;
    } else {
      return null;
    }
  }

  public static String createCherryPickCommitMessage(final CodeReviewCommit n,
      final ApprovalTypes approvalTypes, final Provider<String> urlProvider,
      final ReviewDb db, final IdentifiedUser.GenericFactory identifiedUserFactory) {
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

    for (final PatchSetApproval a : getApprovalsForCommit(db, n)) {
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

    return msgbuf.toString();
  }

  public static List<PatchSetApproval> getApprovalsForCommit(final ReviewDb db, final CodeReviewCommit n) {
    List<PatchSetApproval> approvalList = null;
    try {
      approvalList =
          db.patchSetApprovals().byPatchSet(n.patchsetId).toList();
      Collections.sort(approvalList, new Comparator<PatchSetApproval>() {
        @Override
        public int compare(final PatchSetApproval a, final PatchSetApproval b) {
          return a.getGranted().compareTo(b.getGranted());
        }
      });
    } catch (OrmException e) {
      log.error("Can't read approval records for " + n.patchsetId, e);
    }
    return approvalList;
  }

  private static boolean contains(List<FooterLine> footers, FooterKey key, String val) {
    for (final FooterLine line : footers) {
      if (line.matches(key) && val.equals(line.getValue())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isSignedOffBy(List<FooterLine> footers, String email) {
    for (final FooterLine line : footers) {
      if (line.matches(FooterKey.SIGNED_OFF_BY)
          && email.equals(line.getEmailAddress())) {
        return true;
      }
    }
    return false;
  }

  public static PersonIdent computeMergeCommitAuthor(final ReviewDb reviewDb,
      final IdentifiedUser.GenericFactory identifiedUserFactory,
      final PersonIdent myIdent, final RevWalk rw,
      final List<CodeReviewCommit> codeReviewCommits) {
    PatchSetApproval submitter = null;
    for (final CodeReviewCommit c : codeReviewCommits) {
      PatchSetApproval s = getSubmitter(reviewDb, c.patchsetId);
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
        try {
          rw.parseBody(c);
        } catch (IOException e) {
          log.warn("Cannot parse commit " + c.name(), e);
          continue;
        }
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

  public static boolean canMerge(final MergeSorter mergeSorter,
      final Repository repo, final boolean useContentMerge,
      final CodeReviewCommit mergeTip, final CodeReviewCommit toMerge)
      throws MergeException {
    if (hasMissingDependencies(mergeSorter, toMerge)) {
      return false;
    }

    final ThreeWayMerger m =
        newThreeWayMerger(repo, createDryRunInserter(), useContentMerge);
    try {
      return m.merge(new AnyObjectId[] {mergeTip, toMerge});
    } catch (IOException e) {
      if (e.getMessage().startsWith("Multiple merge bases for")) {
        return false;
      }
      throw new MergeException("Cannot merge " + toMerge.name(), e);
    }
  }

  public static boolean canFastForward(final MergeSorter mergeSorter,
      final CodeReviewCommit mergeTip, final RevWalk rw,
      final CodeReviewCommit toMerge) throws MergeException {
    if (hasMissingDependencies(mergeSorter, toMerge)) {
      return false;
    }

    try {
      return mergeTip == null || rw.isMergedInto(mergeTip, toMerge);
    } catch (IOException e) {
      throw new MergeException("Cannot fast-forward test during merge", e);
    }
  }

  public static boolean canCherryPick(final MergeSorter mergeSorter,
      final Repository repo, final boolean useContentMerge,
      final CodeReviewCommit mergeTip, final RevWalk rw,
      final CodeReviewCommit toMerge) throws MergeException {
    if (mergeTip == null) {
      // The branch is unborn. Fast-forward is possible.
      //
      return true;
    }

    if (toMerge.getParentCount() == 0) {
      // Refuse to merge a root commit into an existing branch,
      // we cannot obtain a delta for the cherry-pick to apply.
      //
      return false;
    }

    if (toMerge.getParentCount() == 1) {
      // If there is only one parent, a cherry-pick can be done by
      // taking the delta relative to that one parent and redoing
      // that on the current merge tip.
      //
      try {
        final ThreeWayMerger m =
            newThreeWayMerger(repo, createDryRunInserter(), useContentMerge);
        m.setBase(toMerge.getParent(0));
        return m.merge(mergeTip, toMerge);
      } catch (IOException e) {
        throw new MergeException("Cannot merge " + toMerge.name(), e);
      }
    }

    // There are multiple parents, so this is a merge commit. We
    // don't want to cherry-pick the merge as clients can't easily
    // rebase their history with that merge present and replaced
    // by an equivalent merge with a different first parent. So
    // instead behave as though MERGE_IF_NECESSARY was configured.
    //
    return canFastForward(mergeSorter, mergeTip, rw, toMerge)
        || canMerge(mergeSorter, repo, useContentMerge, mergeTip, toMerge);
  }

  public static boolean hasMissingDependencies(final MergeSorter mergeSorter,
      final CodeReviewCommit toMerge) throws MergeException {
    try {
      return !mergeSorter.sort(Collections.singleton(toMerge)).contains(toMerge);
    } catch (IOException e) {
      throw new MergeException("Branch head sorting failed", e);
    }
  }

  public static ObjectInserter createDryRunInserter() {
    return new ObjectInserter() {
      private final MutableObjectId buf = new MutableObjectId();
      private final static int LAST_BYTE = Constants.OBJECT_ID_LENGTH - 1;

      @Override
      public ObjectId insert(int objectType, long length, InputStream in)
          throws IOException {
        // create non-existing dummy ID
        buf.setByte(LAST_BYTE, buf.getByte(LAST_BYTE) + 1);
        return buf.copy();
      }

      @Override
      public PackParser newPackParser(InputStream in) throws IOException {
        throw new UnsupportedOperationException();
      }

      @Override
      public void flush() throws IOException {
        // Do nothing.
      }

      @Override
      public void release() {
        // Do nothing.
      }
    };
  }

  public static CodeReviewCommit mergeOneCommit(final ReviewDb reviewDb,
      final IdentifiedUser.GenericFactory identifiedUserFactory,
      final PersonIdent myIdent, final Repository repo, final RevWalk rw,
      final ObjectInserter inserter, final RevFlag canMergeFlag,
      final boolean useContentMerge, final Branch.NameKey destBranch,
      final CodeReviewCommit mergeTip, final CodeReviewCommit n)
      throws MergeException {
    final ThreeWayMerger m = newThreeWayMerger(repo, inserter, useContentMerge);
    try {
      if (m.merge(new AnyObjectId[] {mergeTip, n})) {
        return writeMergeCommit(reviewDb, identifiedUserFactory, myIdent, rw,
            inserter, canMergeFlag, destBranch, mergeTip, m.getResultTreeId(), n);
      } else {
        failed(rw, canMergeFlag, mergeTip, n, CommitMergeStatus.PATH_CONFLICT);
      }
    } catch (IOException e) {
      if (e.getMessage().startsWith("Multiple merge bases for")) {
        try {
          failed(rw, canMergeFlag, mergeTip, n,
              CommitMergeStatus.CRISS_CROSS_MERGE);
        } catch (IOException e2) {
          throw new MergeException("Cannot merge " + n.name(), e);
        }
      } else {
        throw new MergeException("Cannot merge " + n.name(), e);
      }
    }
    return mergeTip;
  }

  private static CodeReviewCommit failed(final RevWalk rw,
      final RevFlag canMergeFlag, final CodeReviewCommit mergeTip,
      final CodeReviewCommit n, final CommitMergeStatus failure)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    rw.resetRetain(canMergeFlag);
    rw.markStart(n);
    rw.markUninteresting(mergeTip);
    CodeReviewCommit failed;
    while ((failed = (CodeReviewCommit) rw.next()) != null) {
      failed.statusCode = failure;
    }
    return failed;
  }

  public static CodeReviewCommit writeMergeCommit(final ReviewDb reviewDb,
      final IdentifiedUser.GenericFactory identifiedUserFactory,
      final PersonIdent myIdent, final RevWalk rw,
      final ObjectInserter inserter, final RevFlag canMergeFlag,
      final Branch.NameKey destBranch, final CodeReviewCommit mergeTip,
      final ObjectId treeId, final CodeReviewCommit n) throws IOException,
      MissingObjectException, IncorrectObjectTypeException {
    final List<CodeReviewCommit> merged = new ArrayList<CodeReviewCommit>();
    rw.resetRetain(canMergeFlag);
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

    PersonIdent authorIdent =
        computeMergeCommitAuthor(reviewDb, identifiedUserFactory, myIdent, rw,
            merged);

    final CommitBuilder mergeCommit = new CommitBuilder();
    mergeCommit.setTreeId(treeId);
    mergeCommit.setParentIds(mergeTip, n);
    mergeCommit.setAuthor(authorIdent);
    mergeCommit.setCommitter(myIdent);
    mergeCommit.setMessage(msgbuf.toString());

    return (CodeReviewCommit) rw.parseCommit(commit(inserter, mergeCommit));
  }

  public static ThreeWayMerger newThreeWayMerger(final Repository repo,
      final ObjectInserter inserter, final boolean useContentMerge) {
    ThreeWayMerger m;
    if (useContentMerge) {
      // Settings for this project allow us to try and
      // automatically resolve conflicts within files if needed.
      // Use ResolveMerge and instruct to operate in core.
      m = MergeStrategy.RESOLVE.newMerger(repo, true);
    } else {
      // No auto conflict resolving allowed. If any of the
      // affected files was modified, merge will fail.
      m = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(repo);
    }
    m.setObjectInserter(new ObjectInserter.Filter() {
      @Override
      protected ObjectInserter delegate() {
        return inserter;
      }

      @Override
      public void flush() {
      }

      @Override
      public void release() {
      }
    });
    return m;
  }

  public static ObjectId commit(final ObjectInserter inserter,
      final CommitBuilder mergeCommit) throws IOException,
      UnsupportedEncodingException {
    ObjectId id = inserter.insert(mergeCommit);
    inserter.flush();
    return id;
  }

  public static PatchSetApproval markCleanMerges(final ReviewDb reviewDb,
      final RevWalk rw, final RevFlag canMergeFlag,
      final CodeReviewCommit mergeTip, final Set<RevCommit> alreadyAccepted)
      throws MergeException {
    if (mergeTip == null) {
      // If mergeTip is null here, branchTip was null, indicating a new branch
      // at the start of the merge process. We also elected to merge nothing,
      // probably due to missing dependencies. Nothing was cleanly merged.
      //
      return null;
    }

    try {
      PatchSetApproval submitApproval = null;

      rw.resetRetain(canMergeFlag);
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
          if (submitApproval == null) {
            submitApproval = getSubmitter(reviewDb, c.patchsetId);
          }
        }
      }

      return submitApproval;
    } catch (IOException e) {
      throw new MergeException("Cannot mark clean merges", e);
    }
  }
}
