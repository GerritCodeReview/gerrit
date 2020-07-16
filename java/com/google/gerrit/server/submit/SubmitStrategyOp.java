// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.submit;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.LabelNormalizer;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.GroupCollector;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.RepoContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;

abstract class SubmitStrategyOp implements BatchUpdateOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  protected final SubmitStrategy.Arguments args;
  protected final CodeReviewCommit toMerge;

  private ReceiveCommand command;
  private PatchSetApproval submitter;
  private ObjectId mergeResultRev;
  private PatchSet mergedPatchSet;
  private Change updatedChange;
  private CodeReviewCommit alreadyMergedCommit;
  private boolean changeAlreadyMerged;

  protected SubmitStrategyOp(SubmitStrategy.Arguments args, CodeReviewCommit toMerge) {
    this.args = args;
    this.toMerge = toMerge;
  }

  final Change.Id getId() {
    return toMerge.change().getId();
  }

  final CodeReviewCommit getCommit() {
    return toMerge;
  }

  protected final BranchNameKey getDest() {
    return toMerge.change().getDest();
  }

  protected final Project.NameKey getProject() {
    return getDest().project();
  }

  @Override
  public final void updateRepo(RepoContext ctx) throws Exception {
    logger.atFine().log(
        "%s#updateRepo for change %s", getClass().getSimpleName(), toMerge.change().getId());
    checkState(
        ctx.getRevWalk() == args.rw,
        "SubmitStrategyOp requires callers to call BatchUpdate#setRepository with exactly the same"
            + " CodeReviewRevWalk instance from the SubmitStrategy.Arguments: %s != %s",
        ctx.getRevWalk(),
        args.rw);
    // Run the submit strategy implementation and record the merge tip state so
    // we can create the ref update.
    CodeReviewCommit tipBefore = args.mergeTip.getCurrentTip();
    alreadyMergedCommit = getAlreadyMergedCommit(ctx);
    if (alreadyMergedCommit == null) {
      updateRepoImpl(ctx);
    } else {
      logger.atFine().log("Already merged as %s", alreadyMergedCommit.name());
    }
    CodeReviewCommit tipAfter = args.mergeTip.getCurrentTip();

    if (Objects.equals(tipBefore, tipAfter)) {
      logger.atFine().log("Did not move tip");
      return;
    } else if (tipAfter == null) {
      logger.atFine().log("No merge tip, no update to perform");
      return;
    }
    logger.atFine().log("Moved tip from %s to %s", tipBefore, tipAfter);

    checkProjectConfig(ctx, tipAfter);

    // Needed by postUpdate, at which point mergeTip will have advanced further,
    // so it's easier to just snapshot the command.
    command =
        new ReceiveCommand(
            firstNonNull(tipBefore, ObjectId.zeroId()), tipAfter, getDest().branch());
    ctx.addRefUpdate(command);
    args.submoduleOp.addBranchTip(getDest(), tipAfter);
  }

  private void checkProjectConfig(RepoContext ctx, CodeReviewCommit commit) {
    String refName = getDest().branch();
    if (RefNames.REFS_CONFIG.equals(refName)) {
      logger.atFine().log("Loading new configuration from %s", RefNames.REFS_CONFIG);
      try {
        ProjectConfig cfg = args.projectConfigFactory.create(getProject());
        cfg.load(ctx.getRevWalk(), commit);
      } catch (Exception e) {
        throw new StorageException(
            "Submit would store invalid"
                + " project configuration "
                + commit.name()
                + " for "
                + getProject(),
            e);
      }
    }
  }

  private CodeReviewCommit getAlreadyMergedCommit(RepoContext ctx) throws IOException {
    CodeReviewCommit tip = args.mergeTip.getInitialTip();
    if (tip == null) {
      return null;
    }
    CodeReviewRevWalk rw = (CodeReviewRevWalk) ctx.getRevWalk();
    Change.Id id = getId();
    String refPrefix = id.toRefPrefix();

    Map<String, ObjectId> refs = ctx.getRepoView().getRefs(refPrefix);
    List<CodeReviewCommit> commits = new ArrayList<>(refs.size());
    for (Map.Entry<String, ObjectId> e : refs.entrySet()) {
      PatchSet.Id psId = PatchSet.Id.fromRef(refPrefix + e.getKey());
      if (psId == null) {
        continue;
      }
      try {
        CodeReviewCommit c = rw.parseCommit(e.getValue());
        c.setPatchsetId(psId);
        commits.add(c);
      } catch (MissingObjectException | IncorrectObjectTypeException ex) {
        continue; // Bogus ref, can't be merged into tip so we don't care.
      }
    }
    commits.sort(comparing((CodeReviewCommit c) -> c.getPatchsetId().get()).reversed());
    CodeReviewCommit result = MergeUtil.findAnyMergedInto(rw, commits, tip);
    if (result == null) {
      return null;
    }

    // Some patch set of this change is actually merged into the target
    // branch, most likely because a previous run of MergeOp failed after
    // updateRepo, during updateChange.
    //
    // Do the best we can to clean this up: mark the change as merged and set
    // the current patch set. Don't touch the dest branch at all. This can
    // lead to some odd situations like another change in the set merging in
    // a different patch set of this change, but that's unavoidable at this
    // point.  At least the change will end up in the right state.
    //
    // TODO(dborowitz): Consider deleting later junk patch set refs. They
    // presumably don't have PatchSets pointing to them.
    rw.parseBody(result);
    result.add(args.canMergeFlag);
    PatchSet.Id psId = result.getPatchsetId();
    result.copyFrom(toMerge);
    result.setPatchsetId(psId); // Got overwriten by copyFrom.
    result.setStatusCode(CommitMergeStatus.ALREADY_MERGED);
    args.commitStatus.put(result);
    return result;
  }

  @Override
  public final boolean updateChange(ChangeContext ctx) throws Exception {
    logger.atFine().log(
        "%s#updateChange for change %s", getClass().getSimpleName(), toMerge.change().getId());
    toMerge.setNotes(ctx.getNotes()); // Update change and notes from ctx.

    if (ctx.getChange().isMerged()) {
      // Either another thread won a race, or we are retrying a whole topic submission after one
      // repo failed with lock failure.
      if (alreadyMergedCommit == null) {
        logger.atFine().log(
            "Change is already merged according to its status, but we were unable to find it"
                + " merged into the current tip (%s)",
            args.mergeTip.getCurrentTip().name());
      } else {
        logger.atFine().log("Change is already merged");
      }
      changeAlreadyMerged = true;
      return false;
    }

    if (alreadyMergedCommit != null) {
      alreadyMergedCommit.setNotes(ctx.getNotes());
      mergedPatchSet = getOrCreateAlreadyMergedPatchSet(ctx);
    } else {
      PatchSet newPatchSet = updateChangeImpl(ctx);
      PatchSet.Id oldPsId = requireNonNull(toMerge.getPatchsetId());
      PatchSet.Id newPsId = requireNonNull(ctx.getChange().currentPatchSetId());
      if (newPatchSet == null) {
        checkState(
            oldPsId.equals(newPsId),
            "patch set advanced from %s to %s but updateChangeImpl did not"
                + " return new patch set instance",
            oldPsId,
            newPsId);
        // Ok to use stale notes to get the old patch set, which didn't change
        // during the submit strategy.
        mergedPatchSet =
            requireNonNull(
                args.psUtil.get(ctx.getNotes(), oldPsId),
                () -> String.format("missing old patch set %s", oldPsId));
      } else {
        PatchSet.Id n = newPatchSet.id();
        checkState(
            !n.equals(oldPsId) && n.equals(newPsId),
            "current patch was %s and is now %s, but updateChangeImpl returned"
                + " new patch set instance at %s",
            oldPsId,
            newPsId,
            n);
        mergedPatchSet = newPatchSet;
      }
    }

    Change c = ctx.getChange();
    Change.Id id = c.getId();
    CodeReviewCommit commit = args.commitStatus.get(id);
    requireNonNull(commit, () -> String.format("missing commit for change %s", id));
    CommitMergeStatus s = commit.getStatusCode();
    requireNonNull(
        s,
        () -> String.format("status not set for change %s; expected to previously fail fast", id));
    logger.atFine().log("Status of change %s (%s) on %s: %s", id, commit.name(), c.getDest(), s);
    setApproval(ctx, args.caller);

    mergeResultRev =
        alreadyMergedCommit == null
            ? args.mergeTip.getMergeResults().get(commit)
            // Our fixup code is not smart enough to find a merge commit
            // corresponding to the merge result. This results in a different
            // ChangeMergedEvent in the fixup case, but we'll just live with that.
            : alreadyMergedCommit;
    try {
      setMerged(ctx, message(ctx, commit, s));
    } catch (StorageException err) {
      String msg = "Error updating change status for " + id;
      logger.atSevere().withCause(err).log(msg);
      args.commitStatus.logProblem(id, msg);
      // It's possible this happened before updating anything in the db, but
      // it's hard to know for sure, so just return true below to be safe.
    }
    updatedChange = c;
    return true;
  }

  /**
   * Returns the updated change after this op has been executed.
   *
   * @return the updated change after this op has been executed, {@link Optional#empty()} if the op
   *     was not executed yet, or if the execution has failed
   */
  public Optional<Change> getUpdatedChange() {
    return Optional.ofNullable(updatedChange);
  }

  private PatchSet getOrCreateAlreadyMergedPatchSet(ChangeContext ctx) throws IOException {
    PatchSet.Id psId = alreadyMergedCommit.getPatchsetId();
    logger.atFine().log("Fixing up already-merged patch set %s", psId);
    PatchSet prevPs = args.psUtil.current(ctx.getNotes());
    ctx.getRevWalk().parseBody(alreadyMergedCommit);
    ctx.getChange()
        .setCurrentPatchSet(
            psId, alreadyMergedCommit.getShortMessage(), ctx.getChange().getOriginalSubject());
    PatchSet existing = args.psUtil.get(ctx.getNotes(), psId);
    if (existing != null) {
      logger.atFine().log("Patch set row exists, only updating change");
      return existing;
    }
    // No patch set for the already merged commit, although we know it came form
    // a patch set ref. Fix up the database. Note that this uses the current
    // user as the uploader, which is as good a guess as any.
    List<String> groups =
        prevPs != null ? prevPs.groups() : GroupCollector.getDefaultGroups(alreadyMergedCommit);
    return args.psUtil.insert(
        ctx.getRevWalk(), ctx.getUpdate(psId), psId, alreadyMergedCommit, groups, null, null);
  }

  private void setApproval(ChangeContext ctx, IdentifiedUser user) throws IOException {
    Change.Id id = ctx.getChange().getId();
    List<SubmitRecord> records = args.commitStatus.getSubmitRecords(id);
    PatchSet.Id oldPsId = toMerge.getPatchsetId();
    PatchSet.Id newPsId = ctx.getChange().currentPatchSetId();

    logger.atFine().log("Add approval for %s", id);
    ChangeUpdate origPsUpdate = ctx.getUpdate(oldPsId);
    origPsUpdate.putReviewer(user.getAccountId(), REVIEWER);
    LabelNormalizer.Result normalized = approve(ctx, origPsUpdate);

    ChangeUpdate newPsUpdate = ctx.getUpdate(newPsId);
    newPsUpdate.merge(args.submissionId, records);
    // If the submit strategy created a new revision (rebase, cherry-pick), copy
    // approvals as well.
    if (!newPsId.equals(oldPsId)) {
      saveApprovals(normalized, newPsUpdate, true);
      submitter = submitter.copyWithPatchSet(newPsId);
    }
  }

  private LabelNormalizer.Result approve(ChangeContext ctx, ChangeUpdate update)
      throws IOException {
    PatchSet.Id psId = update.getPatchSetId();
    Map<PatchSetApproval.Key, PatchSetApproval> byKey = new HashMap<>();
    for (PatchSetApproval psa :
        args.approvalsUtil.byPatchSet(
            ctx.getNotes(), psId, ctx.getRevWalk(), ctx.getRepoView().getConfig())) {
      byKey.put(psa.key(), psa);
    }

    submitter =
        ApprovalsUtil.newApproval(psId, ctx.getUser(), LabelId.legacySubmit(), 1, ctx.getWhen())
            .build();
    byKey.put(submitter.key(), submitter);

    // Flatten out existing approvals for this patch set based upon the current
    // permissions. Once the change is closed the approvals are not updated at
    // presentation view time, except for zero votes used to indicate a reviewer
    // was added. So we need to make sure votes are accurate now. This way if
    // permissions get modified in the future, historical records stay accurate.
    LabelNormalizer.Result normalized =
        args.labelNormalizer.normalize(ctx.getNotes(), byKey.values());
    update.putApproval(submitter.label(), submitter.value());
    saveApprovals(normalized, update, false);
    return normalized;
  }

  private void saveApprovals(
      LabelNormalizer.Result normalized, ChangeUpdate update, boolean includeUnchanged) {
    for (PatchSetApproval psa : normalized.updated()) {
      update.putApprovalFor(psa.accountId(), psa.label(), psa.value());
    }
    for (PatchSetApproval psa : normalized.deleted()) {
      update.removeApprovalFor(psa.accountId(), psa.label());
    }

    // TODO(dborowitz): Don't use a label in NoteDb; just check when status
    // change happened.
    for (PatchSetApproval psa : normalized.unchanged()) {
      if (includeUnchanged || psa.isLegacySubmit()) {
        logger.atFine().log("Adding submit label %s", psa);
        update.putApprovalFor(psa.accountId(), psa.label(), psa.value());
      }
    }
  }

  private ChangeMessage message(ChangeContext ctx, CodeReviewCommit commit, CommitMergeStatus s) {
    requireNonNull(s, "CommitMergeStatus may not be null");
    String txt = s.getDescription();
    if (s == CommitMergeStatus.CLEAN_MERGE) {
      return message(ctx, commit.getPatchsetId(), txt);
    } else if (s == CommitMergeStatus.CLEAN_REBASE || s == CommitMergeStatus.CLEAN_PICK) {
      return message(ctx, commit.getPatchsetId(), txt + " as " + commit.name());
    } else if (s == CommitMergeStatus.SKIPPED_IDENTICAL_TREE) {
      return message(ctx, commit.getPatchsetId(), txt);
    } else if (s == CommitMergeStatus.ALREADY_MERGED) {
      // Best effort to mimic the message that would have happened had this
      // succeeded the first time around.
      switch (args.submitType) {
        case FAST_FORWARD_ONLY:
        case MERGE_ALWAYS:
        case MERGE_IF_NECESSARY:
          return message(ctx, commit, CommitMergeStatus.CLEAN_MERGE);
        case CHERRY_PICK:
          return message(ctx, commit, CommitMergeStatus.CLEAN_PICK);
        case REBASE_IF_NECESSARY:
        case REBASE_ALWAYS:
          return message(ctx, commit, CommitMergeStatus.CLEAN_REBASE);
        case INHERIT:
        default:
          throw new IllegalStateException(
              "unexpected submit type "
                  + args.submitType.toString()
                  + " for change "
                  + commit.change().getId());
      }
    } else {
      throw new IllegalStateException(
          "unexpected status "
              + s
              + " for change "
              + commit.change().getId()
              + "; expected to previously fail fast");
    }
  }

  private ChangeMessage message(ChangeContext ctx, PatchSet.Id psId, String body) {
    return ChangeMessagesUtil.newMessage(
        psId, ctx.getUser(), ctx.getWhen(), body, ChangeMessagesUtil.TAG_MERGED);
  }

  private void setMerged(ChangeContext ctx, ChangeMessage msg) {
    Change c = ctx.getChange();
    logger.atFine().log("Setting change %s merged", c.getId());
    c.setStatus(Change.Status.MERGED);
    c.setSubmissionId(args.submissionId.toString());

    // TODO(dborowitz): We need to be able to change the author of the message,
    // which is not the user from the update context. addMergedMessage was able
    // to do this in the past.
    if (msg != null) {
      args.cmUtil.addChangeMessage(ctx.getUpdate(msg.getPatchSetId()), msg);
    }
  }

  @Override
  public final void postUpdate(Context ctx) throws Exception {
    if (changeAlreadyMerged) {
      // TODO(dborowitz): This is suboptimal behavior in the presence of retries: postUpdate steps
      // will never get run for changes that submitted successfully on any but the final attempt.
      // This is primarily a temporary workaround for the fact that the submitter field is not
      // populated in the changeAlreadyMerged case.
      //
      // If we naively execute postUpdate even if the change is already merged when updateChange
      // being, then we are subject to a race where postUpdate steps are run twice if two submit
      // processes run at the same time.
      logger.atFine().log("Skipping post-update steps for change %s", getId());
      return;
    }
    postUpdateImpl(ctx);

    if (command != null) {
      args.tagCache.updateFastForward(
          getProject(), command.getRefName(), command.getOldId(), command.getNewId());
      // TODO(dborowitz): Move to BatchUpdate? Would also allow us to run once
      // per project even if multiple changes to refs/meta/config are submitted.
      if (RefNames.REFS_CONFIG.equals(getDest().branch())) {
        args.projectCache.evict(getProject());
        ProjectState p =
            args.projectCache.get(getProject()).orElseThrow(illegalState(getProject()));
        try (Repository git = args.repoManager.openRepository(getProject())) {
          git.setGitwebDescription(p.getProject().getDescription());
        } catch (IOException e) {
          logger.atSevere().withCause(e).log("cannot update description of %s", p.getName());
        }
      }
    }

    // Assume the change must have been merged at this point, otherwise we would
    // have failed fast in one of the other steps.
    try {
      args.mergedSenderFactory
          .create(
              ctx.getProject(),
              toMerge.change(),
              submitter.accountId(),
              ctx.getNotify(getId()),
              ctx.getRepoView())
          .sendAsync();
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Cannot email merged notification for %s", getId());
    }
    if (mergeResultRev != null && !args.dryrun) {
      args.changeMerged.fire(
          updatedChange,
          mergedPatchSet,
          args.accountCache.get(submitter.accountId()).orElse(null),
          args.mergeTip.getCurrentTip().name(),
          ctx.getWhen());
    }
  }

  /**
   * @see #updateRepo(RepoContext)
   * @param ctx
   */
  protected void updateRepoImpl(RepoContext ctx) throws Exception {}

  /**
   * @see #updateChange(ChangeContext)
   * @param ctx
   * @return a new patch set if one was created by the submit strategy, or null if not.
   */
  protected PatchSet updateChangeImpl(ChangeContext ctx) throws Exception {
    return null;
  }

  /**
   * @see #postUpdate(Context)
   * @param ctx
   */
  protected void postUpdateImpl(Context ctx) throws Exception {}

  /**
   * Amend the commit with gitlink update
   *
   * @param commit
   */
  protected CodeReviewCommit amendGitlink(CodeReviewCommit commit)
      throws IntegrationConflictException {
    if (!args.submoduleOp.hasSubscription(args.destBranch)) {
      return commit;
    }

    // Modify the commit with gitlink update
    try {
      return args.submoduleOp.amendGitlinksCommit(args.destBranch, commit);
    } catch (IOException e) {
      throw new StorageException(
          String.format("cannot update gitlink for the commit at branch %s", args.destBranch), e);
    } catch (SubmoduleConflictException e) {
      throw new IntegrationConflictException(
          String.format(
              "cannot update gitlink for the commit at branch %s: %s",
              args.destBranch, e.getMessage()),
          e);
    }
  }
}
