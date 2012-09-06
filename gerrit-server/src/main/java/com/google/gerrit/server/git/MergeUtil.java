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

import com.google.gerrit.reviewdb.client.ApprovalCategory;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ThreeWayMerger;
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
import java.security.MessageDigest;
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
import java.util.UUID;

public class MergeUtil {
  private static final Logger log = LoggerFactory.getLogger(MergeUtil.class);

  private static final String R_HEADS_MASTER =
      Constants.R_HEADS + Constants.MASTER;

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
      @Override
      public ObjectId insert(int objectType, long length, InputStream in)
          throws IOException {
        return createRandomObjectId();
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

  private static ObjectId createRandomObjectId() {
    final MessageDigest md = Constants.newMessageDigest();
    md.update(UUID.randomUUID().toString().getBytes());
    return ObjectId.fromRaw(md.digest());
  }

  public static CodeReviewCommit mergeOneCommit(final ReviewDb reviewDb,
      final IdentifiedUser.GenericFactory identifiedUserFactory,
      final PersonIdent myIdent, final Repository repo, final RevWalk rw,
      final ObjectInserter inserter, final boolean useContentMerge,
      final Branch.NameKey destBranch, final CodeReviewCommit mergeTip,
      final CodeReviewCommit n) throws MergeException {
    final ThreeWayMerger m = newThreeWayMerger(repo, inserter, useContentMerge);
    try {
      if (m.merge(new AnyObjectId[] {mergeTip, n})) {
        return writeMergeCommit(reviewDb, identifiedUserFactory, myIdent, rw,
            inserter, destBranch, mergeTip, m.getResultTreeId(), n);
      } else {
        failed(rw, mergeTip, n, CommitMergeStatus.PATH_CONFLICT);
      }
    } catch (IOException e) {
      if (e.getMessage().startsWith("Multiple merge bases for")) {
        try {
          failed(rw, mergeTip, n, CommitMergeStatus.CRISS_CROSS_MERGE);
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
      final CodeReviewCommit mergeTip, final CodeReviewCommit n,
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

  public static CodeReviewCommit writeMergeCommit(final ReviewDb reviewDb,
      final IdentifiedUser.GenericFactory identifiedUserFactory,
      final PersonIdent myIdent, final RevWalk rw,
      final ObjectInserter inserter, final Branch.NameKey destBranch,
      final CodeReviewCommit mergeTip, final ObjectId treeId,
      final CodeReviewCommit n) throws IOException, MissingObjectException,
      IncorrectObjectTypeException {
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
