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
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.CC;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetInfo;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.approval.ApprovalCopier;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.extensions.events.RevisionCreated;
import com.google.gerrit.server.extensions.events.WorkInProgressStateChanged;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.AutoMerger;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.ssh.NoSshInfo;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.gerrit.server.update.RepoContext;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.ReceiveCommand;

public class PatchSetInserter implements BatchUpdateOp {
  public interface Factory {
    PatchSetInserter create(ChangeNotes notes, PatchSet.Id psId, ObjectId commitId);
  }

  // Injected fields.
  private final PermissionBackend permissionBackend;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ChangeKindCache changeKindCache;
  private final CommitValidators.Factory commitValidatorsFactory;
  private final EmailNewPatchSet.Factory emailNewPatchSetFactory;
  private final ProjectCache projectCache;
  private final RevisionCreated revisionCreated;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeMessagesUtil cmUtil;
  private final PatchSetUtil psUtil;
  private final WorkInProgressStateChanged wipStateChanged;
  private final AutoMerger autoMerger;

  // Assisted-injected fields.
  private final PatchSet.Id psId;
  private final ObjectId commitId;
  // Read prior to running the batch update, so must only be used during
  // updateRepo; updateChange and later must use the notes from the
  // ChangeContext.
  private final ChangeNotes origNotes;

  // Fields exposed as setters.
  private String message;
  private String description;
  private Boolean workInProgress;
  private boolean validate = true;
  private boolean checkAddPatchSetPermission = true;
  private List<String> groups = Collections.emptyList();
  private ImmutableListMultimap<String, String> validationOptions = ImmutableListMultimap.of();
  private boolean fireRevisionCreated = true;
  private boolean allowClosed;
  private boolean sendEmail = true;
  private String topic;
  private boolean storeCopiedVotes = true;

  // Fields set during some phase of BatchUpdate.Op.
  private Change change;
  private PatchSet patchSet;
  private PatchSetInfo patchSetInfo;
  private ChangeKind changeKind;
  private String mailMessage;
  private ReviewerSet oldReviewers;
  private boolean oldWorkInProgressState;
  private ApprovalCopier.Result approvalCopierResult;
  private ObjectId preUpdateMetaId;

  @Inject
  public PatchSetInserter(
      PermissionBackend permissionBackend,
      ApprovalsUtil approvalsUtil,
      ChangeMessagesUtil cmUtil,
      PatchSetInfoFactory patchSetInfoFactory,
      ChangeKindCache changeKindCache,
      CommitValidators.Factory commitValidatorsFactory,
      EmailNewPatchSet.Factory emailNewPatchSetFactory,
      PatchSetUtil psUtil,
      RevisionCreated revisionCreated,
      ProjectCache projectCache,
      WorkInProgressStateChanged wipStateChanged,
      AutoMerger autoMerger,
      @Assisted ChangeNotes notes,
      @Assisted PatchSet.Id psId,
      @Assisted ObjectId commitId) {
    this.permissionBackend = permissionBackend;
    this.approvalsUtil = approvalsUtil;
    this.cmUtil = cmUtil;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.changeKindCache = changeKindCache;
    this.commitValidatorsFactory = commitValidatorsFactory;
    this.emailNewPatchSetFactory = emailNewPatchSetFactory;
    this.psUtil = psUtil;
    this.revisionCreated = revisionCreated;
    this.projectCache = projectCache;
    this.wipStateChanged = wipStateChanged;
    this.autoMerger = autoMerger;

    this.origNotes = notes;
    this.psId = psId;
    this.commitId = commitId.copy();
  }

  public PatchSet.Id getPatchSetId() {
    return psId;
  }

  public PatchSetInserter setMessage(String message) {
    this.message = message;
    return this;
  }

  public PatchSetInserter setDescription(String description) {
    this.description = description;
    return this;
  }

  public PatchSetInserter setWorkInProgress(boolean workInProgress) {
    this.workInProgress = workInProgress;
    return this;
  }

  public PatchSetInserter setValidate(boolean validate) {
    this.validate = validate;
    return this;
  }

  public PatchSetInserter setCheckAddPatchSetPermission(boolean checkAddPatchSetPermission) {
    this.checkAddPatchSetPermission = checkAddPatchSetPermission;
    return this;
  }

  public PatchSetInserter setGroups(List<String> groups) {
    requireNonNull(groups, "groups may not be null");
    this.groups = groups;
    return this;
  }

  public PatchSetInserter setValidationOptions(
      ImmutableListMultimap<String, String> validationOptions) {
    requireNonNull(validationOptions, "validationOptions may not be null");
    this.validationOptions = validationOptions;
    return this;
  }

  public PatchSetInserter setFireRevisionCreated(boolean fireRevisionCreated) {
    this.fireRevisionCreated = fireRevisionCreated;
    return this;
  }

  public PatchSetInserter setAllowClosed(boolean allowClosed) {
    this.allowClosed = allowClosed;
    return this;
  }

  public PatchSetInserter setSendEmail(boolean sendEmail) {
    this.sendEmail = sendEmail;
    return this;
  }

  public PatchSetInserter setTopic(String topic) {
    this.topic = topic;
    return this;
  }

  /**
   * We always want to store copied votes except when the change is getting submitted and a new
   * patch-set is created on submit (using submit strategies such as "REBASE_ALWAYS"). In such
   * cases, we already store the votes of the new patch-sets in SubmitStrategyOp#saveApprovals. We
   * should not also store the copied votes.
   */
  public PatchSetInserter setStoreCopiedVotes(boolean storeCopiedVotes) {
    this.storeCopiedVotes = storeCopiedVotes;
    return this;
  }

  public Change getChange() {
    checkState(change != null, "getChange() only valid after executing update");
    return change;
  }

  public PatchSet getPatchSet() {
    checkState(patchSet != null, "getPatchSet() only valid after executing update");
    return patchSet;
  }

  @Override
  public void updateRepo(RepoContext ctx)
      throws AuthException, ResourceConflictException, IOException, PermissionBackendException {
    validate(ctx);
    ctx.addRefUpdate(ObjectId.zeroId(), commitId, getPatchSetId().toRefName());

    changeKind =
        changeKindCache.getChangeKind(
            ctx.getProject(),
            ctx.getRevWalk(),
            ctx.getRepoView().getConfig(),
            psUtil.current(origNotes).commitId(),
            commitId);

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
      throws ResourceConflictException, IOException, BadRequestException {
    preUpdateMetaId = ctx.getNotes().getMetaId();
    change = ctx.getChange();
    ChangeUpdate update = ctx.getUpdate(psId);
    update.setSubjectForCommit("Create patch set " + psId.get());

    if (!change.isNew() && !allowClosed) {
      throw new ResourceConflictException(
          String.format(
              "Cannot create new patch set of change %s because it is %s",
              change.getId(), ChangeUtil.status(change)));
    }

    List<String> newGroups = groups;
    if (newGroups.isEmpty()) {
      PatchSet prevPs = psUtil.current(ctx.getNotes());
      if (prevPs != null) {
        newGroups = prevPs.groups();
      }
    }
    patchSet =
        psUtil.insert(
            ctx.getRevWalk(), ctx.getUpdate(psId), psId, commitId, newGroups, null, description);

    if (ctx.getNotify(change.getId()).handling() != NotifyHandling.NONE) {
      oldReviewers = approvalsUtil.getReviewers(ctx.getNotes());
    }

    oldWorkInProgressState = change.isWorkInProgress();
    if (workInProgress != null) {
      change.setWorkInProgress(workInProgress);
      change.setReviewStarted(!workInProgress);
      update.setWorkInProgress(workInProgress);
    }

    patchSetInfo =
        patchSetInfoFactory.get(ctx.getRevWalk(), ctx.getRevWalk().parseCommit(commitId), psId);
    if (!allowClosed) {
      change.setStatus(Change.Status.NEW);
    }
    change.setCurrentPatchSet(patchSetInfo);
    if (topic != null) {
      change.setTopic(topic);
      try {
        update.setTopic(topic);
      } catch (ValidationException ex) {
        throw new BadRequestException(ex.getMessage());
      }
    }

    if (storeCopiedVotes) {
      approvalCopierResult =
          approvalsUtil.copyApprovalsToNewPatchSet(
              ctx.getNotes(), patchSet, ctx.getRevWalk(), ctx.getRepoView().getConfig(), update);
    }

    mailMessage = insertChangeMessage(update, ctx);

    return true;
  }

  @Nullable
  private String insertChangeMessage(ChangeUpdate update, ChangeContext ctx) {
    StringBuilder messageBuilder = new StringBuilder();
    if (message != null) {
      messageBuilder.append(message);
    }

    if (approvalCopierResult != null) {
      approvalsUtil
          .formatApprovalCopierResult(
              approvalCopierResult,
              projectCache
                  .get(ctx.getProject())
                  .orElseThrow(illegalState(ctx.getProject()))
                  .getLabelTypes())
          .ifPresent(
              msg -> {
                if (message != null && !message.endsWith("\n")) {
                  messageBuilder.append("\n");
                }
                messageBuilder.append("\n").append(msg);
              });
    }

    String changeMessage = messageBuilder.toString();
    if (changeMessage.isEmpty()) {
      return null;
    }

    return cmUtil.setChangeMessage(
        update,
        messageBuilder.toString(),
        ChangeMessagesUtil.uploadedPatchSetTag(change.isWorkInProgress()));
  }

  @Override
  public void postUpdate(PostUpdateContext ctx) {
    NotifyResolver.Result notify = ctx.getNotify(change.getId());
    if (notify.shouldNotify() && sendEmail) {
      requireNonNull(mailMessage);

      emailNewPatchSetFactory
          .create(
              ctx,
              patchSet,
              mailMessage,
              approvalCopierResult.outdatedApprovals().stream()
                  .map(ApprovalCopier.Result.PatchSetApprovalData::patchSetApproval)
                  .collect(toImmutableSet()),
              oldReviewers.byState(REVIEWER),
              oldReviewers.byState(CC),
              changeKind,
              preUpdateMetaId)
          .sendAsync();
    }

    if (fireRevisionCreated) {
      revisionCreated.fire(
          ctx.getChangeData(change), patchSet, ctx.getAccount(), ctx.getWhen(), notify);
    }

    if (workInProgress != null && oldWorkInProgressState != workInProgress) {
      wipStateChanged.fire(ctx.getChangeData(change), patchSet, ctx.getAccount(), ctx.getWhen());
    }
  }

  private void validate(RepoContext ctx)
      throws AuthException, ResourceConflictException, IOException, PermissionBackendException {
    // Not allowed to create a new patch set if the current patch set is locked.
    psUtil.checkPatchSetNotLocked(origNotes);

    if (checkAddPatchSetPermission) {
      permissionBackend.user(ctx.getUser()).change(origNotes).check(ChangePermission.ADD_PATCH_SET);
    }
    projectCache
        .get(ctx.getProject())
        .orElseThrow(illegalState(ctx.getProject()))
        .checkStatePermitsWrite();
    if (!validate) {
      return;
    }

    String refName = getPatchSetId().toRefName();
    try (CommitReceivedEvent event =
        new CommitReceivedEvent(
            new ReceiveCommand(
                ObjectId.zeroId(),
                commitId,
                refName.substring(0, refName.lastIndexOf('/') + 1) + "new"),
            projectCache
                .get(origNotes.getProjectName())
                .orElseThrow(illegalState(origNotes.getProjectName()))
                .getProject(),
            origNotes.getChange().getDest().branch(),
            validationOptions,
            ctx.getRepoView().getConfig(),
            ctx.getRevWalk().getObjectReader(),
            commitId,
            ctx.getIdentifiedUser())) {
      commitValidatorsFactory
          .forGerritCommits(
              permissionBackend.user(ctx.getUser()).project(ctx.getProject()),
              origNotes.getChange().getDest(),
              ctx.getIdentifiedUser(),
              new NoSshInfo(),
              ctx.getRevWalk(),
              origNotes.getChange())
          .validate(event);
    } catch (CommitValidationException e) {
      throw new ResourceConflictException(e.getFullMessage());
    }
  }
}
