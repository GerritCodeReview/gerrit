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
import static com.google.gerrit.server.change.ReviewerAdder.newAddReviewerInputFromCommitIdentity;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
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
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ReviewerAdder.InternalAddReviewerInput;
import com.google.gerrit.server.change.ReviewerAdder.ReviewerAddition;
import com.google.gerrit.server.change.ReviewerAdder.ReviewerAdditionList;
import com.google.gerrit.server.config.SendEmailExecutor;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.extensions.events.CommentAdded;
import com.google.gerrit.server.extensions.events.RevisionCreated;
import com.google.gerrit.server.git.GroupCollector;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.mail.send.CreateChangeSender;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.ssh.NoSshInfo;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.InsertChangeOp;
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
  private final CreateChangeSender.Factory createChangeSenderFactory;
  private final ExecutorService sendEmailExecutor;
  private final CommitValidators.Factory commitValidatorsFactory;
  private final RevisionCreated revisionCreated;
  private final CommentAdded commentAdded;
  private final ReviewerAdder reviewerAdder;
  private final MessageIdGenerator messageIdGenerator;

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
  private boolean validate = true;
  private Map<String, Short> approvals;
  private RequestScopePropagator requestScopePropagator;
  private boolean fireRevisionCreated;
  private boolean sendMail;
  private boolean updateRef;
  private Change.Id revertOf;
  private ImmutableList<InternalAddReviewerInput> reviewerInputs;

  // Fields set during the insertion process.
  private ReceiveCommand cmd;
  private Change change;
  private ChangeMessage changeMessage;
  private PatchSetInfo patchSetInfo;
  private PatchSet patchSet;
  private String pushCert;
  private ProjectState projectState;
  private ReviewerAdditionList reviewerAdditions;

  @Inject
  ChangeInserter(
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      PatchSetInfoFactory patchSetInfoFactory,
      PatchSetUtil psUtil,
      ApprovalsUtil approvalsUtil,
      ChangeMessagesUtil cmUtil,
      CreateChangeSender.Factory createChangeSenderFactory,
      @SendEmailExecutor ExecutorService sendEmailExecutor,
      CommitValidators.Factory commitValidatorsFactory,
      CommentAdded commentAdded,
      RevisionCreated revisionCreated,
      ReviewerAdder reviewerAdder,
      MessageIdGenerator messageIdGenerator,
      @Assisted Change.Id changeId,
      @Assisted ObjectId commitId,
      @Assisted String refName) {
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.psUtil = psUtil;
    this.approvalsUtil = approvalsUtil;
    this.cmUtil = cmUtil;
    this.createChangeSenderFactory = createChangeSenderFactory;
    this.sendEmailExecutor = sendEmailExecutor;
    this.commitValidatorsFactory = commitValidatorsFactory;
    this.revisionCreated = revisionCreated;
    this.commentAdded = commentAdded;
    this.reviewerAdder = reviewerAdder;
    this.messageIdGenerator = messageIdGenerator;

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
            getChangeKey(ctx.getRevWalk(), commitId),
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

  private static Change.Key getChangeKey(RevWalk rw, ObjectId id) throws IOException {
    RevCommit commit = rw.parseCommit(id);
    rw.parseBody(commit);
    List<String> idList = commit.getFooterLines(FooterConstants.CHANGE_ID);
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

  public ChangeInserter setTopic(String topic) {
    checkState(change == null, "setTopic(String) only valid before creating change");
    this.topic = topic;
    return this;
  }

  public ChangeInserter setCherryPickOf(PatchSet.Id cherryPickOf) {
    this.cherryPickOf = cherryPickOf;
    return this;
  }

  public ChangeInserter setMessage(String message) {
    this.message = message;
    return this;
  }

  public ChangeInserter setPatchSetDescription(String patchSetDescription) {
    this.patchSetDescription = patchSetDescription;
    return this;
  }

  public ChangeInserter setValidate(boolean validate) {
    this.validate = validate;
    return this;
  }

  public ChangeInserter setReviewersAndCcs(
      Iterable<Account.Id> reviewers, Iterable<Account.Id> ccs) {
    return setReviewersAndCcsAsStrings(
        Iterables.transform(reviewers, Account.Id::toString),
        Iterables.transform(ccs, Account.Id::toString));
  }

  public ChangeInserter setReviewersAndCcsAsStrings(
      Iterable<String> reviewers, Iterable<String> ccs) {
    reviewerInputs =
        Streams.concat(
                Streams.stream(reviewers)
                    .distinct()
                    .map(id -> newAddReviewerInput(id, ReviewerState.REVIEWER)),
                Streams.stream(ccs).distinct().map(id -> newAddReviewerInput(id, ReviewerState.CC)))
            .collect(toImmutableList());
    return this;
  }

  public ChangeInserter setPrivate(boolean isPrivate) {
    checkState(change == null, "setPrivate(boolean) only valid before creating change");
    this.isPrivate = isPrivate;
    return this;
  }

  public ChangeInserter setWorkInProgress(boolean workInProgress) {
    this.workInProgress = workInProgress;
    return this;
  }

  public ChangeInserter setStatus(Change.Status status) {
    checkState(change == null, "setStatus(Change.Status) only valid before creating change");
    this.status = status;
    return this;
  }

  public ChangeInserter setGroups(List<String> groups) {
    requireNonNull(groups, "groups may not be empty");
    checkState(patchSet == null, "setGroups(Iterable<String>) only valid before creating change");
    this.groups = groups;
    return this;
  }

  public ChangeInserter setFireRevisionCreated(boolean fireRevisionCreated) {
    this.fireRevisionCreated = fireRevisionCreated;
    return this;
  }

  public ChangeInserter setSendMail(boolean sendMail) {
    this.sendMail = sendMail;
    return this;
  }

  public ChangeInserter setRequestScopePropagator(RequestScopePropagator r) {
    this.requestScopePropagator = r;
    return this;
  }

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
   * @param updateRef whether to update the ref during {@code updateRepo}.
   */
  @Deprecated
  public ChangeInserter setUpdateRef(boolean updateRef) {
    this.updateRef = updateRef;
    return this;
  }

  public ChangeMessage getChangeMessage() {
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
        reviewerAdder.prepare(ctx.getNotes(), ctx.getUser(), getReviewerInputs(), true);
    Optional<ReviewerAddition> reviewerError = reviewerAdditions.getFailures().stream().findFirst();
    if (reviewerError.isPresent()) {
      throw new UnprocessableEntityException(reviewerError.get().result.error);
    }
    reviewerAdditions.updateChange(ctx, patchSet);

    LabelTypes labelTypes = projectState.getLabelTypes();
    approvalsUtil.addApprovalsForNewPatchSet(
        update, labelTypes, patchSet, ctx.getUser(), approvals);

    // Check if approvals are changing in with this update. If so, add current user to reviewers.
    // Note that this is done separately as addReviewers is filtering out the change owner as
    // reviewer which is needed in several other code paths.
    // TODO(dborowitz): Still necessary?
    if (!approvals.isEmpty()) {
      update.putReviewer(ctx.getAccountId(), REVIEWER);
    }
    if (message != null) {
      changeMessage =
          ChangeMessagesUtil.newMessage(
              patchSet.id(),
              ctx.getUser(),
              patchSet.createdOn(),
              message,
              ChangeMessagesUtil.uploadedPatchSetTag(workInProgress));
      cmUtil.addChangeMessage(update, changeMessage);
    }
    return true;
  }

  @Override
  public void postUpdate(Context ctx) throws Exception {
    reviewerAdditions.postUpdate(ctx);
    NotifyResolver.Result notify = ctx.getNotify(change.getId());
    if (sendMail && notify.shouldNotify()) {
      Runnable sender =
          new Runnable() {
            @Override
            public void run() {
              try {
                CreateChangeSender emailSender =
                    createChangeSenderFactory.create(change.getProject(), change.getId());
                emailSender.setFrom(change.getOwner());
                emailSender.setPatchSet(patchSet, patchSetInfo);
                emailSender.setNotify(notify);
                emailSender.addReviewers(
                    reviewerAdditions.flattenResults(AddReviewersOp.Result::addedReviewers).stream()
                        .map(PatchSetApproval::accountId)
                        .collect(toImmutableSet()));
                emailSender.addReviewersByEmail(
                    reviewerAdditions.flattenResults(AddReviewersOp.Result::addedReviewersByEmail));
                emailSender.addExtraCC(
                    reviewerAdditions.flattenResults(AddReviewersOp.Result::addedCCs));
                emailSender.addExtraCCByEmail(
                    reviewerAdditions.flattenResults(AddReviewersOp.Result::addedCCsByEmail));
                emailSender.setMessageId(
                    messageIdGenerator.fromChangeUpdate(ctx.getRepoView(), patchSet.id()));
                emailSender.send();
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
      revisionCreated.fire(change, patchSet, ctx.getAccount(), ctx.getWhen(), notify);
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
            change, patchSet, ctx.getAccount(), null, allApprovals, oldApprovals, ctx.getWhen());
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

  private static InternalAddReviewerInput newAddReviewerInput(
      String reviewer, ReviewerState state) {
    // Disable individual emails when adding reviewers, as all reviewers will receive the single
    // bulk new change email.
    InternalAddReviewerInput input =
        ReviewerAdder.newAddReviewerInput(reviewer, state, NotifyHandling.NONE);

    // Ignore failures for reasons like the reviewer being inactive or being unable to see the
    // change. This is required for the push path, where it automatically sets reviewers from
    // certain commit footers: putting a nonexistent user in a footer should not cause an error. In
    // theory we could provide finer control to do this for some reviewers and not others, but it's
    // not worth complicating the ChangeInserter interface further at this time.
    input.otherFailureBehavior = ReviewerAdder.FailureBehavior.IGNORE;

    return input;
  }

  private ImmutableList<InternalAddReviewerInput> getReviewerInputs() {
    return Streams.concat(
            reviewerInputs.stream(),
            Streams.stream(
                newAddReviewerInputFromCommitIdentity(
                    change,
                    patchSetInfo.getCommitId(),
                    patchSetInfo.getAuthor().getAccount(),
                    NotifyHandling.NONE)),
            Streams.stream(
                newAddReviewerInputFromCommitIdentity(
                    change,
                    patchSetInfo.getCommitId(),
                    patchSetInfo.getCommitter().getAccount(),
                    NotifyHandling.NONE)))
        .collect(toImmutableList());
  }
}
