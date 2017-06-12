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

package com.google.gerrit.server.git.strategy;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.git.BatchUpdate.RepoContext;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.GroupCollector;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.LabelNormalizer;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.git.SubmoduleException;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class SubmitStrategyOp extends BatchUpdate.Op {
  private static final Logger log = LoggerFactory.getLogger(SubmitStrategyOp.class);

  protected final SubmitStrategy.Arguments args;
  protected final CodeReviewCommit toMerge;

  private ReceiveCommand command;
  private PatchSetApproval submitter;
  private ObjectId mergeResultRev;
  private PatchSet mergedPatchSet;
  private Change updatedChange;
  private CodeReviewCommit alreadyMerged;

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

  protected final Branch.NameKey getDest() {
    return toMerge.change().getDest();
  }

  protected final Project.NameKey getProject() {
    return getDest().getParentKey();
  }

  @Override
  public final void updateRepo(RepoContext ctx) throws Exception {
    logDebug("{}#updateRepo for change {}", getClass().getSimpleName(), toMerge.change().getId());
    // Run the submit strategy implementation and record the merge tip state so
    // we can create the ref update.
    CodeReviewCommit tipBefore = args.mergeTip.getCurrentTip();
    alreadyMerged = getAlreadyMergedCommit(ctx);
    if (alreadyMerged == null) {
      updateRepoImpl(ctx);
    } else {
      logDebug("Already merged as {}", alreadyMerged.name());
    }
    CodeReviewCommit tipAfter = args.mergeTip.getCurrentTip();

    if (Objects.equals(tipBefore, tipAfter)) {
      logDebug("Did not move tip", getClass().getSimpleName());
      return;
    } else if (tipAfter == null) {
      logDebug("No merge tip, no update to perform");
      return;
    }
    logDebug("Moved tip from {} to {}", tipBefore, tipAfter);

    checkProjectConfig(ctx, tipAfter);

    // Needed by postUpdate, at which point mergeTip will have advanced further,
    // so it's easier to just snapshot the command.
    command =
        new ReceiveCommand(firstNonNull(tipBefore, ObjectId.zeroId()), tipAfter, getDest().get());
    ctx.addRefUpdate(command);
    args.submoduleOp.addBranchTip(getDest(), tipAfter);
  }

  private void checkProjectConfig(RepoContext ctx, CodeReviewCommit commit)
      throws IntegrationException {
    String refName = getDest().get();
    if (RefNames.REFS_CONFIG.equals(refName)) {
      logDebug("Loading new configuration from {}", RefNames.REFS_CONFIG);
      try {
        ProjectConfig cfg = new ProjectConfig(getProject());
        cfg.load(ctx.getRevWalk(), commit);
      } catch (Exception e) {
        throw new IntegrationException(
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

    Collection<Ref> refs = ctx.getRepository().getRefDatabase().getRefs(id.toRefPrefix()).values();
    List<CodeReviewCommit> commits = new ArrayList<>(refs.size());
    for (Ref ref : refs) {
      PatchSet.Id psId = PatchSet.Id.fromRef(ref.getName());
      if (psId == null) {
        continue;
      }
      try {
        CodeReviewCommit c = rw.parseCommit(ref.getObjectId());
        c.setPatchsetId(psId);
        commits.add(c);
      } catch (MissingObjectException | IncorrectObjectTypeException e) {
        continue; // Bogus ref, can't be merged into tip so we don't care.
      }
    }
    Collections.sort(
        commits, ReviewDbUtil.intKeyOrdering().reverse().onResultOf(c -> c.getPatchsetId()));
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
    args.commits.put(result);
    return result;
  }

  @Override
  public final boolean updateChange(ChangeContext ctx) throws Exception {
    logDebug("{}#updateChange for change {}", getClass().getSimpleName(), toMerge.change().getId());
    toMerge.setControl(ctx.getControl()); // Update change and notes from ctx.
    PatchSet.Id oldPsId = checkNotNull(toMerge.getPatchsetId());
    PatchSet.Id newPsId;

    if (alreadyMerged != null) {
      alreadyMerged.setControl(ctx.getControl());
      mergedPatchSet = getOrCreateAlreadyMergedPatchSet(ctx);
      newPsId = mergedPatchSet.getId();
    } else {
      PatchSet newPatchSet = updateChangeImpl(ctx);
      newPsId = checkNotNull(ctx.getChange().currentPatchSetId());
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
            checkNotNull(
                args.psUtil.get(ctx.getDb(), ctx.getNotes(), oldPsId),
                "missing old patch set %s",
                oldPsId);
      } else {
        PatchSet.Id n = newPatchSet.getId();
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
    CodeReviewCommit commit = args.commits.get(id);
    checkNotNull(commit, "missing commit for change " + id);
    CommitMergeStatus s = commit.getStatusCode();
    checkNotNull(s, "status not set for change " + id + " expected to previously fail fast");
    logDebug("Status of change {} ({}) on {}: {}", id, commit.name(), c.getDest(), s);
    setApproval(ctx, args.caller);

    mergeResultRev =
        alreadyMerged == null
            ? args.mergeTip.getMergeResults().get(commit)
            // Our fixup code is not smart enough to find a merge commit
            // corresponding to the merge result. This results in a different
            // ChangeMergedEvent in the fixup case, but we'll just live with that.
            : alreadyMerged;
    try {
      setMerged(ctx, message(ctx, commit, s));
    } catch (OrmException err) {
      String msg = "Error updating change status for " + id;
      log.error(msg, err);
      args.commits.logProblem(id, msg);
      // It's possible this happened before updating anything in the db, but
      // it's hard to know for sure, so just return true below to be safe.
    }
    updatedChange = c;
    return true;
  }

  private PatchSet getOrCreateAlreadyMergedPatchSet(ChangeContext ctx)
      throws IOException, OrmException {
    PatchSet.Id psId = alreadyMerged.getPatchsetId();
    logDebug("Fixing up already-merged patch set {}", psId);
    PatchSet prevPs = args.psUtil.current(ctx.getDb(), ctx.getNotes());
    ctx.getRevWalk().parseBody(alreadyMerged);
    ctx.getChange()
        .setCurrentPatchSet(
            psId, alreadyMerged.getShortMessage(), ctx.getChange().getOriginalSubject());
    PatchSet existing = args.psUtil.get(ctx.getDb(), ctx.getNotes(), psId);
    if (existing != null) {
      logDebug("Patch set row exists, only updating change");
      return existing;
    }
    // No patch set for the already merged commit, although we know it came form
    // a patch set ref. Fix up the database. Note that this uses the current
    // user as the uploader, which is as good a guess as any.
    List<String> groups =
        prevPs != null ? prevPs.getGroups() : GroupCollector.getDefaultGroups(alreadyMerged);
    return args.psUtil.insert(
        ctx.getDb(),
        ctx.getRevWalk(),
        ctx.getUpdate(psId),
        psId,
        alreadyMerged,
        false,
        groups,
        null);
  }

  private void setApproval(ChangeContext ctx, IdentifiedUser user) throws OrmException {
    Change.Id id = ctx.getChange().getId();
    List<SubmitRecord> records = args.commits.getSubmitRecords(id);
    PatchSet.Id oldPsId = toMerge.getPatchsetId();
    PatchSet.Id newPsId = ctx.getChange().currentPatchSetId();

    logDebug("Add approval for " + id);
    ChangeUpdate origPsUpdate = ctx.getUpdate(oldPsId);
    origPsUpdate.putReviewer(user.getAccountId(), REVIEWER);
    LabelNormalizer.Result normalized = approve(ctx, origPsUpdate);

    ChangeUpdate newPsUpdate = ctx.getUpdate(newPsId);
    newPsUpdate.merge(args.submissionId, records);
    // If the submit strategy created a new revision (rebase, cherry-pick), copy
    // approvals as well.
    if (!newPsId.equals(oldPsId)) {
      saveApprovals(normalized, ctx, newPsUpdate, true);
      submitter = convertPatchSet(newPsId).apply(submitter);
    }
  }

  private LabelNormalizer.Result approve(ChangeContext ctx, ChangeUpdate update)
      throws OrmException {
    PatchSet.Id psId = update.getPatchSetId();
    Map<PatchSetApproval.Key, PatchSetApproval> byKey = new HashMap<>();
    for (PatchSetApproval psa :
        args.approvalsUtil.byPatchSet(ctx.getDb(), ctx.getControl(), psId)) {
      byKey.put(psa.getKey(), psa);
    }

    submitter =
        ApprovalsUtil.newApproval(psId, ctx.getUser(), LabelId.legacySubmit(), 1, ctx.getWhen());
    byKey.put(submitter.getKey(), submitter);

    // Flatten out existing approvals for this patch set based upon the current
    // permissions. Once the change is closed the approvals are not updated at
    // presentation view time, except for zero votes used to indicate a reviewer
    // was added. So we need to make sure votes are accurate now. This way if
    // permissions get modified in the future, historical records stay accurate.
    LabelNormalizer.Result normalized =
        args.labelNormalizer.normalize(ctx.getControl(), byKey.values());
    update.putApproval(submitter.getLabel(), submitter.getValue());
    saveApprovals(normalized, ctx, update, false);
    return normalized;
  }

  private void saveApprovals(
      LabelNormalizer.Result normalized,
      ChangeContext ctx,
      ChangeUpdate update,
      boolean includeUnchanged)
      throws OrmException {
    PatchSet.Id psId = update.getPatchSetId();
    ctx.getDb().patchSetApprovals().upsert(convertPatchSet(normalized.getNormalized(), psId));
    ctx.getDb().patchSetApprovals().upsert(zero(convertPatchSet(normalized.deleted(), psId)));
    for (PatchSetApproval psa : normalized.updated()) {
      update.putApprovalFor(psa.getAccountId(), psa.getLabel(), psa.getValue());
    }
    for (PatchSetApproval psa : normalized.deleted()) {
      update.removeApprovalFor(psa.getAccountId(), psa.getLabel());
    }

    // TODO(dborowitz): Don't use a label in NoteDb; just check when status
    // change happened.
    for (PatchSetApproval psa : normalized.unchanged()) {
      if (includeUnchanged || psa.isLegacySubmit()) {
        logDebug("Adding submit label " + psa);
        update.putApprovalFor(psa.getAccountId(), psa.getLabel(), psa.getValue());
      }
    }
  }

  private static Function<PatchSetApproval, PatchSetApproval> convertPatchSet(
      final PatchSet.Id psId) {
    return psa -> {
      if (psa.getPatchSetId().equals(psId)) {
        return psa;
      }
      return new PatchSetApproval(psId, psa);
    };
  }

  private static Iterable<PatchSetApproval> convertPatchSet(
      Iterable<PatchSetApproval> approvals, PatchSet.Id psId) {
    return Iterables.transform(approvals, convertPatchSet(psId));
  }

  private static Iterable<PatchSetApproval> zero(Iterable<PatchSetApproval> approvals) {
    return Iterables.transform(
        approvals,
        a -> {
          PatchSetApproval copy = new PatchSetApproval(a.getPatchSetId(), a);
          copy.setValue((short) 0);
          return copy;
        });
  }

  private String getByAccountName() {
    checkNotNull(submitter, "getByAccountName called before submitter populated");
    Account account = args.accountCache.get(submitter.getAccountId()).getAccount();
    if (account != null && account.getFullName() != null) {
      return " by " + account.getFullName();
    }
    return "";
  }

  private ChangeMessage message(ChangeContext ctx, CodeReviewCommit commit, CommitMergeStatus s)
      throws OrmException {
    checkNotNull(s, "CommitMergeStatus may not be null");
    String txt = s.getMessage();
    if (s == CommitMergeStatus.CLEAN_MERGE) {
      return message(ctx, commit.getPatchsetId(), txt + getByAccountName());
    } else if (s == CommitMergeStatus.CLEAN_REBASE || s == CommitMergeStatus.CLEAN_PICK) {
      return message(
          ctx, commit.getPatchsetId(), txt + " as " + commit.name() + getByAccountName());
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

  private ChangeMessage message(ChangeContext ctx, PatchSet.Id psId, String body)
      throws OrmException {
    return ChangeMessagesUtil.newMessage(
        ctx.getDb(), psId, ctx.getUser(), ctx.getWhen(), body, ChangeMessagesUtil.TAG_MERGED);
  }

  private void setMerged(ChangeContext ctx, ChangeMessage msg) throws OrmException {
    Change c = ctx.getChange();
    ReviewDb db = ctx.getDb();
    logDebug("Setting change {} merged", c.getId());
    c.setStatus(Change.Status.MERGED);
    c.setSubmissionId(args.submissionId.toStringForStorage());

    // TODO(dborowitz): We need to be able to change the author of the message,
    // which is not the user from the update context. addMergedMessage was able
    // to do this in the past.
    if (msg != null) {
      args.cmUtil.addChangeMessage(db, ctx.getUpdate(msg.getPatchSetId()), msg);
    }
  }

  @Override
  public final void postUpdate(Context ctx) throws Exception {
    postUpdateImpl(ctx);

    if (command != null) {
      args.tagCache.updateFastForward(
          getProject(), command.getRefName(), command.getOldId(), command.getNewId());
      // TODO(dborowitz): Move to BatchUpdate? Would also allow us to run once
      // per project even if multiple changes to refs/meta/config are submitted.
      if (RefNames.REFS_CONFIG.equals(getDest().get())) {
        args.projectCache.evict(getProject());
        ProjectState p = args.projectCache.get(getProject());
        args.repoManager.setProjectDescription(
            p.getProject().getNameKey(), p.getProject().getDescription());
      }
    }

    // Assume the change must have been merged at this point, otherwise we would
    // have failed fast in one of the other steps.
    try {
      args.mergedSenderFactory
          .create(ctx.getProject(), getId(), submitter.getAccountId(), args.notifyHandling)
          .sendAsync();
    } catch (Exception e) {
      log.error("Cannot email merged notification for " + getId(), e);
    }
    if (mergeResultRev != null && !args.dryrun) {
      args.changeMerged.fire(
          updatedChange,
          mergedPatchSet,
          args.accountCache.get(submitter.getAccountId()).getAccount(),
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
  protected CodeReviewCommit amendGitlink(CodeReviewCommit commit) throws IntegrationException {
    if (!args.submoduleOp.hasSubscription(args.destBranch)) {
      return commit;
    }

    // Modify the commit with gitlink update
    try {
      return args.submoduleOp.composeGitlinksCommit(args.destBranch, commit);
    } catch (SubmoduleException | IOException e) {
      throw new IntegrationException(
          "cannot update gitlink for the commit at branch: " + args.destBranch);
    }
  }

  protected final void logDebug(String msg, Object... args) {
    if (log.isDebugEnabled()) {
      log.debug(this.args.submissionId + msg, args);
    }
  }

  protected final void logWarn(String msg, Throwable t) {
    if (log.isWarnEnabled()) {
      log.warn(args.submissionId + msg, t);
    }
  }

  protected void logError(String msg, Throwable t) {
    if (log.isErrorEnabled()) {
      if (t != null) {
        log.error(args.submissionId + msg, t);
      } else {
        log.error(args.submissionId + msg);
      }
    }
  }

  protected void logError(String msg) {
    logError(msg, null);
  }
}
