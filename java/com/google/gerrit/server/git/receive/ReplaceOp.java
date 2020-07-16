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

package com.google.gerrit.server.git.receive;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.common.FooterConstants.CHANGE_ID;
import static com.google.gerrit.server.change.ReviewerAdder.newAddReviewerInputFromCommitIdentity;
import static com.google.gerrit.server.mail.MailUtil.getRecipientsFromFooters;
import static com.google.gerrit.server.mail.MailUtil.getRecipientsFromReviewers;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static org.eclipse.jgit.lib.Constants.R_HEADS;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.PatchSetInfo;
import com.google.gerrit.entities.SubmissionId;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.change.AddReviewersOp;
import com.google.gerrit.server.change.ChangeKindCache;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.ReviewerAdder;
import com.google.gerrit.server.change.ReviewerAdder.InternalAddReviewerInput;
import com.google.gerrit.server.change.ReviewerAdder.ReviewerAddition;
import com.google.gerrit.server.change.ReviewerAdder.ReviewerAdditionList;
import com.google.gerrit.server.config.SendEmailExecutor;
import com.google.gerrit.server.extensions.events.CommentAdded;
import com.google.gerrit.server.extensions.events.RevisionCreated;
import com.google.gerrit.server.git.MergedByPushOp;
import com.google.gerrit.server.git.receive.ReceiveCommits.MagicBranchInput;
import com.google.gerrit.server.mail.MailUtil.MailRecipients;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.mail.send.ReplacePatchSetSender;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.RepoContext;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.util.Providers;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushCertificate;
import org.eclipse.jgit.transport.ReceiveCommand;

public class ReplaceOp implements BatchUpdateOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    ReplaceOp create(
        ProjectState projectState,
        BranchNameKey dest,
        boolean checkMergedInto,
        @Nullable String mergeResultRevId,
        @Assisted("priorPatchSetId") PatchSet.Id priorPatchSetId,
        @Assisted("priorCommitId") ObjectId priorCommit,
        @Assisted("patchSetId") PatchSet.Id patchSetId,
        @Assisted("commitId") ObjectId commitId,
        PatchSetInfo info,
        List<String> groups,
        @Nullable MagicBranchInput magicBranch,
        @Nullable PushCertificate pushCertificate,
        Change change);
  }

  private static final String CHANGE_IS_CLOSED = "change is closed";

  private final AccountResolver accountResolver;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeData.Factory changeDataFactory;
  private final ChangeKindCache changeKindCache;
  private final ChangeMessagesUtil cmUtil;
  private final ExecutorService sendEmailExecutor;
  private final RevisionCreated revisionCreated;
  private final CommentAdded commentAdded;
  private final MergedByPushOp.Factory mergedByPushOpFactory;
  private final PatchSetUtil psUtil;
  private final ReplacePatchSetSender.Factory replacePatchSetFactory;
  private final ProjectCache projectCache;
  private final ReviewerAdder reviewerAdder;
  private final Change change;
  private final MessageIdGenerator messageIdGenerator;

  private final ProjectState projectState;
  private final BranchNameKey dest;
  private final boolean checkMergedInto;
  private final String mergeResultRevId;
  private final PatchSet.Id priorPatchSetId;
  private final ObjectId priorCommitId;
  private final PatchSet.Id patchSetId;
  private final ObjectId commitId;
  private final PatchSetInfo info;
  private final MagicBranchInput magicBranch;
  private final PushCertificate pushCertificate;
  private List<String> groups;

  private final Map<String, Short> approvals = new HashMap<>();
  private RevCommit commit;
  private ReceiveCommand cmd;
  private ChangeNotes notes;
  private PatchSet newPatchSet;
  private ChangeKind changeKind;
  private ChangeMessage msg;
  private String rejectMessage;
  private MergedByPushOp mergedByPushOp;
  private RequestScopePropagator requestScopePropagator;
  private ReviewerAdditionList reviewerAdditions;
  private MailRecipients oldRecipients;

  @Inject
  ReplaceOp(
      AccountResolver accountResolver,
      ApprovalsUtil approvalsUtil,
      ChangeData.Factory changeDataFactory,
      ChangeKindCache changeKindCache,
      ChangeMessagesUtil cmUtil,
      RevisionCreated revisionCreated,
      CommentAdded commentAdded,
      MergedByPushOp.Factory mergedByPushOpFactory,
      PatchSetUtil psUtil,
      ReplacePatchSetSender.Factory replacePatchSetFactory,
      ProjectCache projectCache,
      @SendEmailExecutor ExecutorService sendEmailExecutor,
      ReviewerAdder reviewerAdder,
      Change change,
      MessageIdGenerator messageIdGenerator,
      @Assisted ProjectState projectState,
      @Assisted BranchNameKey dest,
      @Assisted boolean checkMergedInto,
      @Assisted @Nullable String mergeResultRevId,
      @Assisted("priorPatchSetId") PatchSet.Id priorPatchSetId,
      @Assisted("priorCommitId") ObjectId priorCommitId,
      @Assisted("patchSetId") PatchSet.Id patchSetId,
      @Assisted("commitId") ObjectId commitId,
      @Assisted PatchSetInfo info,
      @Assisted List<String> groups,
      @Assisted @Nullable MagicBranchInput magicBranch,
      @Assisted @Nullable PushCertificate pushCertificate) {
    this.accountResolver = accountResolver;
    this.approvalsUtil = approvalsUtil;
    this.changeDataFactory = changeDataFactory;
    this.changeKindCache = changeKindCache;
    this.cmUtil = cmUtil;
    this.revisionCreated = revisionCreated;
    this.commentAdded = commentAdded;
    this.mergedByPushOpFactory = mergedByPushOpFactory;
    this.psUtil = psUtil;
    this.replacePatchSetFactory = replacePatchSetFactory;
    this.projectCache = projectCache;
    this.sendEmailExecutor = sendEmailExecutor;
    this.reviewerAdder = reviewerAdder;
    this.change = change;
    this.messageIdGenerator = messageIdGenerator;

    this.projectState = projectState;
    this.dest = dest;
    this.checkMergedInto = checkMergedInto;
    this.mergeResultRevId = mergeResultRevId;
    this.priorPatchSetId = priorPatchSetId;
    this.priorCommitId = priorCommitId.copy();
    this.patchSetId = patchSetId;
    this.commitId = commitId.copy();
    this.info = info;
    this.groups = groups;
    this.magicBranch = magicBranch;
    this.pushCertificate = pushCertificate;
  }

  @Override
  public void updateRepo(RepoContext ctx) throws Exception {
    commit = ctx.getRevWalk().parseCommit(commitId);
    ctx.getRevWalk().parseBody(commit);
    changeKind =
        changeKindCache.getChangeKind(
            projectState.getNameKey(),
            ctx.getRevWalk(),
            ctx.getRepoView().getConfig(),
            priorCommitId,
            commitId);

    if (checkMergedInto) {
      String mergedInto = findMergedInto(ctx, dest.branch(), commit);
      if (mergedInto != null) {
        mergedByPushOp =
            mergedByPushOpFactory.create(
                requestScopePropagator,
                patchSetId,
                new SubmissionId(change),
                mergedInto,
                mergeResultRevId);
      }
    }

    cmd = new ReceiveCommand(ObjectId.zeroId(), commitId, patchSetId.toRefName());
    ctx.addRefUpdate(cmd);
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws RestApiException, IOException, PermissionBackendException, ConfigInvalidException,
          ValidationException {
    notes = ctx.getNotes();
    Change change = notes.getChange();
    if (change == null || change.isClosed()) {
      rejectMessage = CHANGE_IS_CLOSED;
      return false;
    }
    if (groups.isEmpty()) {
      PatchSet prevPs = psUtil.current(notes);
      groups = prevPs != null ? prevPs.groups() : ImmutableList.of();
    }

    ChangeData cd = changeDataFactory.create(ctx.getNotes());
    oldRecipients = getRecipientsFromReviewers(cd.reviewers());

    ChangeUpdate update = ctx.getUpdate(patchSetId);
    update.setSubjectForCommit("Create patch set " + patchSetId.get());

    String reviewMessage = null;
    String psDescription = null;
    if (magicBranch != null) {
      reviewMessage = magicBranch.message;
      psDescription = magicBranch.message;
      approvals.putAll(magicBranch.labels);
      Set<String> hashtags = magicBranch.hashtags;
      if (hashtags != null && !hashtags.isEmpty()) {
        hashtags.addAll(notes.getHashtags());
        update.setHashtags(hashtags);
      }
      if (magicBranch.topic != null && !magicBranch.topic.equals(ctx.getChange().getTopic())) {
        try {
          update.setTopic(magicBranch.topic);
        } catch (ValidationException ex) {
          throw new BadRequestException(ex.getMessage());
        }
      }
      if (magicBranch.removePrivate) {
        change.setPrivate(false);
        update.setPrivate(false);
      } else if (magicBranch.isPrivate) {
        change.setPrivate(true);
        update.setPrivate(true);
      }
      if (magicBranch.ready) {
        change.setWorkInProgress(false);
        change.setReviewStarted(true);
        update.setWorkInProgress(false);
      } else if (magicBranch.workInProgress) {
        change.setWorkInProgress(true);
        update.setWorkInProgress(true);
      }
    }

    newPatchSet =
        psUtil.insert(
            ctx.getRevWalk(),
            update,
            patchSetId,
            commitId,
            groups,
            pushCertificate != null ? pushCertificate.toTextWithSignature() : null,
            psDescription);

    update.setPsDescription(psDescription);
    MailRecipients fromFooters = getRecipientsFromFooters(accountResolver, commit.getFooterLines());
    approvalsUtil.addApprovalsForNewPatchSet(
        update, projectState.getLabelTypes(), newPatchSet, ctx.getUser(), approvals);

    reviewerAdditions =
        reviewerAdder.prepare(
            ctx.getNotes(),
            ctx.getUser(),
            getReviewerInputs(magicBranch, fromFooters, ctx.getChange(), info),
            true);
    Optional<ReviewerAddition> reviewerError = reviewerAdditions.getFailures().stream().findFirst();
    if (reviewerError.isPresent()) {
      throw new UnprocessableEntityException(reviewerError.get().result.error);
    }
    reviewerAdditions.updateChange(ctx, newPatchSet);

    // Check if approvals are changing in with this update. If so, add current user to reviewers.
    // Note that this is done separately as addReviewers is filtering out the change owner as
    // reviewer which is needed in several other code paths.
    if (magicBranch != null && !magicBranch.labels.isEmpty()) {
      update.putReviewer(ctx.getAccountId(), REVIEWER);
    }

    msg = createChangeMessage(ctx, reviewMessage);
    cmUtil.addChangeMessage(update, msg);

    if (mergedByPushOp == null) {
      resetChange(ctx);
    } else {
      mergedByPushOp.setPatchSetProvider(Providers.of(newPatchSet)).updateChange(ctx);
    }

    return true;
  }

  private static ImmutableList<AddReviewerInput> getReviewerInputs(
      @Nullable MagicBranchInput magicBranch,
      MailRecipients fromFooters,
      Change change,
      PatchSetInfo psInfo) {
    // Disable individual emails when adding reviewers, as all reviewers will receive the single
    // bulk new change email.
    Stream<AddReviewerInput> inputs =
        Streams.concat(
            Streams.stream(
                newAddReviewerInputFromCommitIdentity(
                    change,
                    psInfo.getCommitId(),
                    psInfo.getAuthor().getAccount(),
                    NotifyHandling.NONE)),
            Streams.stream(
                newAddReviewerInputFromCommitIdentity(
                    change,
                    psInfo.getCommitId(),
                    psInfo.getCommitter().getAccount(),
                    NotifyHandling.NONE)));
    if (magicBranch != null) {
      inputs =
          Streams.concat(
              inputs,
              magicBranch.getCombinedReviewers(fromFooters).stream()
                  .map(r -> newAddReviewerInput(r, ReviewerState.REVIEWER)),
              magicBranch.getCombinedCcs(fromFooters).stream()
                  .map(r -> newAddReviewerInput(r, ReviewerState.CC)));
    }
    return inputs.collect(toImmutableList());
  }

  private static InternalAddReviewerInput newAddReviewerInput(
      String reviewer, ReviewerState state) {
    // Disable individual emails when adding reviewers, as all reviewers will receive the single
    // bulk new patch set email.
    InternalAddReviewerInput input =
        ReviewerAdder.newAddReviewerInput(reviewer, state, NotifyHandling.NONE);

    // Ignore failures for reasons like the reviewer being inactive or being unable to see the
    // change. See discussion in ChangeInserter.
    input.otherFailureBehavior = ReviewerAdder.FailureBehavior.IGNORE;

    return input;
  }

  private ChangeMessage createChangeMessage(ChangeContext ctx, String reviewMessage)
      throws IOException {
    String approvalMessage =
        ApprovalsUtil.renderMessageWithApprovals(
            patchSetId.get(), approvals, scanLabels(ctx, approvals));
    String kindMessage = changeKindMessage(changeKind);
    StringBuilder message = new StringBuilder(approvalMessage);
    if (!Strings.isNullOrEmpty(kindMessage)) {
      message.append(kindMessage);
    } else {
      message.append('.');
    }
    if (!Strings.isNullOrEmpty(reviewMessage)) {
      message.append("\n\n").append(reviewMessage);
    }
    boolean workInProgress = ctx.getChange().isWorkInProgress();
    if (magicBranch != null && magicBranch.workInProgress) {
      workInProgress = true;
    }
    return ChangeMessagesUtil.newMessage(
        patchSetId,
        ctx.getUser(),
        ctx.getWhen(),
        message.toString(),
        ChangeMessagesUtil.uploadedPatchSetTag(workInProgress));
  }

  private String changeKindMessage(ChangeKind changeKind) {
    switch (changeKind) {
      case MERGE_FIRST_PARENT_UPDATE:
        return ": New merge patch set was added with a new first parent relative to Patch Set "
            + priorPatchSetId.get()
            + ".";
      case TRIVIAL_REBASE:
        return ": Patch Set " + priorPatchSetId.get() + " was rebased.";
      case NO_CHANGE:
        return ": New patch set was added with same tree, parent"
            + (commit.getParentCount() != 1 ? "s" : "")
            + ", and commit message as Patch Set "
            + priorPatchSetId.get()
            + ".";
      case NO_CODE_CHANGE:
        return ": Commit message was updated.";
      case REWORK:
      default:
        return null;
    }
  }

  private Map<String, PatchSetApproval> scanLabels(ChangeContext ctx, Map<String, Short> approvals)
      throws IOException {
    Map<String, PatchSetApproval> current = new HashMap<>();
    // We optimize here and only retrieve current when approvals provided
    if (!approvals.isEmpty()) {
      for (PatchSetApproval a :
          approvalsUtil.byPatchSetUser(
              ctx.getNotes(),
              priorPatchSetId,
              ctx.getAccountId(),
              ctx.getRevWalk(),
              ctx.getRepoView().getConfig())) {
        if (a.isLegacySubmit()) {
          continue;
        }

        LabelType lt = projectState.getLabelTypes().byLabel(a.labelId());
        if (lt != null) {
          current.put(lt.getName(), a);
        }
      }
    }
    return current;
  }

  private void resetChange(ChangeContext ctx) {
    Change change = ctx.getChange();
    if (!change.currentPatchSetId().equals(priorPatchSetId)) {
      return;
    }

    if (magicBranch != null && magicBranch.topic != null) {
      change.setTopic(magicBranch.topic);
    }
    change.setStatus(Change.Status.NEW);
    change.setCurrentPatchSet(info);

    List<String> idList = commit.getFooterLines(CHANGE_ID);
    change.setKey(Change.key(idList.get(idList.size() - 1).trim()));
  }

  @Override
  public void postUpdate(Context ctx) throws Exception {
    reviewerAdditions.postUpdate(ctx);
    if (changeKind != ChangeKind.TRIVIAL_REBASE) {
      // TODO(dborowitz): Merge email templates so we only have to send one.
      Runnable e = new ReplaceEmailTask(ctx);
      if (requestScopePropagator != null) {
        @SuppressWarnings("unused")
        Future<?> possiblyIgnoredError = sendEmailExecutor.submit(requestScopePropagator.wrap(e));
      } else {
        e.run();
      }
    }
    NotifyResolver.Result notify = ctx.getNotify(notes.getChangeId());
    revisionCreated.fire(notes.getChange(), newPatchSet, ctx.getAccount(), ctx.getWhen(), notify);
    try {
      fireApprovalsEvent(ctx);
    } catch (Exception e) {
      logger.atWarning().withCause(e).log("comment-added event invocation failed");
    }
    if (mergedByPushOp != null) {
      mergedByPushOp.postUpdate(ctx);
    }
  }

  private class ReplaceEmailTask implements Runnable {
    private final Context ctx;

    private ReplaceEmailTask(Context ctx) {
      this.ctx = ctx;
    }

    @Override
    public void run() {
      try {
        ReplacePatchSetSender emailSender =
            replacePatchSetFactory.create(projectState.getNameKey(), notes.getChangeId());
        emailSender.setFrom(ctx.getAccount().account().id());
        emailSender.setPatchSet(newPatchSet, info);
        emailSender.setChangeMessage(msg.getMessage(), ctx.getWhen());
        emailSender.setNotify(ctx.getNotify(notes.getChangeId()));
        emailSender.addReviewers(
            Streams.concat(
                    oldRecipients.getReviewers().stream(),
                    reviewerAdditions.flattenResults(AddReviewersOp.Result::addedReviewers).stream()
                        .map(PatchSetApproval::accountId))
                .collect(toImmutableSet()));
        emailSender.addExtraCC(
            Streams.concat(
                    oldRecipients.getCcOnly().stream(),
                    reviewerAdditions.flattenResults(AddReviewersOp.Result::addedCCs).stream())
                .collect(toImmutableSet()));
        emailSender.setMessageId(
            messageIdGenerator.fromChangeUpdate(ctx.getRepoView(), patchSetId));
        // TODO(dborowitz): Support byEmail
        emailSender.send();
      } catch (Exception e) {
        logger.atSevere().withCause(e).log(
            "Cannot send email for new patch set %s", newPatchSet.id());
      }
    }

    @Override
    public String toString() {
      return "send-email newpatchset";
    }
  }

  private void fireApprovalsEvent(Context ctx) {
    if (approvals.isEmpty()) {
      return;
    }
    /* For labels that are not set in this operation, show the "current" value
     * of 0, and no oldValue as the value was not modified by this operation.
     * For labels that are set in this operation, the value was modified, so
     * show a transition from an oldValue of 0 to the new value.
     */
    List<LabelType> labels =
        projectCache
            .get(ctx.getProject())
            .orElseThrow(illegalState(ctx.getProject()))
            .getLabelTypes(notes)
            .getLabelTypes();
    Map<String, Short> allApprovals = new HashMap<>();
    Map<String, Short> oldApprovals = new HashMap<>();
    for (LabelType lt : labels) {
      allApprovals.put(lt.getName(), (short) 0);
      oldApprovals.put(lt.getName(), null);
    }
    for (Map.Entry<String, Short> entry : approvals.entrySet()) {
      if (entry.getValue() != 0) {
        allApprovals.put(entry.getKey(), entry.getValue());
        oldApprovals.put(entry.getKey(), (short) 0);
      }
    }
    commentAdded.fire(
        notes.getChange(),
        newPatchSet,
        ctx.getAccount(),
        null,
        allApprovals,
        oldApprovals,
        ctx.getWhen());
  }

  public PatchSet getPatchSet() {
    return newPatchSet;
  }

  public Change getChange() {
    return notes.getChange();
  }

  public String getRejectMessage() {
    return rejectMessage;
  }

  public ReceiveCommand getCommand() {
    return cmd;
  }

  public ReplaceOp setRequestScopePropagator(RequestScopePropagator requestScopePropagator) {
    this.requestScopePropagator = requestScopePropagator;
    return this;
  }

  private static String findMergedInto(Context ctx, String first, RevCommit commit) {
    try {
      RevWalk rw = ctx.getRevWalk();
      Optional<ObjectId> firstId = ctx.getRepoView().getRef(first);
      if (firstId.isPresent() && rw.isMergedInto(commit, rw.parseCommit(firstId.get()))) {
        return first;
      }

      for (Map.Entry<String, ObjectId> e : ctx.getRepoView().getRefs(R_HEADS).entrySet()) {
        if (rw.isMergedInto(commit, rw.parseCommit(e.getValue()))) {
          return R_HEADS + e.getKey();
        }
      }
      return null;
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Can't check for already submitted change");
      return null;
    }
  }
}
