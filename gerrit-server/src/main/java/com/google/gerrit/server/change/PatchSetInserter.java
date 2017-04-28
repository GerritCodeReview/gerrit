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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.CC;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalCopier;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.extensions.events.RevisionCreated;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.mail.send.ReplacePatchSetSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.ssh.NoSshInfo;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.RepoContext;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PatchSetInserter implements BatchUpdateOp {
  private static final Logger log = LoggerFactory.getLogger(PatchSetInserter.class);

  public interface Factory {
    PatchSetInserter create(ChangeControl ctl, PatchSet.Id psId, ObjectId commitId);
  }

  // Injected fields.
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final CommitValidators.Factory commitValidatorsFactory;
  private final ReplacePatchSetSender.Factory replacePatchSetFactory;
  private final RevisionCreated revisionCreated;
  private final ApprovalsUtil approvalsUtil;
  private final ApprovalCopier approvalCopier;
  private final ChangeMessagesUtil cmUtil;
  private final PatchSetUtil psUtil;

  // Assisted-injected fields.
  private final PatchSet.Id psId;
  private final ObjectId commitId;
  // Read prior to running the batch update, so must only be used during
  // updateRepo; updateChange and later must use the control from the
  // ChangeContext.
  private final ChangeControl origCtl;

  // Fields exposed as setters.
  private String message;
  private String description;
  private boolean validate = true;
  private boolean checkAddPatchSetPermission = true;
  private boolean draft;
  private List<String> groups = Collections.emptyList();
  private boolean fireRevisionCreated = true;
  private NotifyHandling notify = NotifyHandling.ALL;
  private ListMultimap<RecipientType, Account.Id> accountsToNotify = ImmutableListMultimap.of();
  private boolean allowClosed;
  private boolean copyApprovals = true;

  // Fields set during some phase of BatchUpdate.Op.
  private Change change;
  private PatchSet patchSet;
  private PatchSetInfo patchSetInfo;
  private ChangeMessage changeMessage;
  private ReviewerSet oldReviewers;

  @Inject
  public PatchSetInserter(
      ApprovalsUtil approvalsUtil,
      ApprovalCopier approvalCopier,
      ChangeMessagesUtil cmUtil,
      PatchSetInfoFactory patchSetInfoFactory,
      CommitValidators.Factory commitValidatorsFactory,
      ReplacePatchSetSender.Factory replacePatchSetFactory,
      PatchSetUtil psUtil,
      RevisionCreated revisionCreated,
      @Assisted ChangeControl ctl,
      @Assisted PatchSet.Id psId,
      @Assisted ObjectId commitId) {
    this.approvalsUtil = approvalsUtil;
    this.approvalCopier = approvalCopier;
    this.cmUtil = cmUtil;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.commitValidatorsFactory = commitValidatorsFactory;
    this.replacePatchSetFactory = replacePatchSetFactory;
    this.psUtil = psUtil;
    this.revisionCreated = revisionCreated;

    this.origCtl = ctl;
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

  public PatchSetInserter setValidate(boolean validate) {
    this.validate = validate;
    return this;
  }

  public PatchSetInserter setCheckAddPatchSetPermission(boolean checkAddPatchSetPermission) {
    this.checkAddPatchSetPermission = checkAddPatchSetPermission;
    return this;
  }

  public PatchSetInserter setDraft(boolean draft) {
    this.draft = draft;
    return this;
  }

  public PatchSetInserter setGroups(List<String> groups) {
    checkNotNull(groups, "groups may not be null");
    this.groups = groups;
    return this;
  }

  public PatchSetInserter setFireRevisionCreated(boolean fireRevisionCreated) {
    this.fireRevisionCreated = fireRevisionCreated;
    return this;
  }

  public PatchSetInserter setNotify(NotifyHandling notify) {
    this.notify = notify;
    return this;
  }

  public PatchSetInserter setAccountsToNotify(
      ListMultimap<RecipientType, Account.Id> accountsToNotify) {
    this.accountsToNotify = checkNotNull(accountsToNotify);
    return this;
  }

  public PatchSetInserter setAllowClosed(boolean allowClosed) {
    this.allowClosed = allowClosed;
    return this;
  }

  public PatchSetInserter setCopyApprovals(boolean copyApprovals) {
    this.copyApprovals = copyApprovals;
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
      throws AuthException, ResourceConflictException, IOException, OrmException {
    validate(ctx);
    ctx.addRefUpdate(ObjectId.zeroId(), commitId, getPatchSetId().toRefName());
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws ResourceConflictException, OrmException, IOException {
    ReviewDb db = ctx.getDb();
    ChangeControl ctl = ctx.getControl();

    change = ctx.getChange();
    ChangeUpdate update = ctx.getUpdate(psId);
    update.setSubjectForCommit("Create patch set " + psId.get());

    if (!change.getStatus().isOpen() && !allowClosed) {
      throw new ResourceConflictException(
          String.format(
              "Cannot create new patch set of change %s because it is %s",
              change.getId(), ChangeUtil.status(change)));
    }

    List<String> newGroups = groups;
    if (newGroups.isEmpty()) {
      PatchSet prevPs = psUtil.current(db, ctx.getNotes());
      if (prevPs != null) {
        newGroups = prevPs.getGroups();
      }
    }
    patchSet =
        psUtil.insert(
            db,
            ctx.getRevWalk(),
            ctx.getUpdate(psId),
            psId,
            commitId,
            draft,
            newGroups,
            null,
            description);

    if (notify != NotifyHandling.NONE) {
      oldReviewers = approvalsUtil.getReviewers(db, ctl.getNotes());
    }

    if (message != null) {
      changeMessage =
          ChangeMessagesUtil.newMessage(
              patchSet.getId(),
              ctx.getUser(),
              ctx.getWhen(),
              message,
              ChangeMessagesUtil.TAG_UPLOADED_PATCH_SET);
      changeMessage.setMessage(message);
    }

    patchSetInfo =
        patchSetInfoFactory.get(ctx.getRevWalk(), ctx.getRevWalk().parseCommit(commitId), psId);
    if (change.getStatus() != Change.Status.DRAFT && !allowClosed) {
      change.setStatus(Change.Status.NEW);
    }
    change.setCurrentPatchSet(patchSetInfo);
    if (copyApprovals) {
      approvalCopier.copy(db, ctl, patchSet);
    }
    if (changeMessage != null) {
      cmUtil.addChangeMessage(db, update, changeMessage);
    }
    return true;
  }

  @Override
  public void postUpdate(Context ctx) throws OrmException {
    if (notify != NotifyHandling.NONE || !accountsToNotify.isEmpty()) {
      try {
        ReplacePatchSetSender cm = replacePatchSetFactory.create(ctx.getProject(), change.getId());
        cm.setFrom(ctx.getAccountId());
        cm.setPatchSet(patchSet, patchSetInfo);
        cm.setChangeMessage(changeMessage.getMessage(), ctx.getWhen());
        cm.addReviewers(oldReviewers.byState(REVIEWER));
        cm.addExtraCC(oldReviewers.byState(CC));
        cm.setNotify(notify);
        cm.setAccountsToNotify(accountsToNotify);
        cm.send();
      } catch (Exception err) {
        log.error("Cannot send email for new patch set on change " + change.getId(), err);
      }
    }

    if (fireRevisionCreated) {
      revisionCreated.fire(change, patchSet, ctx.getAccount(), ctx.getWhen(), notify);
    }
  }

  private void validate(RepoContext ctx)
      throws AuthException, ResourceConflictException, IOException, OrmException {
    if (checkAddPatchSetPermission && !origCtl.canAddPatchSet(ctx.getDb())) {
      throw new AuthException("cannot add patch set");
    }
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
            origCtl.getProjectControl().getProject(),
            origCtl.getRefControl().getRefName(),
            ctx.getRevWalk().getObjectReader(),
            commitId,
            ctx.getIdentifiedUser())) {
      commitValidatorsFactory
          .forGerritCommits(origCtl.getRefControl(), new NoSshInfo(), ctx.getRevWalk())
          .validate(event);
    } catch (CommitValidationException e) {
      throw new ResourceConflictException(e.getFullMessage());
    }
  }
}
