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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSet.Id;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.UrlFormatter;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.submit.ChangeAlreadyMergedException;
import com.google.gerrit.server.submit.CommitMergeStatus;
import com.google.gerrit.server.submit.IntegrationException;
import com.google.gerrit.server.submit.MergeIdenticalTreeException;
import com.google.gerrit.server.submit.MergeSorter;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.diff.Sequence;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
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
import org.eclipse.jgit.merge.MergeFormatter;
import org.eclipse.jgit.merge.MergeResult;
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
import org.eclipse.jgit.util.TemporaryBuffer;

/**
 * Utility methods used during the merge process.
 *
 * <p><strong>Note:</strong> Unless otherwise specified, the methods in this class <strong>do
 * not</strong> flush {@link ObjectInserter}s. Callers that want to read back objects before
 * flushing should use {@link ObjectInserter#newReader()}. This is already the default behavior of
 * {@code BatchUpdate}.
 */
public class MergeUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static class PluggableCommitMessageGenerator {
    private final DynamicSet<ChangeMessageModifier> changeMessageModifiers;

    @Inject
    PluggableCommitMessageGenerator(DynamicSet<ChangeMessageModifier> changeMessageModifiers) {
      this.changeMessageModifiers = changeMessageModifiers;
    }

    public String generate(
        RevCommit original, RevCommit mergeTip, Branch.NameKey dest, String current) {
      requireNonNull(original.getRawBuffer());
      if (mergeTip != null) {
        requireNonNull(mergeTip.getRawBuffer());
      }
      for (ChangeMessageModifier changeMessageModifier : changeMessageModifiers) {
        current = changeMessageModifier.onSubmit(current, original, mergeTip, dest);
        requireNonNull(
            current,
            () ->
                String.format(
                    "%s.OnSubmit returned null instead of new commit message",
                    changeMessageModifier.getClass().getName()));
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
  private final UrlFormatter urlFormatter;
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
      UrlFormatter urlFormatter,
      ApprovalsUtil approvalsUtil,
      PluggableCommitMessageGenerator commitMessageGenerator,
      @Assisted ProjectState project) {
    this(
        serverConfig,
        db,
        identifiedUserFactory,
        urlFormatter,
        approvalsUtil,
        project,
        commitMessageGenerator,
        project.is(BooleanProjectConfig.USE_CONTENT_MERGE));
  }

  @AssistedInject
  MergeUtil(
      @GerritServerConfig Config serverConfig,
      Provider<ReviewDb> db,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      UrlFormatter urlFormatter,
      ApprovalsUtil approvalsUtil,
      @Assisted ProjectState project,
      PluggableCommitMessageGenerator commitMessageGenerator,
      @Assisted boolean useContentMerge) {
    this.db = db;
    this.identifiedUserFactory = identifiedUserFactory;
    this.urlFormatter = urlFormatter;
    this.approvalsUtil = approvalsUtil;
    this.project = project;
    this.useContentMerge = useContentMerge;
    this.useRecursiveMerge = useRecursiveMerge(serverConfig);
    this.commitMessageGenerator = commitMessageGenerator;
  }

  public CodeReviewCommit getFirstFastForward(
      CodeReviewCommit mergeTip, RevWalk rw, List<CodeReviewCommit> toMerge)
      throws IntegrationException {
    for (Iterator<CodeReviewCommit> i = toMerge.iterator(); i.hasNext(); ) {
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
    } catch (IOException | OrmException e) {
      throw new IntegrationException("Branch head sorting failed", e);
    }
    result.sort(CodeReviewCommit.ORDER);
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
      boolean ignoreIdenticalTree,
      boolean allowConflicts)
      throws MissingObjectException, IncorrectObjectTypeException, IOException,
          MergeIdenticalTreeException, MergeConflictException, MethodNotAllowedException {

    ThreeWayMerger m = newThreeWayMerger(inserter, repoConfig);
    m.setBase(originalCommit.getParent(parentIndex));

    DirCache dc = DirCache.newInCore();
    if (allowConflicts && m instanceof ResolveMerger) {
      // The DirCache must be set on ResolveMerger before calling
      // ResolveMerger#merge(AnyObjectId...) otherwise the entries in DirCache don't get populated.
      ((ResolveMerger) m).setDirCache(dc);
    }

    ObjectId tree;
    ImmutableSet<String> filesWithGitConflicts;
    if (m.merge(mergeTip, originalCommit)) {
      filesWithGitConflicts = null;
      tree = m.getResultTreeId();
      if (tree.equals(mergeTip.getTree()) && !ignoreIdenticalTree) {
        throw new MergeIdenticalTreeException("identical tree");
      }
    } else {
      if (!allowConflicts) {
        throw new MergeConflictException("merge conflict");
      }

      if (!useContentMerge) {
        // If content merge is disabled we don't have a ResolveMerger and hence cannot merge with
        // conflict markers.
        throw new MethodNotAllowedException(
            "Cherry-pick with allow conflicts requires that content merge is enabled.");
      }

      // For merging with conflict markers we need a ResolveMerger, double-check that we have one.
      checkState(m instanceof ResolveMerger, "allow conflicts is not supported");
      Map<String, MergeResult<? extends Sequence>> mergeResults =
          ((ResolveMerger) m).getMergeResults();

      filesWithGitConflicts =
          mergeResults
              .entrySet()
              .stream()
              .filter(e -> e.getValue().containsConflicts())
              .map(Map.Entry::getKey)
              .collect(toImmutableSet());

      tree =
          mergeWithConflicts(
              rw, inserter, dc, "HEAD", mergeTip, "CHANGE", originalCommit, mergeResults);
    }

    CommitBuilder cherryPickCommit = new CommitBuilder();
    cherryPickCommit.setTreeId(tree);
    cherryPickCommit.setParentId(mergeTip);
    cherryPickCommit.setAuthor(originalCommit.getAuthorIdent());
    cherryPickCommit.setCommitter(cherryPickCommitterIdent);
    cherryPickCommit.setMessage(commitMsg);
    matchAuthorToCommitterDate(project, cherryPickCommit);
    CodeReviewCommit commit = rw.parseCommit(inserter.insert(cherryPickCommit));
    commit.setFilesWithGitConflicts(filesWithGitConflicts);
    return commit;
  }

  public static ObjectId mergeWithConflicts(
      RevWalk rw,
      ObjectInserter ins,
      DirCache dc,
      String oursName,
      RevCommit ours,
      String theirsName,
      RevCommit theirs,
      Map<String, MergeResult<? extends Sequence>> mergeResults)
      throws IOException {
    rw.parseBody(ours);
    rw.parseBody(theirs);
    String oursMsg = ours.getShortMessage();
    String theirsMsg = theirs.getShortMessage();

    int nameLength = Math.max(oursName.length(), theirsName.length());
    String oursNameFormatted =
        String.format(
            "%0$-" + nameLength + "s (%s %s)",
            oursName,
            ours.abbreviate(6).name(),
            oursMsg.substring(0, Math.min(oursMsg.length(), 60)));
    String theirsNameFormatted =
        String.format(
            "%0$-" + nameLength + "s (%s %s)",
            theirsName,
            theirs.abbreviate(6).name(),
            theirsMsg.substring(0, Math.min(theirsMsg.length(), 60)));

    MergeFormatter fmt = new MergeFormatter();
    Map<String, ObjectId> resolved = new HashMap<>();
    for (Map.Entry<String, MergeResult<? extends Sequence>> entry : mergeResults.entrySet()) {
      MergeResult<? extends Sequence> p = entry.getValue();
      try (TemporaryBuffer buf = new TemporaryBuffer.LocalFile(null, 10 * 1024 * 1024)) {
        fmt.formatMerge(buf, p, "BASE", oursNameFormatted, theirsNameFormatted, UTF_8.name());
        buf.close();

        try (InputStream in = buf.openInputStream()) {
          resolved.put(entry.getKey(), ins.insert(Constants.OBJ_BLOB, buf.length(), in));
        }
      }
    }

    DirCacheBuilder builder = dc.builder();
    int cnt = dc.getEntryCount();
    for (int i = 0; i < cnt; ) {
      DirCacheEntry entry = dc.getEntry(i);
      if (entry.getStage() == 0) {
        builder.add(entry);
        i++;
        continue;
      }

      int next = dc.nextEntry(i);
      String path = entry.getPathString();
      DirCacheEntry res = new DirCacheEntry(path);
      if (resolved.containsKey(path)) {
        // For a file with content merge conflict that we produced a result
        // above on, collapse the file down to a single stage 0 with just
        // the blob content, and a randomly selected mode (the lowest stage,
        // which should be the merge base, or ours).
        res.setFileMode(entry.getFileMode());
        res.setObjectId(resolved.get(path));

      } else if (next == i + 1) {
        // If there is exactly one stage present, shouldn't be a conflict...
        res.setFileMode(entry.getFileMode());
        res.setObjectId(entry.getObjectId());

      } else if (next == i + 2) {
        // Two stages suggests a delete/modify conflict. Pick the higher
        // stage as the automatic result.
        entry = dc.getEntry(i + 1);
        res.setFileMode(entry.getFileMode());
        res.setObjectId(entry.getObjectId());

      } else {
        // 3 stage conflict, no resolve above
        // Punt on the 3-stage conflict and show the base, for now.
        res.setFileMode(entry.getFileMode());
        res.setObjectId(entry.getObjectId());
      }
      builder.add(res);
      i = next;
    }
    builder.finish();
    return dc.writeTree(ins);
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
      sb.append('\n').append(c);
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
   * @param notes
   * @param psId
   * @return new message
   */
  private String createDetailedCommitMessage(RevCommit n, ChangeNotes notes, PatchSet.Id psId) {
    Change c = notes.getChange();
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

    Optional<String> url = urlFormatter.getChangeViewUrl(null, c.getId());
    if (url.isPresent()) {
      if (!contains(footers, FooterConstants.REVIEWED_ON, url.get())) {
        msgbuf
            .append(FooterConstants.REVIEWED_ON.getName())
            .append(": ")
            .append(url.get())
            .append('\n');
      }
    }
    PatchSetApproval submitAudit = null;

    for (PatchSetApproval a : safeGetApprovals(notes, psId)) {
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
    return createCommitMessageOnSubmit(n, mergeTip, n.notes(), n.getPatchsetId());
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
   * @param notes
   * @param id
   * @return new message
   */
  public String createCommitMessageOnSubmit(
      RevCommit n, RevCommit mergeTip, ChangeNotes notes, Id id) {
    return commitMessageGenerator.generate(
        n, mergeTip, notes.getChange().getDest(), createDetailedCommitMessage(n, notes, id));
  }

  private static boolean isCodeReview(LabelId id) {
    return "Code-Review".equalsIgnoreCase(id.get());
  }

  private static boolean isVerified(LabelId id) {
    return "Verified".equalsIgnoreCase(id.get());
  }

  private Iterable<PatchSetApproval> safeGetApprovals(ChangeNotes notes, PatchSet.Id psId) {
    try {
      return approvalsUtil.byPatchSet(db.get(), notes, psId, null, null);
    } catch (OrmException e) {
      logger.atSevere().withCause(e).log("Can't read approval records for %s", psId);
      return Collections.emptyList();
    }
  }

  private static boolean contains(List<FooterLine> footers, FooterKey key, String val) {
    for (FooterLine line : footers) {
      if (line.matches(key) && val.equals(line.getValue())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isSignedOffBy(List<FooterLine> footers, String email) {
    for (FooterLine line : footers) {
      if (line.matches(FooterKey.SIGNED_OFF_BY) && email.equals(line.getEmailAddress())) {
        return true;
      }
    }
    return false;
  }

  public boolean canMerge(
      MergeSorter mergeSorter, Repository repo, CodeReviewCommit mergeTip, CodeReviewCommit toMerge)
      throws IntegrationException {
    if (hasMissingDependencies(mergeSorter, toMerge)) {
      return false;
    }

    try (ObjectInserter ins = new InMemoryInserter(repo)) {
      return newThreeWayMerger(ins, repo.getConfig()).merge(new AnyObjectId[] {mergeTip, toMerge});
    } catch (LargeObjectException e) {
      logger.atWarning().log("Cannot merge due to LargeObjectException: %s", toMerge.name());
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

  public boolean hasMissingDependencies(MergeSorter mergeSorter, CodeReviewCommit toMerge)
      throws IntegrationException {
    try {
      return !mergeSorter.sort(Collections.singleton(toMerge)).contains(toMerge);
    } catch (IOException | OrmException e) {
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
      for (CodeReviewCommit c : merged) {
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
    mergeResult.setNotes(n.getNotes());
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
      return String.format("Merge changes from topic \"%s\"", Iterables.getFirst(topics, null));
    } else if (topics.size() > 1) {
      return String.format("Merge changes from topics \"%s\"", Joiner.on("\", \"").join(topics));
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
      RevWalk rw, RevFlag canMergeFlag, CodeReviewCommit mergeTip, Set<RevCommit> alreadyAccepted)
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

  private static void matchAuthorToCommitterDate(ProjectState project, CommitBuilder commit) {
    if (project.is(BooleanProjectConfig.MATCH_AUTHOR_TO_COMMITTER_DATE)) {
      commit.setAuthor(
          new PersonIdent(
              commit.getAuthor(),
              commit.getCommitter().getWhen(),
              commit.getCommitter().getTimeZone()));
    }
  }
}
