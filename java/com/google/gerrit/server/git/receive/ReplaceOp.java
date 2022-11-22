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
import static com.google.gerrit.server.change.ReviewerModifier.newReviewerInputFromCommitIdentity;
import static com.google.gerrit.server.mail.MailUtil.getRecipientsFromFooters;
import static com.google.gerrit.server.mail.MailUtil.getRecipientsFromReviewers;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.stream.Collectors.joining;
import static org.eclipse.jgit.lib.Constants.R_HEADS;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.PatchSetInfo;
import com.google.gerrit.entities.SubmissionId;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewerInput;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.approval.ApprovalCopier;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.change.ChangeKindCache;
import com.google.gerrit.server.change.EmailNewPatchSet;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.ReviewerModifier;
import com.google.gerrit.server.change.ReviewerModifier.InternalReviewerInput;
import com.google.gerrit.server.change.ReviewerModifier.ReviewerModification;
import com.google.gerrit.server.change.ReviewerModifier.ReviewerModificationList;
import com.google.gerrit.server.change.ReviewerOp;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.UrlFormatter;
import com.google.gerrit.server.extensions.events.CommentAdded;
import com.google.gerrit.server.extensions.events.RevisionCreated;
import com.google.gerrit.server.git.MergedByPushOp;
import com.google.gerrit.server.git.receive.ReceiveCommits.MagicBranchInput;
import com.google.gerrit.server.mail.MailUtil.MailRecipients;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.gerrit.server.update.RepoContext;
import com.google.gerrit.server.util.LabelVote;
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
        Change change,
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
        RequestScopePropagator requestScopePropagator);
  }

  private static final String CHANGE_IS_CLOSED = "change is closed";

  private final AccountCache accountCache;
  private final AccountResolver accountResolver;
  private final String anonymousCowardName;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeData.Factory changeDataFactory;
  private final ChangeKindCache changeKindCache;
  private final ChangeMessagesUtil cmUtil;
  private final EmailNewPatchSet.Factory emailNewPatchSetFactory;
  private final RevisionCreated revisionCreated;
  private final CommentAdded commentAdded;
  private final MergedByPushOp.Factory mergedByPushOpFactory;
  private final PatchSetUtil psUtil;
  private final ProjectCache projectCache;
  private final ReviewerModifier reviewerModifier;
  private final DynamicItem<UrlFormatter> urlFormatter;

  private final ProjectState projectState;
  private final Change change;
  private final boolean checkMergedInto;
  private final String mergeResultRevId;
  private final PatchSet.Id priorPatchSetId;
  private final ObjectId priorCommitId;
  private final PatchSet.Id patchSetId;
  private final ObjectId commitId;
  private final PatchSetInfo info;
  private final MagicBranchInput magicBranch;
  private final PushCertificate pushCertificate;
  private final RequestScopePropagator requestScopePropagator;
  private List<String> groups;

  private final Map<String, Short> approvals = new HashMap<>();
  private RevCommit commit;
  private ReceiveCommand cmd;
  private ChangeNotes notes;
  private PatchSet newPatchSet;
  private ChangeKind changeKind;
  private String mailMessage;
  private ApprovalCopier.Result approvalCopierResult;
  private String rejectMessage;
  private MergedByPushOp mergedByPushOp;
  private ReviewerModificationList reviewerAdditions;
  private MailRecipients oldRecipients;

  @Inject
  ReplaceOp(
      AccountCache accountCache,
      AccountResolver accountResolver,
      @AnonymousCowardName String anonymousCowardName,
      ApprovalsUtil approvalsUtil,
      ChangeData.Factory changeDataFactory,
      ChangeKindCache changeKindCache,
      ChangeMessagesUtil cmUtil,
      RevisionCreated revisionCreated,
      CommentAdded commentAdded,
      MergedByPushOp.Factory mergedByPushOpFactory,
      PatchSetUtil psUtil,
      ProjectCache projectCache,
      EmailNewPatchSet.Factory emailNewPatchSetFactory,
      ReviewerModifier reviewerModifier,
      DynamicItem<UrlFormatter> urlFormatter,
      @Assisted ProjectState projectState,
      @Assisted Change change,
      @Assisted boolean checkMergedInto,
      @Assisted @Nullable String mergeResultRevId,
      @Assisted("priorPatchSetId") PatchSet.Id priorPatchSetId,
      @Assisted("priorCommitId") ObjectId priorCommitId,
      @Assisted("patchSetId") PatchSet.Id patchSetId,
      @Assisted("commitId") ObjectId commitId,
      @Assisted PatchSetInfo info,
      @Assisted List<String> groups,
      @Assisted @Nullable MagicBranchInput magicBranch,
      @Assisted @Nullable PushCertificate pushCertificate,
      @Assisted RequestScopePropagator requestScopePropagator) {
    this.accountCache = accountCache;
    this.accountResolver = accountResolver;
    this.anonymousCowardName = anonymousCowardName;
    this.approvalsUtil = approvalsUtil;
    this.changeDataFactory = changeDataFactory;
    this.changeKindCache = changeKindCache;
    this.cmUtil = cmUtil;
    this.revisionCreated = revisionCreated;
    this.commentAdded = commentAdded;
    this.mergedByPushOpFactory = mergedByPushOpFactory;
    this.psUtil = psUtil;
    this.projectCache = projectCache;
    this.emailNewPatchSetFactory = emailNewPatchSetFactory;
    this.reviewerModifier = reviewerModifier;
    this.urlFormatter = urlFormatter;

    this.projectState = projectState;
    this.change = change;
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
    this.requestScopePropagator = requestScopePropagator;
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
      String mergedInto = findMergedInto(ctx, change.getDest().branch(), commit);
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
      if (magicBranch.ignoreAttentionSet) {
        update.ignoreFurtherAttentionSetUpdates();
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
        reviewerModifier.prepare(
            ctx.getNotes(),
            ctx.getUser(),
            getReviewerInputs(magicBranch, fromFooters, ctx.getChange(), info),
            true);
    Optional<ReviewerModification> reviewerError =
        reviewerAdditions.getFailures().stream().findFirst();
    if (reviewerError.isPresent()) {
      throw new UnprocessableEntityException(reviewerError.get().result.error);
    }
    reviewerAdditions.updateChange(ctx, newPatchSet);

    // Check if approvals are changing with this update. If so, add the current user (aka the
    // approver) as a reviewers because all approvers must also be reviewers.
    // Note that this is done separately as addReviewers is filtering out the change owner as a
    // reviewer which is needed in several other code paths.
    if (magicBranch != null && !magicBranch.labels.isEmpty()) {
      update.putReviewer(ctx.getAccountId(), REVIEWER);
    }

    approvalCopierResult =
        approvalsUtil.copyApprovalsToNewPatchSet(
            ctx.getNotes(), newPatchSet, ctx.getRevWalk(), ctx.getRepoView().getConfig(), update);

    mailMessage = insertChangeMessage(update, ctx, reviewMessage);
    if (mergedByPushOp == null) {
      resetChange(ctx);
    } else {
      mergedByPushOp.setPatchSetProvider(Providers.of(newPatchSet)).updateChange(ctx);
    }

    return true;
  }

  private ImmutableList<ReviewerInput> getReviewerInputs(
      @Nullable MagicBranchInput magicBranch,
      MailRecipients fromFooters,
      Change change,
      PatchSetInfo psInfo) {
    // Disable individual emails when adding reviewers, as all reviewers will receive the single
    // bulk new change email.
    Stream<ReviewerInput> inputs =
        Streams.concat(
            Streams.stream(
                newReviewerInputFromCommitIdentity(
                    change,
                    psInfo.getCommitId(),
                    psInfo.getAuthor().getAccount(),
                    NotifyHandling.NONE,
                    newPatchSet.uploader())),
            Streams.stream(
                newReviewerInputFromCommitIdentity(
                    change,
                    psInfo.getCommitId(),
                    psInfo.getCommitter().getAccount(),
                    NotifyHandling.NONE,
                    newPatchSet.uploader())));
    if (magicBranch != null) {
      inputs =
          Streams.concat(
              inputs,
              magicBranch.getCombinedReviewers(fromFooters).stream()
                  .map(r -> newReviewerInput(r, ReviewerState.REVIEWER)),
              magicBranch.getCombinedCcs(fromFooters).stream()
                  .map(r -> newReviewerInput(r, ReviewerState.CC)));
    }
    return inputs.collect(toImmutableList());
  }

  private static InternalReviewerInput newReviewerInput(String reviewer, ReviewerState state) {
    // Disable individual emails when adding reviewers, as all reviewers will receive the single
    // bulk new patch set email.
    InternalReviewerInput input =
        ReviewerModifier.newReviewerInput(reviewer, state, NotifyHandling.NONE);

    // Ignore failures for reasons like the reviewer being inactive or being unable to see the
    // change. See discussion in ChangeInserter.
    input.otherFailureBehavior = ReviewerModifier.FailureBehavior.IGNORE_EXCEPT_NOT_FOUND;

    return input;
  }

  private String insertChangeMessage(ChangeUpdate update, ChangeContext ctx, String reviewMessage) {
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
    approvalsUtil
        .formatApprovalCopierResult(approvalCopierResult, projectState.getLabelTypes())
        .ifPresent(
            msg -> {
              if (Strings.isNullOrEmpty(reviewMessage) || !reviewMessage.endsWith("\n")) {
                message.append("\n");
              }
              message.append("\n").append(msg);
            });
    boolean workInProgress = ctx.getChange().isWorkInProgress();
    if (magicBranch != null && magicBranch.workInProgress) {
      workInProgress = true;
    }
    return cmUtil.setChangeMessage(
        update, message.toString(), ChangeMessagesUtil.uploadedPatchSetTag(workInProgress));
  }

  @Nullable
  private String changeKindMessage(ChangeKind changeKind) {
    switch (changeKind) {
      case MERGE_FIRST_PARENT_UPDATE:
        return ": New merge patch set was added with a new first parent relative to Patch Set "
            + priorPatchSetId.get()
            + ".";
      case TRIVIAL_REBASE:
        return ": Patch Set " + priorPatchSetId.get() + " was rebased.";
      case NO_CHANGE:
        return ": New patch set was added with same tree, parent "
            + (commit.getParentCount() != 1 ? "trees" : "tree")
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

  private Map<String, PatchSetApproval> scanLabels(
      ChangeContext ctx, Map<String, Short> approvals) {
    Map<String, PatchSetApproval> current = new HashMap<>();
    // We optimize here and only retrieve current when approvals provided
    if (!approvals.isEmpty()) {
      for (PatchSetApproval a :
          approvalsUtil.byPatchSetUser(ctx.getNotes(), priorPatchSetId, ctx.getAccountId())) {
        if (a.isLegacySubmit()) {
          continue;
        }

        projectState
            .getLabelTypes()
            .byLabel(a.labelId())
            .ifPresent(l -> current.put(l.getName(), a));
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

    List<String> idList = ChangeUtil.getChangeIdsFromFooter(commit, urlFormatter.get());
    change.setKey(Change.key(idList.get(idList.size() - 1).trim()));
  }

  @Override
  public void postUpdate(PostUpdateContext ctx) throws Exception {
    reviewerAdditions.postUpdate(ctx);

    // TODO(dborowitz): Merge email templates so we only have to send one.
    emailNewPatchSetFactory
        .create(
            ctx,
            newPatchSet,
            mailMessage,
            approvalCopierResult.outdatedApprovals().stream()
                .map(ApprovalCopier.Result.ApprovalData::patchSetApproval)
                .collect(toImmutableSet()),
            Streams.concat(
                    oldRecipients.getReviewers().stream(),
                    reviewerAdditions.flattenResults(ReviewerOp.Result::addedReviewers).stream()
                        .map(PatchSetApproval::accountId))
                .collect(toImmutableSet()),
            Streams.concat(
                    oldRecipients.getCcOnly().stream(),
                    reviewerAdditions.flattenResults(ReviewerOp.Result::addedCCs).stream())
                .collect(toImmutableSet()),
            changeKind,
            notes.getMetaId())
        .setRequestScopePropagator(requestScopePropagator)
        .sendAsync();

    NotifyResolver.Result notify = ctx.getNotify(notes.getChangeId());
    revisionCreated.fire(
        ctx.getChangeData(notes), newPatchSet, ctx.getAccount(), ctx.getWhen(), notify);
    try {
      fireApprovalsEvent(ctx);
    } catch (Exception e) {
      logger.atWarning().withCause(e).log("comment-added event invocation failed");
    }
    if (mergedByPushOp != null) {
      mergedByPushOp.postUpdate(ctx);
    }
  }

  private void fireApprovalsEvent(PostUpdateContext ctx) {
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
        ctx.getChangeData(notes),
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

  public Optional<String> getOutdatedApprovalsMessage() {
    if (approvalCopierResult == null || approvalCopierResult.outdatedApprovals().isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(
        "The following approvals got outdated and were removed:\n"
            + approvalCopierResult.outdatedApprovals().stream()
                .map(ApprovalCopier.Result.ApprovalData::patchSetApproval)
                .map(
                    outdatedApproval ->
                        String.format(
                            "* %s by %s",
                            LabelVote.create(outdatedApproval.label(), outdatedApproval.value())
                                .format(),
                            getNameFor(outdatedApproval.accountId())))
                .sorted()
                .collect(joining("\n")));
  }

  private String getNameFor(Account.Id accountId) {
    Optional<Account> account = accountCache.get(accountId).map(AccountState::account);
    String name = null;
    if (account.isPresent()) {
      name = account.get().fullName();
      if (name == null) {
        name = account.get().preferredEmail();
      }
    }
    if (name == null) {
      name = anonymousCowardName + " #" + accountId;
    }
    return name;
  }

  public ReceiveCommand getCommand() {
    return cmd;
  }

  @Nullable
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
