// Copyright (C) 2013 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.entities.Change.INITIAL_PATCH_SET_ID;
import static com.google.gerrit.server.change.ReviewerModifier.newReviewerInputFromCommitIdentity;
import static com.google.gerrit.server.mail.EmailFactories.REVIEW_REQUESTED;
import static com.google.gerrit.server.notedb.ChangeUpdate.MAX_CUSTOM_KEYED_VALUES;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.PatchSetInfo;
import com.google.gerrit.entities.SubmissionId;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.change.ReviewerModifier.InternalReviewerInput;
import com.google.gerrit.server.change.ReviewerModifier.ReviewerModification;
import com.google.gerrit.server.change.ReviewerModifier.ReviewerModificationList;
import com.google.gerrit.server.config.SendEmailExecutor;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.extensions.events.CommentAdded;
import com.google.gerrit.server.extensions.events.RevisionCreated;
import com.google.gerrit.server.git.GroupCollector;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.mail.EmailFactories;
import com.google.gerrit.server.mail.send.ChangeEmail;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import com.google.gerrit.server.mail.send.StartReviewChangeEmailDecorator;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.AutoMerger;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.ssh.NoSshInfo;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.InsertChangeOp;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.gerrit.server.update.RepoContext;
import com.google.gerrit.server.util.CommitMessageUtil;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

public class ChangeInserter implements InsertChangeOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    ChangeInserter create(Change.Id cid, ObjectId commitId, String refName);
  }

  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final PatchSetUtil psUtil;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeMessagesUtil cmUtil;
  private final EmailFactories emailFactories;
  private final ExecutorService sendEmailExecutor;
  private final CommitValidators.Factory commitValidatorsFactory;
  private final RevisionCreated revisionCreated;
  private final CommentAdded commentAdded;
  private final ReviewerModifier reviewerModifier;
  private final MessageIdGenerator messageIdGenerator;
  private final AutoMerger autoMerger;
  private final ChangeUtil changeUtil;

  private final Change.Id changeId;
  private final PatchSet.Id psId;
  private final ObjectId commitId;
  private final String refName;

  // Fields exposed as setters.
  private PatchSet.Id cherryPickOf;
  private Change.Status status;
  private String topic;
  private String message;
  private String patchSetDescription;
  private boolean isPrivate;
  private boolean workInProgress;
  private List<String> groups = Collections.emptyList();
  private ImmutableListMultimap<String, String> validationOptions = ImmutableListMultimap.of();
  private ImmutableMap<String, String> customKeyedValues = ImmutableMap.of();
  private boolean validate = true;
  private Map<String, Short> approvals;
  private RequestScopePropagator requestScopePropagator;
  private boolean fireRevisionCreated;
  private boolean sendMail;
  private boolean updateRef;
  private Change.Id revertOf;
  private ImmutableList<InternalReviewerInput> reviewerInputs;

  // Fields set during the insertion process.
  private ReceiveCommand cmd;
  private Change change;
  private String changeMessage;
  private PatchSetInfo patchSetInfo;
  private PatchSet patchSet;
  private String pushCert;
  private ProjectState projectState;
  private ReviewerModificationList reviewerAdditions;

  @Inject
  ChangeInserter(
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      PatchSetInfoFactory patchSetInfoFactory,
      PatchSetUtil psUtil,
      ApprovalsUtil approvalsUtil,
      ChangeMessagesUtil cmUtil,
      EmailFactories emailFactories,
      @SendEmailExecutor ExecutorService sendEmailExecutor,
      CommitValidators.Factory commitValidatorsFactory,
      CommentAdded commentAdded,
      RevisionCreated revisionCreated,
      ReviewerModifier reviewerModifier,
      MessageIdGenerator messageIdGenerator,
      AutoMerger autoMerger,
      ChangeUtil changeUtil,
      @Assisted Change.Id changeId,
      @Assisted ObjectId commitId,
      @Assisted String refName) {
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.psUtil = psUtil;
    this.approvalsUtil = approvalsUtil;
    this.cmUtil = cmUtil;
    this.emailFactories = emailFactories;
    this.sendEmailExecutor = sendEmailExecutor;
    this.commitValidatorsFactory = commitValidatorsFactory;
    this.revisionCreated = revisionCreated;
    this.commentAdded = commentAdded;
    this.reviewerModifier = reviewerModifier;
    this.messageIdGenerator = messageIdGenerator;
    this.autoMerger = autoMerger;
    this.changeUtil = changeUtil;

    this.changeId = changeId;
    this.psId = PatchSet.id(changeId, INITIAL_PATCH_SET_ID);
    this.commitId = commitId.copy();
    this.refName = refName;
    this.reviewerInputs = ImmutableList.of();
    this.approvals = Collections.emptyMap();
    this.fireRevisionCreated = true;
    this.sendMail = true;
    this.updateRef = true;
  }

  @Override
  public Change createChange(Context ctx) throws IOException {
    change =
        new Change(
            getChangeKey(ctx.getRevWalk()),
            changeId,
            ctx.getAccountId(),
            BranchNameKey.create(ctx.getProject(), refName),
            ctx.getWhen());
    change.setStatus(MoreObjects.firstNonNull(status, Change.Status.NEW));
    change.setTopic(topic);
    change.setCherryPickOf(cherryPickOf);
    change.setPrivate(isPrivate);
    change.setWorkInProgress(workInProgress);
    change.setReviewStarted(!workInProgress);
    change.setRevertOf(revertOf);
    return change;
  }

  private Change.Key getChangeKey(RevWalk rw) throws IOException {
    RevCommit commit = rw.parseCommit(commitId);
    rw.parseBody(commit);
    List<String> idList = changeUtil.getChangeIdsFromFooter(commit);
    if (!idList.isEmpty()) {
      return Change.key(idList.get(idList.size() - 1).trim());
    }
    // A Change-Id is generated for the review, but not appended to the commit message.
    // This can happen if requireChangeId is false.
    return CommitMessageUtil.generateKey();
  }

  public PatchSet.Id getPatchSetId() {
    return psId;
  }

  public ObjectId getCommitId() {
    return commitId;
  }

  public Change getChange() {
    checkState(change != null, "getChange() only valid after creating change");
    return change;
  }

  @CanIgnoreReturnValue
  public ChangeInserter setTopic(String topic) {
    checkState(change == null, "setTopic(String) only valid before creating change");
    this.topic = topic;
    return this;
  }

  @CanIgnoreReturnValue
  public ChangeInserter setCherryPickOf(PatchSet.Id cherryPickOf) {
    this.cherryPickOf = cherryPickOf;
    return this;
  }

  @CanIgnoreReturnValue
  public ChangeInserter setMessage(String message) {
    this.message = message;
    return this;
  }

  @CanIgnoreReturnValue
  public ChangeInserter setPatchSetDescription(String patchSetDescription) {
    this.patchSetDescription = patchSetDescription;
    return this;
  }

  @CanIgnoreReturnValue
  public ChangeInserter setValidate(boolean validate) {
    this.validate = validate;
    return this;
  }

  @CanIgnoreReturnValue
  public ChangeInserter setReviewersAndCcs(
      Iterable<Account.Id> reviewers, Iterable<Account.Id> ccs) {
    return setReviewersAndCcsAsStrings(
        Iterables.transform(reviewers, Account.Id::toString),
        Iterables.transform(ccs, Account.Id::toString));
  }

  @CanIgnoreReturnValue
  public ChangeInserter setReviewersAndCcsIgnoreVisibility(
      Iterable<Account.Id> reviewers, Iterable<Account.Id> ccs) {
    return setReviewersAndCcsAsStrings(
        Iterables.transform(reviewers, Account.Id::toString),
        Iterables.transform(ccs, Account.Id::toString),
        /* skipVisibilityCheck= */ true);
  }

  @CanIgnoreReturnValue
  public ChangeInserter setReviewersAndCcsAsStrings(
      Iterable<String> reviewers, Iterable<String> ccs) {
    return setReviewersAndCcsAsStrings(reviewers, ccs, /* skipVisibilityCheck= */ false);
  }

  @CanIgnoreReturnValue
  private ChangeInserter setReviewersAndCcsAsStrings(
      Iterable<String> reviewers, Iterable<String> ccs, boolean skipVisibilityCheck) {
    reviewerInputs =
        Streams.concat(
                Streams.stream(reviewers)
                    .distinct()
                    .map(id -> newReviewerInput(id, ReviewerState.REVIEWER, skipVisibilityCheck)),
                Streams.stream(ccs)
                    .distinct()
                    .map(id -> newReviewerInput(id, ReviewerState.CC, skipVisibilityCheck)))
            .collect(toImmutableList());
    return this;
  }

  @CanIgnoreReturnValue
  public ChangeInserter setPrivate(boolean isPrivate) {
    checkState(change == null, "setPrivate(boolean) only valid before creating change");
    this.isPrivate = isPrivate;
    return this;
  }

  @CanIgnoreReturnValue
  public ChangeInserter setWorkInProgress(boolean workInProgress) {
    this.workInProgress = workInProgress;
    return this;
  }

  @CanIgnoreReturnValue
  public ChangeInserter setStatus(Change.Status status) {
    checkState(change == null, "setStatus(Change.Status) only valid before creating change");
    this.status = status;
    return this;
  }

  @CanIgnoreReturnValue
  public ChangeInserter setGroups(List<String> groups) {
    requireNonNull(groups, "groups may not be empty");
    checkState(patchSet == null, "setGroups(List<String>) only valid before creating change");
    this.groups = groups;
    return this;
  }

  @CanIgnoreReturnValue
  public ChangeInserter setCustomKeyedValues(ImmutableMap<String, String> customKeyedValues) {
    requireNonNull(customKeyedValues, "customKeyedValues may not be null");
    this.customKeyedValues = customKeyedValues;
    return this;
  }

  @CanIgnoreReturnValue
  public ChangeInserter setValidationOptions(
      ImmutableListMultimap<String, String> validationOptions) {
    requireNonNull(validationOptions, "validationOptions may not be null");
    checkState(
        patchSet == null,
        "setValidationOptions(ImmutableListMultimap<String, String>) only valid before creating a"
            + " change");
    this.validationOptions = validationOptions;
    return this;
  }

  @CanIgnoreReturnValue
  public ChangeInserter setFireRevisionCreated(boolean fireRevisionCreated) {
    this.fireRevisionCreated = fireRevisionCreated;
    return this;
  }

  @CanIgnoreReturnValue
  public ChangeInserter setSendMail(boolean sendMail) {
    this.sendMail = sendMail;
    return this;
  }

  @CanIgnoreReturnValue
  public ChangeInserter setRequestScopePropagator(RequestScopePropagator r) {
    this.requestScopePropagator = r;
    return this;
  }

  @CanIgnoreReturnValue
  public ChangeInserter setRevertOf(Change.Id revertOf) {
    this.revertOf = revertOf;
    return this;
  }

  public void setPushCertificate(String cert) {
    pushCert = cert;
  }

  public PatchSet getPatchSet() {
    checkState(patchSet != null, "getPatchSet() only valid after creating change");
    return patchSet;
  }

  @CanIgnoreReturnValue
  public ChangeInserter setApprovals(Map<String, Short> approvals) {
    this.approvals = approvals;
    return this;
  }

  /**
   * Set whether to include the new patch set ref update in this update.
   *
   * <p>If false, the caller is responsible for creating the patch set ref <strong>before</strong>
   * executing the containing {@code BatchUpdate}.
   *
   * <p>Should not be used in new code, as it doesn't result in a single atomic batch ref update for
   * code and NoteDb meta refs.
   *
   * @param updateRef whether to update the ref during {@link #updateRepo(RepoContext)}.
   */
  @Deprecated
  @CanIgnoreReturnValue
  public ChangeInserter setUpdateRef(boolean updateRef) {
    this.updateRef = updateRef;
    return this;
  }

  @Nullable
  public String getChangeMessage() {
    if (message == null) {
      return null;
    }
    checkState(changeMessage != null, "getChangeMessage() only valid after inserting change");
    return changeMessage;
  }

  public ReceiveCommand getCommand() {
    return cmd;
  }

  @Override
  public void updateRepo(RepoContext ctx) throws ResourceConflictException, IOException {
    cmd = new ReceiveCommand(ObjectId.zeroId(), commitId, psId.toRefName());
    projectState = projectCache.get(ctx.getProject()).orElseThrow(illegalState(ctx.getProject()));
    validate(ctx);
    if (!updateRef) {
      return;
    }
    ctx.addRefUpdate(cmd);
    Optional<ReceiveCommand> autoMerge =
        autoMerger.createAutoMergeCommitIfNecessary(
            ctx.getRepoView(),
            ctx.getRevWalk(),
            ctx.getInserter(),
            ctx.getRevWalk().parseCommit(commitId));
    if (autoMerge.isPresent()) {
      ctx.addRefUpdate(autoMerge.get());
    }
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws RestApiException, IOException, PermissionBackendException, ConfigInvalidException {
    change = ctx.getChange(); // Use defensive copy created by ChangeControl.
    patchSetInfo =
        patchSetInfoFactory.get(ctx.getRevWalk(), ctx.getRevWalk().parseCommit(commitId), psId);
    ctx.getChange().setCurrentPatchSet(patchSetInfo);

    ChangeUpdate update = ctx.getUpdate(psId);
    update.setChangeId(change.getKey().get());
    update.setSubjectForCommit("Create change");
    update.setBranch(change.getDest().branch());
    try {
      update.setTopic(change.getTopic());
    } catch (ValidationException ex) {
      throw new BadRequestException(ex.getMessage());
    }
    if (customKeyedValues != null) {
      try {
        if (customKeyedValues.entrySet().size() > MAX_CUSTOM_KEYED_VALUES) {
          throw new ValidationException("Too many custom keyed values");
        }
        for (Map.Entry<String, String> entry : customKeyedValues.entrySet()) {
          update.addCustomKeyedValue(entry.getKey(), entry.getValue());
        }
      } catch (ValidationException ex) {
        throw new BadRequestException(ex.getMessage());
      }
    }
    update.setPsDescription(patchSetDescription);
    update.setPrivate(isPrivate);
    update.setWorkInProgress(workInProgress);
    if (revertOf != null) {
      update.setRevertOf(revertOf.get());
    }
    if (cherryPickOf != null) {
      update.setCherryPickOf(cherryPickOf.getCommaSeparatedChangeAndPatchSetId());
    }

    List<String> newGroups = groups;
    if (newGroups.isEmpty()) {
      newGroups = GroupCollector.getDefaultGroups(commitId);
    }
    patchSet =
        psUtil.insert(
            ctx.getRevWalk(), update, psId, commitId, newGroups, pushCert, patchSetDescription);

    /* TODO: fixStatusToMerged is used here because the tests
     * (byStatusClosed() in AbstractQueryChangesTest)
     * insert changes that are already merged,
     * and setStatus may not be used to set the Status to merged
     *
     * is it possible to make the tests use the merge code path,
     * instead of setting the status directly?
     */
    if (change.getStatus() == Change.Status.MERGED) {
      update.fixStatusToMerged(new SubmissionId(change));
    } else {
      update.setStatus(change.getStatus());
    }

    reviewerAdditions =
        reviewerModifier.prepare(ctx.getNotes(), ctx.getUser(), getReviewerInputs(), true);
    Optional<ReviewerModification> reviewerError =
        reviewerAdditions.getFailures().stream().findFirst();
    if (reviewerError.isPresent()) {
      throw new UnprocessableEntityException(reviewerError.get().result.error);
    }
    reviewerAdditions.updateChange(ctx, patchSet);

    LabelTypes labelTypes = projectState.getLabelTypes();
    approvalsUtil.addApprovalsForNewPatchSet(
        update, labelTypes, patchSet, ctx.getUser(), approvals);

    // Check if approvals are changing with this update. If so, add the current user (aka the
    // approver) as a reviewers because all approvers must also be reviewers.
    // Note that this is done separately as addReviewers is filtering out the change owner as a
    // reviewer which is needed in several other code paths.
    if (!approvals.isEmpty()) {
      update.putReviewer(ctx.getAccountId(), REVIEWER);
    }
    if (message != null) {
      changeMessage =
          cmUtil.setChangeMessage(
              update, message, ChangeMessagesUtil.uploadedPatchSetTag(workInProgress));
    }
    return true;
  }

  @Override
  public void postUpdate(PostUpdateContext ctx) throws Exception {
    reviewerAdditions.postUpdate(ctx);
    NotifyResolver.Result notify = ctx.getNotify(change.getId());
    if (sendMail) {
      Runnable sender =
          new Runnable() {
            @Override
            public void run() {
              try {
                StartReviewChangeEmailDecorator startReviewEmail =
                    emailFactories.createStartReviewChangeEmail();
                startReviewEmail.markAsCreateChange();
                startReviewEmail.addReviewers(
                    reviewerAdditions.flattenResults(ReviewerOp.Result::addedReviewers).stream()
                        .map(PatchSetApproval::accountId)
                        .collect(toImmutableSet()));
                startReviewEmail.addReviewersByEmail(
                    reviewerAdditions.flattenResults(ReviewerOp.Result::addedReviewersByEmail));
                startReviewEmail.addExtraCC(
                    reviewerAdditions.flattenResults(ReviewerOp.Result::addedCCs));
                startReviewEmail.addExtraCCByEmail(
                    reviewerAdditions.flattenResults(ReviewerOp.Result::addedCCsByEmail));
                ChangeEmail changeEmail =
                    emailFactories.createChangeEmail(
                        change.getProject(), change.getId(), startReviewEmail);
                changeEmail.setPatchSet(patchSet, patchSetInfo);
                OutgoingEmail outgoingEmail =
                    emailFactories.createOutgoingEmail(REVIEW_REQUESTED, changeEmail);
                outgoingEmail.setFrom(change.getOwner());
                outgoingEmail.setNotify(notify);
                outgoingEmail.setMessageId(
                    messageIdGenerator.fromChangeUpdate(ctx.getRepoView(), patchSet.id()));
                outgoingEmail.send();
              } catch (Exception e) {
                logger.atSevere().withCause(e).log(
                    "Cannot send email for new change %s", change.getId());
              }
            }

            @Override
            public String toString() {
              return "send-email newchange";
            }
          };
      if (requestScopePropagator != null) {
        @SuppressWarnings("unused")
        Future<?> possiblyIgnoredError =
            sendEmailExecutor.submit(requestScopePropagator.wrap(sender));
      } else {
        sender.run();
      }
    }

    /* For labels that are not set in this operation, show the "current" value
     * of 0, and no oldValue as the value was not modified by this operation.
     * For labels that are set in this operation, the value was modified, so
     * show a transition from an oldValue of 0 to the new value.
     */
    if (fireRevisionCreated) {
      revisionCreated.fire(
          ctx.getChangeData(change), patchSet, ctx.getAccount(), ctx.getWhen(), notify);
      if (approvals != null && !approvals.isEmpty()) {
        List<LabelType> labels = projectState.getLabelTypes(change.getDest()).getLabelTypes();
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
            ctx.getChangeData(change),
            patchSet,
            ctx.getAccount(),
            null,
            allApprovals,
            oldApprovals,
            ctx.getWhen());
      }
    }
  }

  private void validate(RepoContext ctx) throws IOException, ResourceConflictException {
    if (!validate) {
      return;
    }

    try {
      try (CommitReceivedEvent event =
          new CommitReceivedEvent(
              cmd,
              projectState.getProject(),
              change.getDest().branch(),
              validationOptions,
              ctx.getRepoView().getConfig(),
              ctx.getRevWalk().getObjectReader(),
              commitId,
              ctx.getIdentifiedUser())) {
        commitValidatorsFactory
            .forGerritCommits(
                permissionBackend.user(ctx.getUser()).project(ctx.getProject()),
                BranchNameKey.create(ctx.getProject(), refName),
                ctx.getIdentifiedUser(),
                new NoSshInfo(),
                ctx.getRevWalk(),
                change)
            .validate(event);
      }
    } catch (CommitValidationException e) {
      throw new ResourceConflictException(e.getFullMessage());
    }
  }

  private static InternalReviewerInput newReviewerInput(
      String reviewer, ReviewerState state, boolean skipVisibilityCheck) {
    // Disable individual emails when adding reviewers, as all reviewers will receive the single
    // bulk new change email.
    InternalReviewerInput input =
        ReviewerModifier.newReviewerInput(reviewer, state, NotifyHandling.NONE);

    // Ignore failures for reasons like the reviewer being inactive or being unable to see the
    // change. This is required for the push path, where it automatically sets reviewers from
    // certain commit footers: putting a nonexistent user in a footer should not cause an error. In
    // theory we could provide finer control to do this for some reviewers and not others, but it's
    // not worth complicating the ChangeInserter interface further at this time.
    input.otherFailureBehavior = ReviewerModifier.FailureBehavior.IGNORE_EXCEPT_NOT_FOUND;

    input.skipVisibilityCheck = skipVisibilityCheck;

    return input;
  }

  private ImmutableList<InternalReviewerInput> getReviewerInputs() {
    if (projectState.is(BooleanProjectConfig.SKIP_ADDING_AUTHOR_AND_COMMITTER_AS_REVIEWERS)) {
      return reviewerInputs;
    }
    return Streams.concat(
            reviewerInputs.stream(),
            Streams.stream(
                newReviewerInputFromCommitIdentity(
                    change,
                    patchSetInfo.getCommitId(),
                    patchSetInfo.getAuthor().getAccount(),
                    NotifyHandling.NONE,
                    change.getOwner())),
            Streams.stream(
                newReviewerInputFromCommitIdentity(
                    change,
                    patchSetInfo.getCommitId(),
                    patchSetInfo.getCommitter().getAccount(),
                    NotifyHandling.NONE,
                    change.getOwner())))
        .collect(toImmutableList());
  }
}
