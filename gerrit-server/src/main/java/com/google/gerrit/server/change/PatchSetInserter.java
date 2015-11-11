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

import com.google.common.collect.SetMultimap;
import com.google.gerrit.common.ChangeHooks;
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
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.extensions.events.RevisionCreated;
import com.google.gerrit.server.git.BanCommit;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.git.BatchUpdate.RepoContext;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.mail.ReplacePatchSetSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.ssh.NoSshInfo;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class PatchSetInserter extends BatchUpdate.Op {
  private static final Logger log =
      LoggerFactory.getLogger(PatchSetInserter.class);

  public static interface Factory {
    PatchSetInserter create(RefControl refControl, PatchSet.Id psId,
        RevCommit commit);
  }

  // Injected fields.
  private final ChangeHooks hooks;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ReviewDb db;
  private final CommitValidators.Factory commitValidatorsFactory;
  private final ReplacePatchSetSender.Factory replacePatchSetFactory;
  private final RevisionCreated revisionCreated;
  private final ApprovalsUtil approvalsUtil;
  private final ApprovalCopier approvalCopier;
  private final ChangeMessagesUtil cmUtil;
  private final PatchSetUtil psUtil;

  // Assisted-injected fields.
  private final PatchSet.Id psId;
  private final RevCommit commit;
  private final RefControl refControl;

  // Fields exposed as setters.
  private SshInfo sshInfo;
  private String message;
  private CommitValidators.Policy validatePolicy =
      CommitValidators.Policy.GERRIT;
  private boolean draft;
  private List<String> groups = Collections.emptyList();
  private boolean runHooks = true;
  private boolean sendMail = true;
  private boolean allowClosed;
  private boolean copyApprovals = true;

  // Fields set during some phase of BatchUpdate.Op.
  private Change change;
  private PatchSet patchSet;
  private PatchSetInfo patchSetInfo;
  private ChangeMessage changeMessage;
  private SetMultimap<ReviewerStateInternal, Account.Id> oldReviewers;

  @AssistedInject
  public PatchSetInserter(ChangeHooks hooks,
      ReviewDb db,
      ApprovalsUtil approvalsUtil,
      ApprovalCopier approvalCopier,
      ChangeMessagesUtil cmUtil,
      PatchSetInfoFactory patchSetInfoFactory,
      CommitValidators.Factory commitValidatorsFactory,
      ReplacePatchSetSender.Factory replacePatchSetFactory,
      PatchSetUtil psUtil,
      RevisionCreated revisionCreated,
      @Assisted RefControl refControl,
      @Assisted PatchSet.Id psId,
      @Assisted RevCommit commit) {
    this.hooks = hooks;
    this.db = db;
    this.approvalsUtil = approvalsUtil;
    this.approvalCopier = approvalCopier;
    this.cmUtil = cmUtil;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.commitValidatorsFactory = commitValidatorsFactory;
    this.replacePatchSetFactory = replacePatchSetFactory;
    this.psUtil = psUtil;
    this.revisionCreated = revisionCreated;

    this.refControl = refControl;
    this.psId = psId;
    this.commit = commit;
  }

  public PatchSet.Id getPatchSetId() {
    return psId;
  }

  public PatchSetInserter setMessage(String message) {
    this.message = message;
    return this;
  }

  public PatchSetInserter setSshInfo(SshInfo sshInfo) {
    this.sshInfo = sshInfo;
    return this;
  }

  public PatchSetInserter setValidatePolicy(CommitValidators.Policy validate) {
    this.validatePolicy = checkNotNull(validate);
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

  public PatchSetInserter setRunHooks(boolean runHooks) {
    this.runHooks = runHooks;
    return this;
  }

  public PatchSetInserter setSendMail(boolean sendMail) {
    this.sendMail = sendMail;
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
      throws ResourceConflictException, IOException {
    init();
    validate(ctx);
    ctx.addRefUpdate(new ReceiveCommand(ObjectId.zeroId(),
        commit, getPatchSetId().toRefName(), ReceiveCommand.Type.CREATE));
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws ResourceConflictException, OrmException, IOException {
    ChangeControl ctl = ctx.getControl();

    change = ctx.getChange();
    ChangeUpdate update = ctx.getUpdate(psId);
    update.setSubjectForCommit("Create patch set " + psId.get());

    if (!change.getStatus().isOpen() && !allowClosed) {
      throw new ResourceConflictException(String.format(
          "Cannot create new patch set of change %s because it is %s",
          change.getId(), change.getStatus().name().toLowerCase()));
    }

    List<String> newGroups = groups;
    if (newGroups.isEmpty()) {
      PatchSet prevPs = psUtil.current(ctx.getDb(), ctx.getNotes());
      if (prevPs != null) {
        newGroups = prevPs.getGroups();
      }
    }
    patchSet = psUtil.insert(ctx.getDb(), ctx.getRevWalk(), ctx.getUpdate(psId),
        psId, commit, draft, newGroups, null);

    if (sendMail) {
      oldReviewers = approvalsUtil.getReviewers(db, ctl.getNotes());
    }

    if (message != null) {
      changeMessage = new ChangeMessage(
          new ChangeMessage.Key(ctl.getId(), ChangeUtil.messageUUID(db)),
          ctx.getUser().getAccountId(), ctx.getWhen(), patchSet.getId());
      changeMessage.setMessage(message);
    }

    patchSetInfo = patchSetInfoFactory.get(ctx.getRevWalk(), commit, psId);
    if (change.getStatus() != Change.Status.DRAFT && !allowClosed) {
      change.setStatus(Change.Status.NEW);
    }
    change.setCurrentPatchSet(patchSetInfo);
    ctx.saveChange();
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
    if (sendMail) {
      try {
        ReplacePatchSetSender cm = replacePatchSetFactory.create(
            ctx.getProject(), change.getId());
        cm.setFrom(ctx.getUser().getAccountId());
        cm.setPatchSet(patchSet, patchSetInfo);
        cm.setChangeMessage(changeMessage);
        cm.addReviewers(oldReviewers.get(REVIEWER));
        cm.addExtraCC(oldReviewers.get(CC));
        cm.send();
      } catch (Exception err) {
        log.error("Cannot send email for new patch set on change "
            + change.getId(), err);
      }
    }

    if (runHooks) {
      revisionCreated.fire(change, patchSet, ctx.getUser().getAccountId());
      hooks.doPatchsetCreatedHook(change, patchSet, ctx.getDb());
    }
  }

  private void init() {
    if (sshInfo == null) {
      sshInfo = new NoSshInfo();
    }
  }

  private void validate(RepoContext ctx)
      throws ResourceConflictException, IOException {
    CommitValidators cv = commitValidatorsFactory.create(
        refControl, sshInfo, ctx.getRepository());

    String refName = getPatchSetId().toRefName();
    CommitReceivedEvent event = new CommitReceivedEvent(
        new ReceiveCommand(
            ObjectId.zeroId(),
            commit.getId(),
            refName.substring(0, refName.lastIndexOf('/') + 1) + "new"),
        refControl.getProjectControl().getProject(), refControl.getRefName(),
        commit, ctx.getUser().asIdentifiedUser());

    try {
      switch (validatePolicy) {
      case RECEIVE_COMMITS:
        NoteMap rejectCommits = BanCommit.loadRejectCommitsMap(
            ctx.getRepository(), ctx.getRevWalk());
        cv.validateForReceiveCommits(event, rejectCommits);
        break;
      case GERRIT:
        cv.validateForGerritCommits(event);
        break;
      case NONE:
        break;
      }
    } catch (CommitValidationException e) {
      throw new ResourceConflictException(e.getFullMessage());
    }
  }
}
