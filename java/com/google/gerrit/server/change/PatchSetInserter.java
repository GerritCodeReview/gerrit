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
import static com.google.gerrit.server.notedb.ReviewerStateInternal.CC;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static java.util.Objects.requireNonNull;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetInfo;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.extensions.events.RevisionCreated;
import com.google.gerrit.server.extensions.events.WorkInProgressStateChanged;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.mail.send.ReplacePatchSetSender;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.ssh.NoSshInfo;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.RepoContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.ReceiveCommand;

public class PatchSetInserter implements BatchUpdateOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    PatchSetInserter create(ChangeNotes notes, PatchSet.Id psId, ObjectId commitId);
  }

  // Injected fields.
  private final PermissionBackend permissionBackend;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final CommitValidators.Factory commitValidatorsFactory;
  private final ReplacePatchSetSender.Factory replacePatchSetFactory;
  private final ProjectCache projectCache;
  private final RevisionCreated revisionCreated;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeMessagesUtil cmUtil;
  private final PatchSetUtil psUtil;
  private final WorkInProgressStateChanged wipStateChanged;

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
  private boolean fireRevisionCreated = true;
  private boolean allowClosed;
  private boolean sendEmail = true;

  // Fields set during some phase of BatchUpdate.Op.
  private Change change;
  private PatchSet patchSet;
  private PatchSetInfo patchSetInfo;
  private ChangeMessage changeMessage;
  private ReviewerSet oldReviewers;
  private boolean oldWorkInProgressState;

  @Inject
  public PatchSetInserter(
      PermissionBackend permissionBackend,
      ApprovalsUtil approvalsUtil,
      ChangeMessagesUtil cmUtil,
      PatchSetInfoFactory patchSetInfoFactory,
      CommitValidators.Factory commitValidatorsFactory,
      ReplacePatchSetSender.Factory replacePatchSetFactory,
      PatchSetUtil psUtil,
      RevisionCreated revisionCreated,
      ProjectCache projectCache,
      WorkInProgressStateChanged wipStateChanged,
      @Assisted ChangeNotes notes,
      @Assisted PatchSet.Id psId,
      @Assisted ObjectId commitId) {
    this.permissionBackend = permissionBackend;
    this.approvalsUtil = approvalsUtil;
    this.cmUtil = cmUtil;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.commitValidatorsFactory = commitValidatorsFactory;
    this.replacePatchSetFactory = replacePatchSetFactory;
    this.psUtil = psUtil;
    this.revisionCreated = revisionCreated;
    this.projectCache = projectCache;
    this.wipStateChanged = wipStateChanged;

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
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws ResourceConflictException, IOException {
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

    if (message != null) {
      changeMessage =
          ChangeMessagesUtil.newMessage(
              patchSet.id(),
              ctx.getUser(),
              ctx.getWhen(),
              message,
              ChangeMessagesUtil.uploadedPatchSetTag(change.isWorkInProgress()));
      changeMessage.setMessage(message);
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
    if (changeMessage != null) {
      cmUtil.addChangeMessage(update, changeMessage);
    }
    return true;
  }

  @Override
  public void postUpdate(Context ctx) {
    NotifyResolver.Result notify = ctx.getNotify(change.getId());
    if (notify.shouldNotify() && sendEmail) {
      requireNonNull(changeMessage);
      try {
        ReplacePatchSetSender cm = replacePatchSetFactory.create(ctx.getProject(), change.getId());
        cm.setFrom(ctx.getAccountId());
        cm.setPatchSet(patchSet, patchSetInfo);
        cm.setChangeMessage(changeMessage.getMessage(), ctx.getWhen());
        cm.addReviewers(oldReviewers.byState(REVIEWER));
        cm.addExtraCC(oldReviewers.byState(CC));
        cm.setNotify(notify);
        cm.send();
      } catch (Exception err) {
        logger.atSevere().withCause(err).log(
            "Cannot send email for new patch set on change %s", change.getId());
      }
    }

    if (fireRevisionCreated) {
      revisionCreated.fire(change, patchSet, ctx.getAccount(), ctx.getWhen(), notify);
    }

    if (workInProgress != null && oldWorkInProgressState != workInProgress) {
      wipStateChanged.fire(change, patchSet, ctx.getAccount(), ctx.getWhen());
    }
  }

  private void validate(RepoContext ctx)
      throws AuthException, ResourceConflictException, IOException, PermissionBackendException {
    // Not allowed to create a new patch set if the current patch set is locked.
    psUtil.checkPatchSetNotLocked(origNotes);

    if (checkAddPatchSetPermission) {
      permissionBackend.user(ctx.getUser()).change(origNotes).check(ChangePermission.ADD_PATCH_SET);
    }
    projectCache.checkedGet(ctx.getProject()).checkStatePermitsWrite();
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
            projectCache.checkedGet(origNotes.getProjectName()).getProject(),
            origNotes.getChange().getDest().branch(),
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
