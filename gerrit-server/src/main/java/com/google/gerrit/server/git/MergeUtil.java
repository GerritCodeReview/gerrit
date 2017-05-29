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
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSet.Id;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.strategy.CommitMergeStatus;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoMergeBaseException;
import org.eclipse.jgit.errors.NoMergeBaseException.MergeBaseFailureReason;
import org.eclipse.jgit.errors.RevisionSyntaxException;
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
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.merge.ThreeWayMergeStrategy;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods used during the merge process.
 *
 * <p><strong>Note:</strong> Unless otherwise specified, the methods in this class <strong>do
 * not</strong> flush {@link ObjectInserter}s. Callers that want to read back objects before
 * flushing should use {@link ObjectInserter#newReader()}. This is already the default behavior of
 * {@code BatchUpdate}.
 */
public class MergeUtil {
  private static final Logger log = LoggerFactory.getLogger(MergeUtil.class);

  static class PluggableCommitMessageGenerator {
    private final DynamicSet<ChangeMessageModifier> changeMessageModifiers;

    @Inject
    PluggableCommitMessageGenerator(DynamicSet<ChangeMessageModifier> changeMessageModifiers) {
      this.changeMessageModifiers = changeMessageModifiers;
    }

    public String generate(
        RevCommit original, RevCommit mergeTip, ChangeControl ctl, String current) {
      checkNotNull(original.getRawBuffer());
      if (mergeTip != null) {
        checkNotNull(mergeTip.getRawBuffer());
      }
      for (ChangeMessageModifier changeMessageModifier : changeMessageModifiers) {
        current =
            changeMessageModifier.onSubmit(current, original, mergeTip, ctl.getChange().getDest());
        checkNotNull(
            current,
            changeMessageModifier.getClass().getName()
                + ".OnSubmit returned null instead of new commit message");
      }
      return current;
    }
  }

  private static final String R_HEADS_MASTER = Constants.R_HEADS + Constants.MASTER;

  public static boolean useRecursiveMerge(Config cfg) {
    return cfg.getBoolean("core", null, "useRecursiveMerge", true);
  }

  public static ThreeWayMergeStrategy getMergeStrategy(Config cfg) {
    return useRecursiveMerge(cfg) ? MergeStrategy.RECURSIVE : MergeStrategy.RESOLVE;
  }

  public interface Factory {
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
  private final PluggableCommitMessageGenerator commitMessageGenerator;

  @AssistedInject
  MergeUtil(
      @GerritServerConfig Config serverConfig,
      Provider<ReviewDb> db,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      @CanonicalWebUrl @Nullable Provider<String> urlProvider,
      ApprovalsUtil approvalsUtil,
      PluggableCommitMessageGenerator commitMessageGenerator,
      @Assisted ProjectState project) {
    this(
        serverConfig,
        db,
        identifiedUserFactory,
        urlProvider,
        approvalsUtil,
        project,
        commitMessageGenerator,
        project.isUseContentMerge());
  }

  @AssistedInject
  MergeUtil(
      @GerritServerConfig Config serverConfig,
      Provider<ReviewDb> db,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      @CanonicalWebUrl @Nullable Provider<String> urlProvider,
      ApprovalsUtil approvalsUtil,
      @Assisted ProjectState project,
      PluggableCommitMessageGenerator commitMessageGenerator,
      @Assisted boolean useContentMerge) {
    this.db = db;
    this.identifiedUserFactory = identifiedUserFactory;
    this.urlProvider = urlProvider;
    this.approvalsUtil = approvalsUtil;
    this.project = project;
    this.useContentMerge = useContentMerge;
    this.useRecursiveMerge = useRecursiveMerge(serverConfig);
    this.commitMessageGenerator = commitMessageGenerator;
  }

  public CodeReviewCommit getFirstFastForward(
      final CodeReviewCommit mergeTip, final RevWalk rw, final List<CodeReviewCommit> toMerge)
      throws IntegrationException {
    for (final Iterator<CodeReviewCommit> i = toMerge.iterator(); i.hasNext(); ) {
      try {
        final CodeReviewCommit n = i.next();
        if (mergeTip == null || rw.isMergedInto(mergeTip, n)) {
          i.remove();
          return n;
        }
      } catch (IOException e) {
        throw new IntegrationException("Cannot fast-forward test during merge", e);
      }
    }
    return mergeTip;
  }

  public List<CodeReviewCommit> reduceToMinimalMerge(
      MergeSorter mergeSorter, Collection<CodeReviewCommit> toSort) throws IntegrationException {
    List<CodeReviewCommit> result = new ArrayList<>();
    try {
      result.addAll(mergeSorter.sort(toSort));
    } catch (IOException e) {
      throw new IntegrationException("Branch head sorting failed", e);
    }
    Collections.sort(result, CodeReviewCommit.ORDER);
    return result;
  }

  public CodeReviewCommit createCherryPickFromCommit(
      ObjectInserter inserter,
      Config repoConfig,
      RevCommit mergeTip,
      RevCommit originalCommit,
      PersonIdent cherryPickCommitterIdent,
      String commitMsg,
      CodeReviewRevWalk rw,
      int parentIndex,
      String strategy,
      boolean ignoreIdenticalTree)
      throws IOException, MergeIdenticalTreeException, MergeConflictException {
    String mergeStrategy =
        MoreObjects.firstNonNull(Strings.emptyToNull(strategy), mergeStrategyName());
    Merger merger = newMerger(inserter, repoConfig, mergeStrategy);
    if (merger instanceof ThreeWayMerger) {
      ((ThreeWayMerger) merger).setBase(originalCommit.getParent(parentIndex));
    }

    if (merger.merge(originalCommit, mergeTip)) {
      ObjectId tree = merger.getResultTreeId();
      if (tree.equals(mergeTip.getTree())
          && !ignoreIdenticalTree
          && !mergeStrategy.equals(MergeStrategy.THEIRS.getName())) {
        throw new MergeIdenticalTreeException("identical tree");
      }

      CommitBuilder mergeCommit = new CommitBuilder();
      mergeCommit.setTreeId(tree);
      mergeCommit.setParentId(mergeTip);
      mergeCommit.setAuthor(originalCommit.getAuthorIdent());
      mergeCommit.setCommitter(cherryPickCommitterIdent);
      mergeCommit.setMessage(commitMsg);
      return rw.parseCommit(inserter.insert(mergeCommit));
    }
    throw new MergeConflictException("merge conflict");
  }

  public static RevCommit createMergeCommit(
      ObjectInserter inserter,
      Config repoConfig,
      RevCommit mergeTip,
      RevCommit originalCommit,
      String mergeStrategy,
      PersonIdent committerIndent,
      String commitMsg,
      RevWalk rw)
      throws IOException, MergeIdenticalTreeException, MergeConflictException {

    if (!MergeStrategy.THEIRS.getName().equals(mergeStrategy)
        && rw.isMergedInto(originalCommit, mergeTip)) {
      throw new ChangeAlreadyMergedException(
          "'" + originalCommit.getName() + "' has already been merged");
    }

    Merger m = newMerger(inserter, repoConfig, mergeStrategy);
    if (m.merge(false, mergeTip, originalCommit)) {
      ObjectId tree = m.getResultTreeId();

      CommitBuilder mergeCommit = new CommitBuilder();
      mergeCommit.setTreeId(tree);
      mergeCommit.setParentIds(mergeTip, originalCommit);
      mergeCommit.setAuthor(committerIndent);
      mergeCommit.setCommitter(committerIndent);
      mergeCommit.setMessage(commitMsg);
      return rw.parseCommit(inserter.insert(mergeCommit));
    }
    List<String> conflicts = ImmutableList.of();
    if (m instanceof ResolveMerger) {
      conflicts = ((ResolveMerger) m).getUnmergedPaths();
    }
    throw new MergeConflictException(createConflictMessage(conflicts));
  }

  public static String createConflictMessage(List<String> conflicts) {
    StringBuilder sb = new StringBuilder("merge conflict(s)");
    for (String c : conflicts) {
      sb.append('\n' + c);
    }
    return sb.toString();
  }

  /**
   * Adds footers to existing commit message based on the state of the change.
   *
   * <p>This adds the following footers if they are missing:
   *
   * <ul>
   *   <li>Reviewed-on: <i>url</i>
   *   <li>Reviewed-by | Tested-by | <i>Other-Label-Name</i>: <i>reviewer</i>
   *   <li>Change-Id
   * </ul>
   *
   * @param n
   * @param ctl
   * @param psId
   * @return new message
   */
  private String createDetailedCommitMessage(RevCommit n, ChangeControl ctl, PatchSet.Id psId) {
    Change c = ctl.getChange();
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

    if (!contains(footers, FooterConstants.CHANGE_ID, c.getKey().get())) {
      msgbuf.append(FooterConstants.CHANGE_ID.getName());
      msgbuf.append(": ");
      msgbuf.append(c.getKey().get());
      msgbuf.append('\n');
    }

    final String siteUrl = urlProvider.get();
    if (siteUrl != null) {
      final String url = siteUrl + c.getId().get();
      if (!contains(footers, FooterConstants.REVIEWED_ON, url)) {
        msgbuf.append(FooterConstants.REVIEWED_ON.getName());
        msgbuf.append(": ");
        msgbuf.append(url);
        msgbuf.append('\n');
      }
    }

    PatchSetApproval submitAudit = null;

    for (final PatchSetApproval a : safeGetApprovals(ctl, psId)) {
      if (a.getValue() <= 0) {
        // Negative votes aren't counted.
        continue;
      }

      if (a.isLegacySubmit()) {
        // Submit is treated specially, below (becomes committer)
        //
        if (submitAudit == null || a.getGranted().compareTo(submitAudit.getGranted()) > 0) {
          submitAudit = a;
        }
        continue;
      }

      final Account acc = identifiedUserFactory.create(a.getAccountId()).getAccount();
      final StringBuilder identbuf = new StringBuilder();
      if (acc.getFullName() != null && acc.getFullName().length() > 0) {
        if (identbuf.length() > 0) {
          identbuf.append(' ');
        }
        identbuf.append(acc.getFullName());
      }
      if (acc.getPreferredEmail() != null && acc.getPreferredEmail().length() > 0) {
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

  public String createCommitMessageOnSubmit(CodeReviewCommit n, RevCommit mergeTip) {
    return createCommitMessageOnSubmit(n, mergeTip, n.getControl(), n.getPatchsetId());
  }

  /**
   * Creates a commit message for a change, which can be customized by plugins.
   *
   * <p>By default, adds footers to existing commit message based on the state of the change.
   * Plugins implementing {@link ChangeMessageModifier} can modify the resulting commit message
   * arbitrarily.
   *
   * @param n
   * @param mergeTip
   * @param ctl
   * @param id
   * @return new message
   */
  public String createCommitMessageOnSubmit(
      RevCommit n, RevCommit mergeTip, ChangeControl ctl, Id id) {
    return commitMessageGenerator.generate(
        n, mergeTip, ctl, createDetailedCommitMessage(n, ctl, id));
  }

  private static boolean isCodeReview(LabelId id) {
    return "Code-Review".equalsIgnoreCase(id.get());
  }

  private static boolean isVerified(LabelId id) {
    return "Verified".equalsIgnoreCase(id.get());
  }

  private Iterable<PatchSetApproval> safeGetApprovals(ChangeControl ctl, PatchSet.Id psId) {
    try {
      return approvalsUtil.byPatchSet(db.get(), ctl, psId);
    } catch (OrmException e) {
      log.error("Can't read approval records for " + psId, e);
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
      if (line.matches(FooterKey.SIGNED_OFF_BY) && email.equals(line.getEmailAddress())) {
        return true;
      }
    }
    return false;
  }

  public boolean canMerge(
      final MergeSorter mergeSorter,
      final Repository repo,
      final CodeReviewCommit mergeTip,
      final CodeReviewCommit toMerge)
      throws IntegrationException {
    if (hasMissingDependencies(mergeSorter, toMerge)) {
      return false;
    }

    try (ObjectInserter ins = new InMemoryInserter(repo)) {
      return newThreeWayMerger(ins, repo.getConfig()).merge(new AnyObjectId[] {mergeTip, toMerge});
    } catch (LargeObjectException e) {
      log.warn("Cannot merge due to LargeObjectException: " + toMerge.name());
      return false;
    } catch (NoMergeBaseException e) {
      return false;
    } catch (IOException e) {
      throw new IntegrationException("Cannot merge " + toMerge.name(), e);
    }
  }

  public boolean canFastForward(
      MergeSorter mergeSorter,
      CodeReviewCommit mergeTip,
      CodeReviewRevWalk rw,
      CodeReviewCommit toMerge)
      throws IntegrationException {
    if (hasMissingDependencies(mergeSorter, toMerge)) {
      return false;
    }

    try {
      return mergeTip == null
          || rw.isMergedInto(mergeTip, toMerge)
          || rw.isMergedInto(toMerge, mergeTip);
    } catch (IOException e) {
      throw new IntegrationException("Cannot fast-forward test during merge", e);
    }
  }

  public boolean canCherryPick(
      MergeSorter mergeSorter,
      Repository repo,
      CodeReviewCommit mergeTip,
      CodeReviewRevWalk rw,
      CodeReviewCommit toMerge)
      throws IntegrationException {
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
      try (ObjectInserter ins = new InMemoryInserter(repo)) {
        ThreeWayMerger m = newThreeWayMerger(ins, repo.getConfig());
        m.setBase(toMerge.getParent(0));
        return m.merge(mergeTip, toMerge);
      } catch (IOException e) {
        throw new IntegrationException(
            String.format(
                "Cannot merge commit %s with mergetip %s", toMerge.name(), mergeTip.name()),
            e);
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

  public boolean hasMissingDependencies(
      final MergeSorter mergeSorter, final CodeReviewCommit toMerge) throws IntegrationException {
    try {
      return !mergeSorter.sort(Collections.singleton(toMerge)).contains(toMerge);
    } catch (IOException e) {
      throw new IntegrationException("Branch head sorting failed", e);
    }
  }

  public CodeReviewCommit mergeOneCommit(
      PersonIdent author,
      PersonIdent committer,
      CodeReviewRevWalk rw,
      ObjectInserter inserter,
      Config repoConfig,
      Branch.NameKey destBranch,
      CodeReviewCommit mergeTip,
      CodeReviewCommit n)
      throws IntegrationException {
    ThreeWayMerger m = newThreeWayMerger(inserter, repoConfig);
    try {
      if (m.merge(new AnyObjectId[] {mergeTip, n})) {
        return writeMergeCommit(
            author, committer, rw, inserter, destBranch, mergeTip, m.getResultTreeId(), n);
      }
      failed(rw, mergeTip, n, CommitMergeStatus.PATH_CONFLICT);
    } catch (NoMergeBaseException e) {
      try {
        failed(rw, mergeTip, n, getCommitMergeStatus(e.getReason()));
      } catch (IOException e2) {
        throw new IntegrationException("Cannot merge " + n.name(), e);
      }
    } catch (IOException e) {
      throw new IntegrationException("Cannot merge " + n.name(), e);
    }
    return mergeTip;
  }

  private static CommitMergeStatus getCommitMergeStatus(MergeBaseFailureReason reason) {
    switch (reason) {
      case MULTIPLE_MERGE_BASES_NOT_SUPPORTED:
      case TOO_MANY_MERGE_BASES:
      default:
        return CommitMergeStatus.MANUAL_RECURSIVE_MERGE;
      case CONFLICTS_DURING_MERGE_BASE_CALCULATION:
        return CommitMergeStatus.PATH_CONFLICT;
    }
  }

  private static CodeReviewCommit failed(
      CodeReviewRevWalk rw,
      CodeReviewCommit mergeTip,
      CodeReviewCommit n,
      CommitMergeStatus failure)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    rw.reset();
    rw.markStart(n);
    rw.markUninteresting(mergeTip);
    CodeReviewCommit failed;
    while ((failed = rw.next()) != null) {
      failed.setStatusCode(failure);
    }
    return failed;
  }

  public CodeReviewCommit writeMergeCommit(
      PersonIdent author,
      PersonIdent committer,
      CodeReviewRevWalk rw,
      ObjectInserter inserter,
      Branch.NameKey destBranch,
      CodeReviewCommit mergeTip,
      ObjectId treeId,
      CodeReviewCommit n)
      throws IOException, MissingObjectException, IncorrectObjectTypeException {
    final List<CodeReviewCommit> merged = new ArrayList<>();
    rw.reset();
    rw.markStart(n);
    rw.markUninteresting(mergeTip);
    CodeReviewCommit crc;
    while ((crc = rw.next()) != null) {
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

    final CommitBuilder mergeCommit = new CommitBuilder();
    mergeCommit.setTreeId(treeId);
    mergeCommit.setParentIds(mergeTip, n);
    mergeCommit.setAuthor(author);
    mergeCommit.setCommitter(committer);
    mergeCommit.setMessage(msgbuf.toString());

    CodeReviewCommit mergeResult = rw.parseCommit(inserter.insert(mergeCommit));
    mergeResult.setControl(n.getControl());
    return mergeResult;
  }

  private String summarize(RevWalk rw, List<CodeReviewCommit> merged) throws IOException {
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
      return String.format("Merge changes from topic '%s'", Iterables.getFirst(topics, null));
    } else if (topics.size() > 1) {
      return String.format("Merge changes from topics '%s'", Joiner.on("', '").join(topics));
    } else {
      return String.format(
          "Merge changes %s%s",
          FluentIterable.from(merged)
              .limit(5)
              .transform(c -> c.change().getKey().abbreviate())
              .join(Joiner.on(',')),
          merged.size() > 5 ? ", ..." : "");
    }
  }

  public ThreeWayMerger newThreeWayMerger(ObjectInserter inserter, Config repoConfig) {
    return newThreeWayMerger(inserter, repoConfig, mergeStrategyName());
  }

  public String mergeStrategyName() {
    return mergeStrategyName(useContentMerge, useRecursiveMerge);
  }

  public static String mergeStrategyName(boolean useContentMerge, boolean useRecursiveMerge) {
    if (useContentMerge) {
      // Settings for this project allow us to try and automatically resolve
      // conflicts within files if needed. Use either the old resolve merger or
      // new recursive merger, and instruct to operate in core.
      if (useRecursiveMerge) {
        return MergeStrategy.RECURSIVE.getName();
      }
      return MergeStrategy.RESOLVE.getName();
    }
    // No auto conflict resolving allowed. If any of the
    // affected files was modified, merge will fail.
    return MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.getName();
  }

  public static ThreeWayMerger newThreeWayMerger(
      ObjectInserter inserter, Config repoConfig, String strategyName) {
    Merger m = newMerger(inserter, repoConfig, strategyName);
    checkArgument(
        m instanceof ThreeWayMerger,
        "merge strategy %s does not support three-way merging",
        strategyName);
    return (ThreeWayMerger) m;
  }

  public static Merger newMerger(ObjectInserter inserter, Config repoConfig, String strategyName) {
    MergeStrategy strategy = MergeStrategy.get(strategyName);
    checkArgument(strategy != null, "invalid merge strategy: %s", strategyName);
    return strategy.newMerger(
        new ObjectInserter.Filter() {
          @Override
          protected ObjectInserter delegate() {
            return inserter;
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        },
        repoConfig);
  }

  public void markCleanMerges(
      final RevWalk rw,
      final RevFlag canMergeFlag,
      final CodeReviewCommit mergeTip,
      final Set<RevCommit> alreadyAccepted)
      throws IntegrationException {
    if (mergeTip == null) {
      // If mergeTip is null here, branchTip was null, indicating a new branch
      // at the start of the merge process. We also elected to merge nothing,
      // probably due to missing dependencies. Nothing was cleanly merged.
      //
      return;
    }

    try {
      rw.resetRetain(canMergeFlag);
      rw.sort(RevSort.TOPO);
      rw.sort(RevSort.REVERSE, true);
      rw.markStart(mergeTip);
      for (RevCommit c : alreadyAccepted) {
        // If branch was not created by this submit.
        if (!Objects.equals(c, mergeTip)) {
          rw.markUninteresting(c);
        }
      }

      CodeReviewCommit c;
      while ((c = (CodeReviewCommit) rw.next()) != null) {
        if (c.getPatchsetId() != null && c.getStatusCode() == null) {
          c.setStatusCode(CommitMergeStatus.CLEAN_MERGE);
        }
      }
    } catch (IOException e) {
      throw new IntegrationException("Cannot mark clean merges", e);
    }
  }

  public Set<Change.Id> findUnmergedChanges(
      Set<Change.Id> expected,
      CodeReviewRevWalk rw,
      RevFlag canMergeFlag,
      CodeReviewCommit oldTip,
      CodeReviewCommit mergeTip,
      Iterable<Change.Id> alreadyMerged)
      throws IntegrationException {
    if (mergeTip == null) {
      return expected;
    }

    try {
      Set<Change.Id> found = Sets.newHashSetWithExpectedSize(expected.size());
      Iterables.addAll(found, alreadyMerged);
      rw.resetRetain(canMergeFlag);
      rw.sort(RevSort.TOPO);
      rw.markStart(mergeTip);
      if (oldTip != null) {
        rw.markUninteresting(oldTip);
      }

      CodeReviewCommit c;
      while ((c = rw.next()) != null) {
        if (c.getPatchsetId() == null) {
          continue;
        }
        Change.Id id = c.getPatchsetId().getParentKey();
        if (!expected.contains(id)) {
          continue;
        }
        found.add(id);
        if (found.size() == expected.size()) {
          return Collections.emptySet();
        }
      }
      return Sets.difference(expected, found);
    } catch (IOException e) {
      throw new IntegrationException("Cannot check if changes were merged", e);
    }
  }

  public static CodeReviewCommit findAnyMergedInto(
      CodeReviewRevWalk rw, Iterable<CodeReviewCommit> commits, CodeReviewCommit tip)
      throws IOException {
    for (CodeReviewCommit c : commits) {
      // TODO(dborowitz): Seems like this could get expensive for many patch
      // sets. Is there a more efficient implementation?
      if (rw.isMergedInto(c, tip)) {
        return c;
      }
    }
    return null;
  }

  public static RevCommit resolveCommit(Repository repo, RevWalk rw, String str)
      throws BadRequestException, ResourceNotFoundException, IOException {
    try {
      ObjectId commitId = repo.resolve(str);
      if (commitId == null) {
        throw new BadRequestException("Cannot resolve '" + str + "' to a commit");
      }
      return rw.parseCommit(commitId);
    } catch (AmbiguousObjectException | IncorrectObjectTypeException | RevisionSyntaxException e) {
      throw new BadRequestException(e.getMessage());
    } catch (MissingObjectException e) {
      throw new ResourceNotFoundException(e.getMessage());
    }
  }
}
