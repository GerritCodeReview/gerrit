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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.reviewdb.client.Change.INITIAL_PATCH_SET_ID;

import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.BanCommit;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.git.BatchUpdate.RepoContext;
import com.google.gerrit.server.git.GroupCollector;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.mail.CreateChangeSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.ssh.NoSshInfo;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class ChangeInserter extends BatchUpdate.InsertChangeOp {
  public static interface Factory {
    ChangeInserter create(RefControl ctl, Change c, RevCommit rc);
  }

  private static final Logger log =
      LoggerFactory.getLogger(ChangeInserter.class);

  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ChangeHooks hooks;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeMessagesUtil cmUtil;
  private final CreateChangeSender.Factory createChangeSenderFactory;
  private final WorkQueue workQueue;
  private final CommitValidators.Factory commitValidatorsFactory;

  private final RefControl refControl;
  private final IdentifiedUser user;
  private final Change change;
  private final PatchSet patchSet;
  private final RevCommit commit;

  // Fields exposed as setters.
  private String message;
  private CommitValidators.Policy validatePolicy =
      CommitValidators.Policy.GERRIT;
  private Set<Account.Id> reviewers;
  private Set<Account.Id> extraCC;
  private Map<String, Short> approvals;
  private RequestScopePropagator requestScopePropagator;
  private boolean runHooks;
  private boolean sendMail;
  private boolean updateRef;

  // Fields set during the insertion process.
  private ChangeMessage changeMessage;
  private PatchSetInfo patchSetInfo;

  @Inject
  ChangeInserter(PatchSetInfoFactory patchSetInfoFactory,
      ChangeHooks hooks,
      ApprovalsUtil approvalsUtil,
      ChangeMessagesUtil cmUtil,
      CreateChangeSender.Factory createChangeSenderFactory,
      WorkQueue workQueue,
      CommitValidators.Factory commitValidatorsFactory,
      @Assisted RefControl refControl,
      @Assisted Change change,
      @Assisted RevCommit commit) {
    String projectName = refControl.getProjectControl().getProject().getName();
    String refName = refControl.getRefName();
    checkArgument(projectName.equals(change.getProject().get())
          && refName.equals(change.getDest().get()),
        "RefControl for %s,%s does not match change destination %s",
        projectName, refName, change.getDest());

    this.patchSetInfoFactory = patchSetInfoFactory;
    this.hooks = hooks;
    this.approvalsUtil = approvalsUtil;
    this.cmUtil = cmUtil;
    this.createChangeSenderFactory = createChangeSenderFactory;
    this.workQueue = workQueue;
    this.commitValidatorsFactory = commitValidatorsFactory;

    this.refControl = refControl;
    this.change = change;
    this.commit = commit;
    this.reviewers = Collections.emptySet();
    this.extraCC = Collections.emptySet();
    this.approvals = Collections.emptyMap();
    this.runHooks = true;
    this.sendMail = true;
    this.updateRef = true;

    user = refControl.getUser().asIdentifiedUser();
    patchSet =
        new PatchSet(new PatchSet.Id(change.getId(), INITIAL_PATCH_SET_ID));
    patchSet.setCreatedOn(change.getCreatedOn());
    patchSet.setUploader(change.getOwner());
    patchSet.setRevision(new RevId(commit.name()));
  }

  @Override
  public Change getChange() {
    return change;
  }

  public IdentifiedUser getUser() {
    return user;
  }

  public ChangeInserter setMessage(String message) {
    this.message = message;
    return this;
  }

  public ChangeInserter setValidatePolicy(CommitValidators.Policy validate) {
    this.validatePolicy = checkNotNull(validate);
    return this;
  }

  public ChangeInserter setReviewers(Set<Account.Id> reviewers) {
    this.reviewers = reviewers;
    return this;
  }

  public ChangeInserter setExtraCC(Set<Account.Id> extraCC) {
    this.extraCC = extraCC;
    return this;
  }

  public ChangeInserter setDraft(boolean draft) {
    change.setStatus(draft ? Change.Status.DRAFT : Change.Status.NEW);
    patchSet.setDraft(draft);
    return this;
  }

  public ChangeInserter setGroups(Iterable<String> groups) {
    patchSet.setGroups(groups);
    return this;
  }

  public ChangeInserter setRunHooks(boolean runHooks) {
    this.runHooks = runHooks;
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

  public PatchSet getPatchSet() {
    return patchSet;
  }

  public ChangeInserter setApprovals(Map<String, Short> approvals) {
    this.approvals = approvals;
    return this;
  }

  public ChangeInserter setUpdateRef(boolean updateRef) {
    this.updateRef = updateRef;
    return this;
  }

  public ChangeMessage getChangeMessage() {
    if (message == null) {
      return null;
    }
    checkState(changeMessage != null,
        "getChangeMessage() only valid after inserting change");
    return changeMessage;
  }

  @Override
  public void updateRepo(RepoContext ctx)
      throws ResourceConflictException, IOException {
    validate(ctx);
    patchSetInfo = patchSetInfoFactory.get(
        ctx.getRevWalk(), commit, patchSet.getId());
    change.setCurrentPatchSet(patchSetInfo);
    if (!updateRef) {
      return;
    }
    ctx.addRefUpdate(
        new ReceiveCommand(ObjectId.zeroId(), commit, patchSet.getRefName()));
  }

  @Override
  public void updateChange(ChangeContext ctx) throws OrmException, IOException {
    ReviewDb db = ctx.getDb();
    ChangeControl ctl = ctx.getChangeControl();
    ChangeUpdate update = ctx.getChangeUpdate();
    if (patchSet.getGroups() == null) {
      patchSet.setGroups(GroupCollector.getDefaultGroups(patchSet));
    }
    db.patchSets().insert(Collections.singleton(patchSet));
    db.changes().insert(Collections.singleton(change));
    LabelTypes labelTypes = ctl.getProjectControl().getLabelTypes();
    approvalsUtil.addReviewers(db, update, labelTypes, change,
        patchSet, patchSetInfo, reviewers, Collections.<Account.Id> emptySet());
    approvalsUtil.addApprovals(db, update, labelTypes, patchSet,
        ctx.getChangeControl(), approvals);
    if (message != null) {
      changeMessage =
          new ChangeMessage(new ChangeMessage.Key(change.getId(),
              ChangeUtil.messageUUID(db)), user.getAccountId(),
              patchSet.getCreatedOn(), patchSet.getId());
      changeMessage.setMessage(message);
      cmUtil.addChangeMessage(db, update, changeMessage);
    }
  }

  @Override
  public void postUpdate(Context ctx) throws OrmException {
    if (sendMail) {
      Runnable sender = new Runnable() {
        @Override
        public void run() {
          try {
            CreateChangeSender cm =
                createChangeSenderFactory.create(change.getId());
            cm.setFrom(change.getOwner());
            cm.setPatchSet(patchSet, patchSetInfo);
            cm.addReviewers(reviewers);
            cm.addExtraCC(extraCC);
            cm.send();
          } catch (Exception e) {
            log.error("Cannot send email for new change " + change.getId(), e);
          }
        }

        @Override
        public String toString() {
          return "send-email newchange";
        }
      };
      if (requestScopePropagator != null) {
        workQueue.getDefaultQueue().submit(requestScopePropagator.wrap(sender));
      } else {
        sender.run();
      }
    }

    if (runHooks) {
      ReviewDb db = ctx.getDb();
      hooks.doPatchsetCreatedHook(change, patchSet, db);
      if (approvals != null && !approvals.isEmpty()) {
        hooks.doCommentAddedHook(
            change, user.getAccount(), patchSet, null, approvals, null, db);
      }
    }
  }

  private void validate(RepoContext ctx)
      throws IOException, ResourceConflictException {
    if (validatePolicy == CommitValidators.Policy.NONE) {
      return;
    }
    CommitValidators cv = commitValidatorsFactory.create(
        refControl, new NoSshInfo(), ctx.getRepository());

    String refName = patchSet.getId().toRefName();
    CommitReceivedEvent event = new CommitReceivedEvent(
        new ReceiveCommand(
            ObjectId.zeroId(),
            commit.getId(),
            refName),
        refControl.getProjectControl().getProject(),
        change.getDest().get(),
        commit,
        user);

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
