// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.change;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.RebaseUtil.Base;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.GroupCollector;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.MergeUtilFactory;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.gerrit.server.update.RepoContext;
import com.google.gerrit.server.util.AccountTemplateUtil;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.diff.Sequence;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.merge.MergeResult;
import org.eclipse.jgit.merge.Merger;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * BatchUpdate operation that rebases a change.
 *
 * <p>Can only be executed in a {@link com.google.gerrit.server.update.BatchUpdate} set has a {@link
 * CodeReviewRevWalk} set as {@link RevWalk} (set via {@link
 * com.google.gerrit.server.update.BatchUpdate#setRepository(org.eclipse.jgit.lib.Repository,
 * RevWalk, org.eclipse.jgit.lib.ObjectInserter)}).
 */
public class RebaseChangeOp implements BatchUpdateOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    RebaseChangeOp create(ChangeNotes notes, PatchSet originalPatchSet, ObjectId baseCommitId);

    RebaseChangeOp create(ChangeNotes notes, PatchSet originalPatchSet, Change.Id baseChangeId);
  }

  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final MergeUtilFactory mergeUtilFactory;
  private final RebaseUtil rebaseUtil;
  private final ChangeResource.Factory changeResourceFactory;
  private final ChangeNotes.Factory notesFactory;

  private final ChangeNotes notes;
  private final PatchSet originalPatchSet;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final ProjectCache projectCache;
  private final Project.NameKey projectName;

  private ObjectId baseCommitId;
  private Change.Id baseChangeId;
  private PersonIdent committerIdent;
  private boolean fireRevisionCreated = true;
  private boolean validate = true;
  private boolean checkAddPatchSetPermission = true;
  private boolean forceContentMerge;
  private boolean allowConflicts;
  private boolean detailedCommitMessage;
  private boolean postMessage = true;
  private boolean sendEmail = true;
  private boolean storeCopiedVotes = true;
  private boolean matchAuthorToCommitterDate = false;
  private ImmutableListMultimap<String, String> validationOptions = ImmutableListMultimap.of();
  private String mergeStrategy;
  private boolean verifyNeedsRebase = true;
  private final boolean useDiff3;

  private CodeReviewCommit rebasedCommit;
  private PatchSet.Id rebasedPatchSetId;
  private PatchSetInserter patchSetInserter;
  private PatchSet rebasedPatchSet;

  @AssistedInject
  RebaseChangeOp(
      PatchSetInserter.Factory patchSetInserterFactory,
      MergeUtilFactory mergeUtilFactory,
      RebaseUtil rebaseUtil,
      ChangeResource.Factory changeResourceFactory,
      ChangeNotes.Factory notesFactory,
      GenericFactory identifiedUserFactory,
      ProjectCache projectCache,
      @GerritServerConfig Config cfg,
      @Assisted ChangeNotes notes,
      @Assisted PatchSet originalPatchSet,
      @Assisted ObjectId baseCommitId) {
    this(
        patchSetInserterFactory,
        mergeUtilFactory,
        rebaseUtil,
        changeResourceFactory,
        notesFactory,
        identifiedUserFactory,
        projectCache,
        cfg,
        notes,
        originalPatchSet);
    this.baseCommitId = baseCommitId;
    this.baseChangeId = null;
  }

  @AssistedInject
  RebaseChangeOp(
      PatchSetInserter.Factory patchSetInserterFactory,
      MergeUtilFactory mergeUtilFactory,
      RebaseUtil rebaseUtil,
      ChangeResource.Factory changeResourceFactory,
      ChangeNotes.Factory notesFactory,
      GenericFactory identifiedUserFactory,
      ProjectCache projectCache,
      @GerritServerConfig Config cfg,
      @Assisted ChangeNotes notes,
      @Assisted PatchSet originalPatchSet,
      @Assisted Change.Id baseChangeId) {
    this(
        patchSetInserterFactory,
        mergeUtilFactory,
        rebaseUtil,
        changeResourceFactory,
        notesFactory,
        identifiedUserFactory,
        projectCache,
        cfg,
        notes,
        originalPatchSet);
    this.baseChangeId = baseChangeId;
    this.baseCommitId = null;
  }

  private RebaseChangeOp(
      PatchSetInserter.Factory patchSetInserterFactory,
      MergeUtilFactory mergeUtilFactory,
      RebaseUtil rebaseUtil,
      ChangeResource.Factory changeResourceFactory,
      ChangeNotes.Factory notesFactory,
      GenericFactory identifiedUserFactory,
      ProjectCache projectCache,
      @GerritServerConfig Config cfg,
      ChangeNotes notes,
      PatchSet originalPatchSet) {
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.mergeUtilFactory = mergeUtilFactory;
    this.rebaseUtil = rebaseUtil;
    this.changeResourceFactory = changeResourceFactory;
    this.notesFactory = notesFactory;
    this.identifiedUserFactory = identifiedUserFactory;
    this.projectCache = projectCache;
    this.notes = notes;
    this.projectName = notes.getProjectName();
    this.originalPatchSet = originalPatchSet;
    this.useDiff3 = cfg.getBoolean("change", null, "diff3ConflictView", false);
  }

  @CanIgnoreReturnValue
  public RebaseChangeOp setCommitterIdent(PersonIdent committerIdent) {
    this.committerIdent = committerIdent;
    return this;
  }

  @CanIgnoreReturnValue
  public RebaseChangeOp setValidate(boolean validate) {
    this.validate = validate;
    return this;
  }

  @CanIgnoreReturnValue
  public RebaseChangeOp setCheckAddPatchSetPermission(boolean checkAddPatchSetPermission) {
    this.checkAddPatchSetPermission = checkAddPatchSetPermission;
    return this;
  }

  @CanIgnoreReturnValue
  public RebaseChangeOp setFireRevisionCreated(boolean fireRevisionCreated) {
    this.fireRevisionCreated = fireRevisionCreated;
    return this;
  }

  @CanIgnoreReturnValue
  public RebaseChangeOp setForceContentMerge(boolean forceContentMerge) {
    this.forceContentMerge = forceContentMerge;
    return this;
  }

  /**
   * Allows the rebase to succeed if there are conflicts.
   *
   * <p>This setting requires that {@link #forceContentMerge} is set {@code true}. If {@link
   * #forceContentMerge} is {@code false} this setting has no effect.
   *
   * @see #setForceContentMerge(boolean)
   */
  @CanIgnoreReturnValue
  public RebaseChangeOp setAllowConflicts(boolean allowConflicts) {
    this.allowConflicts = allowConflicts;
    return this;
  }

  @CanIgnoreReturnValue
  public RebaseChangeOp setDetailedCommitMessage(boolean detailedCommitMessage) {
    this.detailedCommitMessage = detailedCommitMessage;
    return this;
  }

  @CanIgnoreReturnValue
  public RebaseChangeOp setPostMessage(boolean postMessage) {
    this.postMessage = postMessage;
    return this;
  }

  /**
   * We always want to store copied votes except when the change is getting submitted and a new
   * patch-set is created on submit (using submit strategies such as "REBASE_ALWAYS"). In such
   * cases, we already store the votes of the new patch-sets in SubmitStrategyOp#saveApprovals. We
   * should not also store the copied votes.
   */
  @CanIgnoreReturnValue
  public RebaseChangeOp setStoreCopiedVotes(boolean storeCopiedVotes) {
    this.storeCopiedVotes = storeCopiedVotes;
    return this;
  }

  @CanIgnoreReturnValue
  public RebaseChangeOp setSendEmail(boolean sendEmail) {
    this.sendEmail = sendEmail;
    return this;
  }

  @CanIgnoreReturnValue
  public RebaseChangeOp setMatchAuthorToCommitterDate(boolean matchAuthorToCommitterDate) {
    this.matchAuthorToCommitterDate = matchAuthorToCommitterDate;
    return this;
  }

  @CanIgnoreReturnValue
  public RebaseChangeOp setValidationOptions(
      ImmutableListMultimap<String, String> validationOptions) {
    requireNonNull(validationOptions, "validationOptions may not be null");
    this.validationOptions = validationOptions;
    return this;
  }

  @CanIgnoreReturnValue
  public RebaseChangeOp setMergeStrategy(String strategy) {
    this.mergeStrategy = strategy;
    return this;
  }

  @CanIgnoreReturnValue
  public RebaseChangeOp setVerifyNeedsRebase(boolean verifyNeedsRebase) {
    this.verifyNeedsRebase = verifyNeedsRebase;
    return this;
  }

  @Override
  public void updateRepo(RepoContext ctx)
      throws InvalidChangeOperationException,
          RestApiException,
          IOException,
          NoSuchChangeException,
          PermissionBackendException,
          DiffNotAvailableException {
    // Ok that originalPatchSet was not read in a transaction, since we just
    // need its revision.
    RevWalk rw = ctx.getRevWalk();
    RevCommit original = rw.parseCommit(originalPatchSet.commitId());
    rw.parseBody(original);
    RevCommit baseCommit;
    if (baseCommitId != null && baseChangeId == null) {
      baseCommit = rw.parseCommit(baseCommitId);
    } else if (baseChangeId != null) {
      baseCommit =
          PatchSetUtil.getCurrentRevCommitIncludingPending(ctx, notesFactory, baseChangeId);
    } else {
      throw new IllegalStateException(
          "Exactly one of base commit and base change must be provided.");
    }
    CurrentUser changeOwner = identifiedUserFactory.create(notes.getChange().getOwner());

    String newCommitMessage;
    if (detailedCommitMessage) {
      rw.parseBody(baseCommit);
      newCommitMessage =
          newMergeUtil()
              .createCommitMessageOnSubmit(original, baseCommit, notes, originalPatchSet.id());
    } else {
      newCommitMessage = original.getFullMessage();
    }

    rebasedCommit = rebaseCommit(ctx, original, baseCommit, newCommitMessage, notes.getChangeId());
    Base base =
        rebaseUtil.parseBase(
            new RevisionResource(
                changeResourceFactory.create(notes, changeOwner), originalPatchSet),
            baseCommit.getName());

    rebasedPatchSetId =
        ChangeUtil.nextPatchSetIdFromChangeRefs(
            ctx.getRepoView().getRefs(originalPatchSet.id().changeId().toRefPrefix()).keySet(),
            notes.getChange().currentPatchSetId());
    patchSetInserter =
        patchSetInserterFactory
            .create(notes, rebasedPatchSetId, rebasedCommit)
            .setDescription("Rebase")
            .setFireRevisionCreated(fireRevisionCreated)
            .setCheckAddPatchSetPermission(checkAddPatchSetPermission)
            .setValidate(validate)
            .setSendEmail(sendEmail)
            // The votes are automatically copied and they don't count as copied votes. See
            // method's javadoc.
            .setStoreCopiedVotes(storeCopiedVotes);

    if (!rebasedCommit.getFilesWithGitConflicts().isEmpty()
        && !notes.getChange().isWorkInProgress()) {
      patchSetInserter.setWorkInProgress(true);
    }

    patchSetInserter.setValidationOptions(validationOptions);

    if (postMessage) {
      patchSetInserter.setMessage(
          messageForRebasedChange(
              ctx.getIdentifiedUser(), rebasedPatchSetId, originalPatchSet.id(), rebasedCommit));
    }

    if (base != null && !base.notes().getChange().isMerged()) {
      if (!base.notes().getChange().isMerged()) {
        // Add to end of relation chain for open base change.
        patchSetInserter.setGroups(base.patchSet().groups());
      } else {
        // If the base is merged, start a new relation chain.
        patchSetInserter.setGroups(GroupCollector.getDefaultGroups(rebasedCommit));
      }
    }

    logger.atFine().log(
        "flushing inserter %s", ctx.getRevWalk().getObjectReader().getCreatedFromInserter());
    ctx.getRevWalk().getObjectReader().getCreatedFromInserter().flush();
    patchSetInserter.updateRepo(ctx);
  }

  private static String messageForRebasedChange(
      IdentifiedUser user,
      PatchSet.Id rebasePatchSetId,
      PatchSet.Id originalPatchSetId,
      CodeReviewCommit commit) {
    StringBuilder stringBuilder =
        new StringBuilder(
            String.format(
                "Patch Set %d: Patch Set %d was rebased",
                rebasePatchSetId.get(), originalPatchSetId.get()));

    if (user.isImpersonating()) {
      stringBuilder.append(
          String.format(
              " on behalf of %s", AccountTemplateUtil.getAccountTemplate(user.getAccountId())));
    }

    if (!commit.getFilesWithGitConflicts().isEmpty()) {
      stringBuilder.append("\n\nThe following files contain Git conflicts:\n");
      commit.getFilesWithGitConflicts().stream()
          .sorted()
          .forEach(filePath -> stringBuilder.append("* ").append(filePath).append("\n"));
    }

    return stringBuilder.toString();
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws ResourceConflictException, IOException, BadRequestException {
    boolean ret = patchSetInserter.updateChange(ctx);
    rebasedPatchSet = patchSetInserter.getPatchSet();
    return ret;
  }

  @Override
  public void postUpdate(PostUpdateContext ctx) {
    patchSetInserter.postUpdate(ctx);
  }

  public CodeReviewCommit getRebasedCommit() {
    checkState(rebasedCommit != null, "getRebasedCommit() only valid after updateRepo");
    return rebasedCommit;
  }

  public PatchSet getOriginalPatchSet() {
    return originalPatchSet;
  }

  public PatchSet.Id getPatchSetId() {
    checkState(rebasedPatchSetId != null, "getPatchSetId() only valid after updateRepo");
    return rebasedPatchSetId;
  }

  public PatchSet getPatchSet() {
    checkState(rebasedPatchSet != null, "getPatchSet() only valid after executing update");
    return rebasedPatchSet;
  }

  private MergeUtil newMergeUtil() {
    ProjectState project = projectCache.get(projectName).orElseThrow(illegalState(projectName));
    return forceContentMerge
        ? mergeUtilFactory.create(project, true)
        : mergeUtilFactory.create(project);
  }

  /**
   * Rebase a commit.
   *
   * @param ctx repo context.
   * @param original the commit to rebase.
   * @param base base to rebase against.
   * @return the rebased commit.
   * @throws MergeConflictException the rebase failed due to a merge conflict.
   * @throws IOException the merge failed for another reason.
   */
  private CodeReviewCommit rebaseCommit(
      RepoContext ctx,
      RevCommit original,
      ObjectId base,
      String commitMessage,
      Change.Id originalChangeId)
      throws ResourceConflictException, IOException {
    RevCommit parentCommit = original.getParent(0);

    if (verifyNeedsRebase && base.equals(parentCommit)) {
      throw new ResourceConflictException("Change is already up to date.");
    }

    MergeUtil mergeUtil = newMergeUtil();
    String strategy =
        firstNonNull(Strings.emptyToNull(mergeStrategy), mergeUtil.mergeStrategyName());

    Merger merger = MergeUtil.newMerger(ctx.getInserter(), ctx.getRepoView().getConfig(), strategy);
    if (merger instanceof ThreeWayMerger) {
      ((ThreeWayMerger) merger).setBase(parentCommit);
    }

    DirCache dc = DirCache.newInCore();
    if (allowConflicts && merger instanceof ResolveMerger) {
      // The DirCache must be set on ResolveMerger before calling
      // ResolveMerger#merge(AnyObjectId...) otherwise the entries in DirCache don't get populated.
      ((ResolveMerger) merger).setDirCache(dc);
    }

    boolean success = merger.merge(original, base);

    ObjectId tree;
    ImmutableSet<String> filesWithGitConflicts;
    if (success) {
      filesWithGitConflicts = null;
      tree = merger.getResultTreeId();
      logger.atFine().log(
          "tree of rebased commit: %s (no conflicts, inserter: %s)",
          tree.name(), merger.getObjectInserter());
    } else {
      List<String> conflicts = ImmutableList.of();
      Map<String, ResolveMerger.MergeFailureReason> failed = ImmutableMap.of();
      if (merger instanceof ResolveMerger) {
        conflicts = ((ResolveMerger) merger).getUnmergedPaths();
        failed = ((ResolveMerger) merger).getFailingPaths();
      }

      if (merger.getResultTreeId() != null) {
        // Merging with conflicts below uses the same DirCache instance that has been used by the
        // Merger to attempt the merge without conflicts.
        //
        // The Merger uses the DirCache to do the updates, and in particular to write the result
        // tree. DirCache caches a single DirCacheTree instance that is used to write the result
        // tree, but it writes the result tree only if there were no conflicts.
        //
        // Merging with conflicts uses the same DirCache instance to write the tree with conflicts
        // that has been used by the Merger. This means if the Merger unexpectedly wrote a result
        // tree although there had been conflicts, then merging with conflicts uses the same
        // DirCacheTree instance to write the tree with conflicts. However DirCacheTree#writeTree
        // writes a tree only once and then that tree is cached. Further invocations of
        // DirCacheTree#writeTree have no effect and return the previously created tree. This means
        // merging with conflicts can only successfully create the tree with conflicts if the Merger
        // didn't write a result tree yet. Hence this is checked here and we log a warning if the
        // result tree was already written.
        logger.atWarning().log(
            "result tree has already been written: %s (merger: %s, conflicts: %s, failed: %s)",
            merger, merger.getResultTreeId().name(), conflicts, failed);
      }

      if (!allowConflicts || !(merger instanceof ResolveMerger)) {
        throw new MergeConflictException(
            String.format(
                "Change %s could not be rebased due to a conflict during merge.\n\n%s",
                originalChangeId.toString(), MergeUtil.createConflictMessage(conflicts)));
      }

      Map<String, MergeResult<? extends Sequence>> mergeResults =
          ((ResolveMerger) merger).getMergeResults();

      filesWithGitConflicts =
          mergeResults.entrySet().stream()
              .filter(e -> e.getValue().containsConflicts())
              .map(Map.Entry::getKey)
              .collect(toImmutableSet());

      logger.atFine().log("rebasing with conflicts");
      tree =
          MergeUtil.mergeWithConflicts(
              ctx.getRevWalk(),
              ctx.getInserter(),
              dc,
              "PATCH SET",
              original,
              "BASE",
              ctx.getRevWalk().parseCommit(base),
              mergeResults,
              useDiff3);
      logger.atFine().log(
          "tree of rebased commit: %s (with conflicts, inserter: %s)",
          tree.name(), ctx.getInserter());
    }

    List<ObjectId> parents = new ArrayList<>();
    parents.add(base);
    if (original.getParentCount() > 1) {
      // If a merge commit is rebased add all other parents (parent 2 to N).
      for (int parent = 1; parent < original.getParentCount(); parent++) {
        parents.add(original.getParent(parent));
      }
    }

    CommitBuilder cb = new CommitBuilder();
    cb.setTreeId(tree);
    cb.setParentIds(parents);
    cb.setAuthor(original.getAuthorIdent());
    cb.setMessage(commitMessage);
    if (committerIdent != null) {
      cb.setCommitter(committerIdent);
    } else {
      PersonIdent committerIdent =
          Optional.ofNullable(original.getCommitterIdent())
              .map(ident -> ctx.newCommitterIdent(ident.getEmailAddress(), ctx.getIdentifiedUser()))
              .orElseGet(ctx::newCommitterIdent);
      cb.setCommitter(committerIdent);
    }
    if (matchAuthorToCommitterDate) {
      cb.setAuthor(
          new PersonIdent(
              cb.getAuthor(), cb.getCommitter().getWhen(), cb.getCommitter().getTimeZone()));
    }
    ObjectId objectId = ctx.getInserter().insert(cb);
    CodeReviewCommit commit = ((CodeReviewRevWalk) ctx.getRevWalk()).parseCommit(objectId);
    commit.setFilesWithGitConflicts(filesWithGitConflicts);
    logger.atFine().log("rebased commit=%s", commit.name());
    return commit;
  }
}
