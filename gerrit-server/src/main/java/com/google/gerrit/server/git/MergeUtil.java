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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoMergeBaseException;
import org.eclipse.jgit.errors.NoMergeBaseException.MergeBaseFailureReason;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.Merger;
import org.eclipse.jgit.merge.ThreeWayMergeStrategy;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

public class MergeUtil {
  private static final Logger log = LoggerFactory.getLogger(MergeUtil.class);
  private static final String R_HEADS_MASTER =
      Constants.R_HEADS + Constants.MASTER;

  public static boolean useRecursiveMerge(Config cfg) {
    return cfg.getBoolean("core", null, "useRecursiveMerge", true);
  }

  public static ThreeWayMergeStrategy getMergeStrategy(Config cfg) {
    return useRecursiveMerge(cfg)
        ? MergeStrategy.RECURSIVE
        : MergeStrategy.RESOLVE;
  }

  public static interface Factory {
    MergeUtil create(ProjectState project);
    MergeUtil create(ProjectState project, boolean useContentMerge);
  }

  private final Provider<ReviewDb> db;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final Provider<String> urlProvider;
  private final ApprovalsUtil approvalsUtil;
  private final ProjectState project;
  private final boolean useContentMerge;
  private final boolean useRecursiveMerge;

  @AssistedInject
  MergeUtil(@GerritServerConfig Config serverConfig,
      final Provider<ReviewDb> db,
      final IdentifiedUser.GenericFactory identifiedUserFactory,
      @CanonicalWebUrl @Nullable final Provider<String> urlProvider,
      final ApprovalsUtil approvalsUtil,
      @Assisted final ProjectState project) {
    this(serverConfig, db, identifiedUserFactory, urlProvider, approvalsUtil,
        project, project.isUseContentMerge());
  }

  @AssistedInject
  MergeUtil(@GerritServerConfig Config serverConfig,
      final Provider<ReviewDb> db,
      final IdentifiedUser.GenericFactory identifiedUserFactory,
      @CanonicalWebUrl @Nullable final Provider<String> urlProvider,
      final ApprovalsUtil approvalsUtil,
      @Assisted final ProjectState project,
      @Assisted boolean useContentMerge) {
    this.db = db;
    this.identifiedUserFactory = identifiedUserFactory;
    this.urlProvider = urlProvider;
    this.approvalsUtil = approvalsUtil;
    this.project = project;
    this.useContentMerge = useContentMerge;
    this.useRecursiveMerge = useRecursiveMerge(serverConfig);
  }

  public CodeReviewCommit getFirstFastForward(
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

  public List<CodeReviewCommit> reduceToMinimalMerge(MergeSorter mergeSorter,
      Collection<CodeReviewCommit> toSort) throws MergeException {
    List<CodeReviewCommit> result = new ArrayList<>();
    try {
      result.addAll(mergeSorter.sort(toSort));
    } catch (IOException e) {
      throw new MergeException("Branch head sorting failed", e);
    }
    Collections.sort(result, CodeReviewCommit.ORDER);
    return result;
  }

  public PatchSetApproval getSubmitter(CodeReviewCommit c) {
    return approvalsUtil.getSubmitter(db.get(), c.notes(), c.getPatchsetId());
  }

  public RevCommit createCherryPickFromCommit(Repository repo,
      ObjectInserter inserter, RevCommit mergeTip, RevCommit originalCommit,
      PersonIdent cherryPickCommitterIdent, String commitMsg, RevWalk rw)
      throws MissingObjectException, IncorrectObjectTypeException, IOException,
      MergeIdenticalTreeException, MergeConflictException {

    final ThreeWayMerger m = newThreeWayMerger(repo, inserter);

    m.setBase(originalCommit.getParent(0));
    if (m.merge(mergeTip, originalCommit)) {
      ObjectId tree = m.getResultTreeId();
      if (tree.equals(mergeTip.getTree())) {
        throw new MergeIdenticalTreeException("identical tree");
      }

      CommitBuilder mergeCommit = new CommitBuilder();
      mergeCommit.setTreeId(tree);
      mergeCommit.setParentId(mergeTip);
      mergeCommit.setAuthor(originalCommit.getAuthorIdent());
      mergeCommit.setCommitter(cherryPickCommitterIdent);
      mergeCommit.setMessage(commitMsg);
      return rw.parseCommit(commit(inserter, mergeCommit));
    } else {
      throw new MergeConflictException("merge conflict");
    }
  }

  public String createCherryPickCommitMessage(final CodeReviewCommit n) {
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

    if (!contains(footers, FooterConstants.CHANGE_ID, n.change().getKey().get())) {
      msgbuf.append(FooterConstants.CHANGE_ID.getName());
      msgbuf.append(": ");
      msgbuf.append(n.change().getKey().get());
      msgbuf.append('\n');
    }

    final String siteUrl = urlProvider.get();
    if (siteUrl != null) {
      final String url = siteUrl + n.getPatchsetId().getParentKey().get();
      if (!contains(footers, FooterConstants.REVIEWED_ON, url)) {
        msgbuf.append(FooterConstants.REVIEWED_ON.getName());
        msgbuf.append(": ");
        msgbuf.append(url);
        msgbuf.append('\n');
      }
    }

    PatchSetApproval submitAudit = null;

    for (final PatchSetApproval a : safeGetApprovals(n)) {
      if (a.getValue() <= 0) {
        // Negative votes aren't counted.
        continue;
      }

      if (a.isSubmit()) {
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
      if (isCodeReview(a.getLabelId())) {
        tag = "Reviewed-by";
      } else if (isVerified(a.getLabelId())) {
        tag = "Tested-by";
      } else {
        final LabelType lt = project.getLabelTypes().byLabel(a.getLabelId());
        if (lt == null) {
          continue;
        }
        tag = lt.getName();
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

  private static boolean isCodeReview(LabelId id) {
    return "Code-Review".equalsIgnoreCase(id.get());
  }

  private static boolean isVerified(LabelId id) {
    return "Verified".equalsIgnoreCase(id.get());
  }

  private Iterable<PatchSetApproval> safeGetApprovals(CodeReviewCommit n) {
    try {
      return approvalsUtil.byPatchSet(db.get(), n.getControl(), n.getPatchsetId());
    } catch (OrmException e) {
      log.error("Can't read approval records for " + n.getPatchsetId(), e);
      return Collections.emptyList();
    }
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

  public PersonIdent computeMergeCommitAuthor(final PersonIdent myIdent,
      final RevWalk rw, final List<CodeReviewCommit> codeReviewCommits) {
    PatchSetApproval submitter = null;
    for (final CodeReviewCommit c : codeReviewCommits) {
      PatchSetApproval s = getSubmitter(c);
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
      Set<String> emails = new HashSet<>();
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
      if (emails.size() == 1 && who.hasEmailAddress(emails.iterator().next())) {
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

  public boolean canMerge(final MergeSorter mergeSorter,
      final Repository repo, final CodeReviewCommit mergeTip,
      final CodeReviewCommit toMerge)
      throws MergeException {
    if (hasMissingDependencies(mergeSorter, toMerge)) {
      return false;
    }

    ThreeWayMerger m = newThreeWayMerger(repo, createDryRunInserter(repo));
    try {
      return m.merge(new AnyObjectId[] {mergeTip, toMerge});
    } catch (LargeObjectException e) {
      log.warn("Cannot merge due to LargeObjectException: " + toMerge.name());
      return false;
    } catch (NoMergeBaseException e) {
      return false;
    } catch (IOException e) {
      throw new MergeException("Cannot merge " + toMerge.name(), e);
    }
  }

  public boolean canFastForward(final MergeSorter mergeSorter,
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

  public boolean canCherryPick(final MergeSorter mergeSorter,
      final Repository repo, final CodeReviewCommit mergeTip, final RevWalk rw,
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
        ThreeWayMerger m = newThreeWayMerger(repo, createDryRunInserter(repo));
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
        || canMerge(mergeSorter, repo, mergeTip, toMerge);
  }

  public boolean hasMissingDependencies(final MergeSorter mergeSorter,
      final CodeReviewCommit toMerge) throws MergeException {
    try {
      return !mergeSorter.sort(Collections.singleton(toMerge)).contains(toMerge);
    } catch (IOException e) {
      throw new MergeException("Branch head sorting failed", e);
    }
  }

  public static ObjectInserter createDryRunInserter(Repository db) {
    final ObjectInserter delegate = db.newObjectInserter();
    return new ObjectInserter.Filter() {
      @Override
      protected ObjectInserter delegate() {
        return delegate;
      }
      @Override
      public PackParser newPackParser(InputStream in) throws IOException {
        throw new UnsupportedOperationException();
      }
      @Override
      public void flush() throws IOException {
        // Do nothing.
      }
    };
  }

  public CodeReviewCommit mergeOneCommit(final PersonIdent myIdent,
      final Repository repo, final RevWalk rw, final ObjectInserter inserter,
      final RevFlag canMergeFlag, final Branch.NameKey destBranch,
      final CodeReviewCommit mergeTip, final CodeReviewCommit n)
      throws MergeException {
    final ThreeWayMerger m = newThreeWayMerger(repo, inserter);
    try {
      if (m.merge(new AnyObjectId[] {mergeTip, n})) {
        return writeMergeCommit(myIdent, rw, inserter, canMergeFlag, destBranch,
            mergeTip, m.getResultTreeId(), n);
      } else {
        failed(rw, canMergeFlag, mergeTip, n, CommitMergeStatus.PATH_CONFLICT);
      }
    } catch (NoMergeBaseException e) {
      try {
        failed(rw, canMergeFlag, mergeTip, n,
            getCommitMergeStatus(e.getReason()));
      } catch (IOException e2) {
        throw new MergeException("Cannot merge " + n.name(), e);
      }
    } catch (IOException e) {
      throw new MergeException("Cannot merge " + n.name(), e);
    }
    return mergeTip;
  }

  private static CommitMergeStatus getCommitMergeStatus(
      MergeBaseFailureReason reason) {
    switch (reason) {
      case MULTIPLE_MERGE_BASES_NOT_SUPPORTED:
      case TOO_MANY_MERGE_BASES:
      default:
        return CommitMergeStatus.MANUAL_RECURSIVE_MERGE;
      case CONFLICTS_DURING_MERGE_BASE_CALCULATION:
        return CommitMergeStatus.PATH_CONFLICT;
    }
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
      failed.setStatusCode(failure);
    }
    return failed;
  }

  public CodeReviewCommit writeMergeCommit(final PersonIdent myIdent,
      final RevWalk rw, final ObjectInserter inserter,
      final RevFlag canMergeFlag, final Branch.NameKey destBranch,
      final CodeReviewCommit mergeTip, final ObjectId treeId,
      final CodeReviewCommit n) throws IOException,
      MissingObjectException, IncorrectObjectTypeException {
    final List<CodeReviewCommit> merged = new ArrayList<>();
    rw.resetRetain(canMergeFlag);
    rw.markStart(n);
    rw.markUninteresting(mergeTip);
    for (final RevCommit c : rw) {
      final CodeReviewCommit crc = (CodeReviewCommit) c;
      if (crc.getPatchsetId() != null) {
        merged.add(crc);
      }
    }

    StringBuilder msgbuf = new StringBuilder().append(summarize(rw, merged));
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

    PersonIdent authorIdent = computeMergeCommitAuthor(myIdent, rw, merged);

    final CommitBuilder mergeCommit = new CommitBuilder();
    mergeCommit.setTreeId(treeId);
    mergeCommit.setParentIds(mergeTip, n);
    mergeCommit.setAuthor(authorIdent);
    mergeCommit.setCommitter(myIdent);
    mergeCommit.setMessage(msgbuf.toString());

    CodeReviewCommit mergeResult =
        (CodeReviewCommit) rw.parseCommit(commit(inserter, mergeCommit));
    mergeResult.setControl(n.getControl());
    return mergeResult;
  }

  private String summarize(RevWalk rw, List<CodeReviewCommit> merged)
      throws IOException {
    if (merged.size() == 1) {
      CodeReviewCommit c = merged.get(0);
      rw.parseBody(c);
      return String.format("Merge \"%s\"", c.getShortMessage());
    }

    LinkedHashSet<String> topics = new LinkedHashSet<>(4);
    for (CodeReviewCommit c : merged) {
      if (!Strings.isNullOrEmpty(c.change().getTopic())) {
        topics.add(c.change().getTopic());
      }
    }

    if (topics.size() == 1) {
      return String.format("Merge changes from topic '%s'",
          Iterables.getFirst(topics, null));
    } else if (topics.size() > 1) {
      return String.format("Merge changes from topics '%s'",
          Joiner.on("', '").join(topics));
    } else {
      return String.format("Merge changes %s%s",
          Joiner.on(',').join(Iterables.transform(
              Iterables.limit(merged, 5),
              new Function<CodeReviewCommit, String>() {
                @Override
                public String apply(CodeReviewCommit in) {
                  return in.change().getKey().abbreviate();
                }
              })),
          merged.size() > 5 ? ", ..." : "");
    }
  }

  public ThreeWayMerger newThreeWayMerger(final Repository repo,
      final ObjectInserter inserter) {
    return newThreeWayMerger(repo, inserter, mergeStrategyName());
  }

  public String mergeStrategyName() {
    return mergeStrategyName(useContentMerge, useRecursiveMerge);
  }

  public static String mergeStrategyName(boolean useContentMerge,
      boolean useRecursiveMerge) {
    if (useContentMerge) {
      // Settings for this project allow us to try and automatically resolve
      // conflicts within files if needed. Use either the old resolve merger or
      // new recursive merger, and instruct to operate in core.
      if (useRecursiveMerge) {
        return MergeStrategy.RECURSIVE.getName();
      } else {
        return MergeStrategy.RESOLVE.getName();
      }
    } else {
      // No auto conflict resolving allowed. If any of the
      // affected files was modified, merge will fail.
      return MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.getName();
    }
  }

  public static ThreeWayMerger newThreeWayMerger(Repository repo,
      final ObjectInserter inserter, String strategyName) {
    MergeStrategy strategy = MergeStrategy.get(strategyName);
    checkArgument(strategy != null, "invalid merge strategy: %s", strategyName);
    Merger m = strategy.newMerger(repo, true);
    checkArgument(m instanceof ThreeWayMerger,
        "merge strategy %s does not support three-way merging", strategyName);
    m.setObjectInserter(new ObjectInserter.Filter() {
      @Override
      protected ObjectInserter delegate() {
        return inserter;
      }

      @Override
      public void flush() {
      }

      @Override
      public void close() {
      }
    });
    return (ThreeWayMerger) m;
  }

  public ObjectId commit(final ObjectInserter inserter,
      final CommitBuilder mergeCommit) throws IOException,
      UnsupportedEncodingException {
    ObjectId id = inserter.insert(mergeCommit);
    inserter.flush();
    return id;
  }

  public PatchSetApproval markCleanMerges(final RevWalk rw,
      final RevFlag canMergeFlag, final CodeReviewCommit mergeTip,
      final Set<RevCommit> alreadyAccepted) throws MergeException {
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
        if (c.getPatchsetId() != null) {
          c.setStatusCode(CommitMergeStatus.CLEAN_MERGE);
          if (submitApproval == null) {
            submitApproval = getSubmitter(c);
          }
        }
      }

      return submitApproval;
    } catch (IOException e) {
      throw new MergeException("Cannot mark clean merges", e);
    }
  }
}
