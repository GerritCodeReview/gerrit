package com.google.gerrit.server.change;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.RebaseUtil.Base;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.GroupCollector;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.MergeUtilFactory;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.gerrit.server.update.RepoContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.diff.Sequence;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.merge.MergeResult;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public abstract class RebaseOp<RebaseOpSubclass extends RebaseOp<RebaseOpSubclass>>
    implements BatchUpdateOp {

  protected final PatchSetInserter.Factory patchSetInserterFactory;
  protected final MergeUtilFactory mergeUtilFactory;
  protected final RebaseUtil rebaseUtil;
  protected final ChangeResource.Factory changeResourceFactory;
  protected final ChangeNotes notes;
  protected final PatchSet originalPatchSet;
  protected final IdentifiedUser.GenericFactory identifiedUserFactory;
  protected final ProjectCache projectCache;
  protected ObjectId baseCommitId;
  protected boolean fireRevisionCreated = true;
  protected boolean validate = true;
  protected boolean checkAddPatchSetPermission = true;
  protected boolean detailedCommitMessage;
  protected boolean postMessage = true;
  protected boolean sendEmail = true;
  protected boolean storeCopiedVotes = true;
  protected ImmutableListMultimap<String, String> validationOptions = ImmutableListMultimap.of();
  protected CodeReviewCommit rebasedCommit;
  protected PatchSet.Id rebasedPatchSetId;
  protected PatchSetInserter patchSetInserter;
  protected PatchSet rebasedPatchSet;
  private PersonIdent committerIdent;
  private boolean forceContentMerge;
  private boolean allowConflicts;
  private boolean matchAuthorToCommitterDate = false;

  public RebaseOp(
      PatchSetInserter.Factory patchSetInserterFactory,
      MergeUtilFactory mergeUtilFactory,
      RebaseUtil rebaseUtil,
      ChangeResource.Factory changeResourceFactory,
      @Assisted ChangeNotes notes,
      @Assisted PatchSet originalPatchSet,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      ProjectCache projectCache,
      @Assisted ObjectId baseCommitId) {
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.mergeUtilFactory = mergeUtilFactory;
    this.rebaseUtil = rebaseUtil;
    this.changeResourceFactory = changeResourceFactory;
    this.notes = notes;
    this.originalPatchSet = originalPatchSet;
    this.identifiedUserFactory = identifiedUserFactory;
    this.projectCache = projectCache;
    this.baseCommitId = baseCommitId;
  }

  protected static String messageForRebasedChange(
      PatchSet.Id rebasePatchSetId, PatchSet.Id originalPatchSetId, CodeReviewCommit commit) {
    StringBuilder stringBuilder =
        new StringBuilder(
            String.format(
                "Patch Set %d: Patch Set %d was rebased",
                rebasePatchSetId.get(), originalPatchSetId.get()));

    if (!commit.getFilesWithGitConflicts().isEmpty()) {
      stringBuilder.append("\n\nThe following files contain Git conflicts:\n");
      commit.getFilesWithGitConflicts().stream()
          .sorted()
          .forEach(filePath -> stringBuilder.append("* ").append(filePath).append("\n"));
    }

    return stringBuilder.toString();
  }

  public RebaseOpSubclass setCommitterIdent(PersonIdent committerIdent) {
    this.committerIdent = committerIdent;
    return (RebaseOpSubclass) this;
  }

  public RebaseOpSubclass setValidate(boolean validate) {
    this.validate = validate;
    return (RebaseOpSubclass) this;
  }

  public RebaseOpSubclass setCheckAddPatchSetPermission(boolean checkAddPatchSetPermission) {
    this.checkAddPatchSetPermission = checkAddPatchSetPermission;
    return (RebaseOpSubclass) this;
  }

  public RebaseOpSubclass setFireRevisionCreated(boolean fireRevisionCreated) {
    this.fireRevisionCreated = fireRevisionCreated;
    return (RebaseOpSubclass) this;
  }

  public RebaseOpSubclass setForceContentMerge(boolean forceContentMerge) {
    this.forceContentMerge = forceContentMerge;
    return (RebaseOpSubclass) this;
  }

  /**
   * Allows the rebase to succeed if there are conflicts.
   *
   * <p>This setting requires that {@link #forceContentMerge} is set {@code true}. If {@link
   * #forceContentMerge} is {@code false} this setting has no effect.
   *
   * @see #setForceContentMerge(boolean)
   */
  public RebaseOpSubclass setAllowConflicts(boolean allowConflicts) {
    this.allowConflicts = allowConflicts;
    return (RebaseOpSubclass) this;
  }

  public RebaseOpSubclass setDetailedCommitMessage(boolean detailedCommitMessage) {
    this.detailedCommitMessage = detailedCommitMessage;
    return (RebaseOpSubclass) this;
  }

  public RebaseOpSubclass setPostMessage(boolean postMessage) {
    this.postMessage = postMessage;
    return (RebaseOpSubclass) this;
  }

  /**
   * We always want to store copied votes except when the change is getting submitted and a new
   * patch-set is created on submit (using submit strategies such as "REBASE_ALWAYS"). In such
   * cases, we already store the votes of the new patch-sets in SubmitStrategyOp#saveApprovals. We
   * should not also store the copied votes.
   */
  public RebaseOpSubclass setStoreCopiedVotes(boolean storeCopiedVotes) {
    this.storeCopiedVotes = storeCopiedVotes;
    return (RebaseOpSubclass) this;
  }

  public RebaseOpSubclass setMatchAuthorToCommitterDate(boolean matchAuthorToCommitterDate) {
    this.matchAuthorToCommitterDate = matchAuthorToCommitterDate;
    return (RebaseOpSubclass) this;
  }

  public RebaseOpSubclass setValidationOptions(
      ImmutableListMultimap<String, String> validationOptions) {
    requireNonNull(validationOptions, "validationOptions may not be null");
    this.validationOptions = validationOptions;
    return (RebaseOpSubclass) this;
  }

  public RebaseOpSubclass setSendEmail(boolean sendEmail) {
    this.sendEmail = sendEmail;
    return (RebaseOpSubclass) this;
  }

  public CodeReviewCommit getRebasedCommit() {
    checkState(rebasedCommit != null, "getRebasedCommit() only valid after updateRepo");
    return rebasedCommit;
  }

  public PatchSet.Id getPatchSetId() {
    checkState(rebasedPatchSetId != null, "getPatchSetId() only valid after updateRepo");
    return rebasedPatchSetId;
  }

  public PatchSet getPatchSet() {
    checkState(rebasedPatchSet != null, "getPatchSet() only valid after executing update");
    return rebasedPatchSet;
  }

  protected MergeUtil newMergeUtil() {
    ProjectState project =
        projectCache.get(notes.getProjectName()).orElseThrow(illegalState(notes.getProjectName()));
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
  protected CodeReviewCommit rebaseCommit(
      RepoContext ctx, RevCommit original, ObjectId base, String commitMessage)
      throws ResourceConflictException, IOException {
    RevCommit parentCommit = original.getParent(0);

    if (base.equals(parentCommit)) {
      throw new ResourceConflictException("Change is already up to date.");
    }

    ThreeWayMerger merger =
        newMergeUtil().newThreeWayMerger(ctx.getInserter(), ctx.getRepoView().getConfig());
    merger.setBase(parentCommit);

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
    } else {
      List<String> conflicts = ImmutableList.of();
      if (merger instanceof ResolveMerger) {
        conflicts = ((ResolveMerger) merger).getUnmergedPaths();
      }

      if (!allowConflicts || !(merger instanceof ResolveMerger)) {
        throw new MergeConflictException(
            "The change could not be rebased due to a conflict during merge.\n\n"
                + MergeUtil.createConflictMessage(conflicts));
      }

      Map<String, MergeResult<? extends Sequence>> mergeResults =
          ((ResolveMerger) merger).getMergeResults();

      filesWithGitConflicts =
          mergeResults.entrySet().stream()
              .filter(e -> e.getValue().containsConflicts())
              .map(Map.Entry::getKey)
              .collect(toImmutableSet());

      tree =
          MergeUtil.mergeWithConflicts(
              ctx.getRevWalk(),
              ctx.getInserter(),
              dc,
              "PATCH SET",
              original,
              "BASE",
              ctx.getRevWalk().parseCommit(base),
              mergeResults);
    }

    CommitBuilder cb = new CommitBuilder();
    cb.setTreeId(tree);
    cb.setParentId(base);
    cb.setAuthor(original.getAuthorIdent());
    cb.setMessage(commitMessage);
    if (committerIdent != null) {
      cb.setCommitter(committerIdent);
    } else {
      cb.setCommitter(ctx.newCommitterIdent());
    }
    if (matchAuthorToCommitterDate) {
      cb.setAuthor(
          new PersonIdent(
              cb.getAuthor(), cb.getCommitter().getWhen(), cb.getCommitter().getTimeZone()));
    }
    ObjectId objectId = ctx.getInserter().insert(cb);
    CodeReviewCommit commit = ((CodeReviewRevWalk) ctx.getRevWalk()).parseCommit(objectId);
    commit.setFilesWithGitConflicts(filesWithGitConflicts);
    return commit;
  }

  /**
   * BatchUpdate operation that rebases a change.
   *
   * <p>Can only be executed in a {@link com.google.gerrit.server.update.BatchUpdate} set has a
   * {@link CodeReviewRevWalk} set as {@link RevWalk} (set via {@link
   * com.google.gerrit.server.update.BatchUpdate#setRepository(org.eclipse.jgit.lib.Repository,
   * RevWalk, org.eclipse.jgit.lib.ObjectInserter)}).
   */
  public static class RebaseChangeOp extends RebaseOp<RebaseChangeOp> {
    public interface Factory {
      RebaseChangeOp create(ChangeNotes notes, PatchSet originalPatchSet, ObjectId baseCommitId);
    }

    @Inject
    RebaseChangeOp(
        PatchSetInserter.Factory patchSetInserterFactory,
        MergeUtilFactory mergeUtilFactory,
        RebaseUtil rebaseUtil,
        ChangeResource.Factory changeResourceFactory,
        IdentifiedUser.GenericFactory identifiedUserFactory,
        ProjectCache projectCache,
        @Assisted ChangeNotes notes,
        @Assisted PatchSet originalPatchSet,
        @Assisted ObjectId baseCommitId) {
      super(
          patchSetInserterFactory,
          mergeUtilFactory,
          rebaseUtil,
          changeResourceFactory,
          notes,
          originalPatchSet,
          identifiedUserFactory,
          projectCache,
          baseCommitId);
    }

    @Override
    public void updateRepo(RepoContext ctx)
        throws InvalidChangeOperationException, RestApiException, IOException,
            NoSuchChangeException, PermissionBackendException {
      // Ok that originalPatchSet was not read in a transaction, since we just
      // need its revision.
      RevWalk rw = ctx.getRevWalk();
      RevCommit original = rw.parseCommit(originalPatchSet.commitId());
      rw.parseBody(original);
      RevCommit baseCommit = rw.parseCommit(baseCommitId);
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

      rebasedCommit = rebaseCommit(ctx, original, baseCommit, newCommitMessage);
      ctx.getInserter().flush();
      Base base =
          rebaseUtil.parseBase(
              new RevisionResource(
                  changeResourceFactory.create(notes, changeOwner), originalPatchSet),
              baseCommitId.name());

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
            messageForRebasedChange(rebasedPatchSetId, originalPatchSet.id(), rebasedCommit));
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

      ctx.getRevWalk().getObjectReader().getCreatedFromInserter().flush();
      patchSetInserter.updateRepo(ctx);
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
  }
}
