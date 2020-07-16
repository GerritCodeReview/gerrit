// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.extensions.api.changes.DeleteReviewerInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.extensions.events.ReviewerDeleted;
import com.google.gerrit.server.mail.send.DeleteReviewerSender;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.RemoveReviewerControl;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.RepoView;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DeleteReviewerOp implements BatchUpdateOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    DeleteReviewerOp create(AccountState reviewerAccount, DeleteReviewerInput input);
  }

  private final ApprovalsUtil approvalsUtil;
  private final PatchSetUtil psUtil;
  private final ChangeMessagesUtil cmUtil;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ReviewerDeleted reviewerDeleted;
  private final Provider<IdentifiedUser> user;
  private final DeleteReviewerSender.Factory deleteReviewerSenderFactory;
  private final RemoveReviewerControl removeReviewerControl;
  private final ProjectCache projectCache;
  private final MessageIdGenerator messageIdGenerator;

  private final AccountState reviewer;
  private final DeleteReviewerInput input;

  ChangeMessage changeMessage;
  Change currChange;
  PatchSet currPs;
  Map<String, Short> newApprovals = new HashMap<>();
  Map<String, Short> oldApprovals = new HashMap<>();

  @Inject
  DeleteReviewerOp(
      ApprovalsUtil approvalsUtil,
      PatchSetUtil psUtil,
      ChangeMessagesUtil cmUtil,
      IdentifiedUser.GenericFactory userFactory,
      ReviewerDeleted reviewerDeleted,
      Provider<IdentifiedUser> user,
      DeleteReviewerSender.Factory deleteReviewerSenderFactory,
      RemoveReviewerControl removeReviewerControl,
      ProjectCache projectCache,
      MessageIdGenerator messageIdGenerator,
      @Assisted AccountState reviewerAccount,
      @Assisted DeleteReviewerInput input) {
    this.approvalsUtil = approvalsUtil;
    this.psUtil = psUtil;
    this.cmUtil = cmUtil;
    this.userFactory = userFactory;
    this.reviewerDeleted = reviewerDeleted;
    this.user = user;
    this.deleteReviewerSenderFactory = deleteReviewerSenderFactory;
    this.removeReviewerControl = removeReviewerControl;
    this.projectCache = projectCache;
    this.messageIdGenerator = messageIdGenerator;
    this.reviewer = reviewerAccount;
    this.input = input;
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws AuthException, ResourceNotFoundException, PermissionBackendException, IOException {
    Account.Id reviewerId = reviewer.account().id();
    // Check of removing this reviewer (even if there is no vote processed by the loop below) is OK
    removeReviewerControl.checkRemoveReviewer(ctx.getNotes(), ctx.getUser(), reviewerId);

    if (!approvalsUtil.getReviewers(ctx.getNotes()).all().contains(reviewerId)) {
      throw new ResourceNotFoundException();
    }
    currChange = ctx.getChange();
    currPs = psUtil.current(ctx.getNotes());

    LabelTypes labelTypes =
        projectCache
            .get(ctx.getProject())
            .orElseThrow(illegalState(ctx.getProject()))
            .getLabelTypes(ctx.getNotes());
    // removing a reviewer will remove all her votes
    for (LabelType lt : labelTypes.getLabelTypes()) {
      newApprovals.put(lt.getName(), (short) 0);
    }

    StringBuilder msg = new StringBuilder();
    msg.append("Removed reviewer " + reviewer.account().fullName());
    StringBuilder removedVotesMsg = new StringBuilder();
    removedVotesMsg.append(" with the following votes:\n\n");
    boolean votesRemoved = false;
    for (PatchSetApproval a : approvals(ctx, reviewerId)) {
      // Check if removing this vote is OK
      removeReviewerControl.checkRemoveReviewer(ctx.getNotes(), ctx.getUser(), a);
      if (a.patchSetId().equals(currPs.id()) && a.value() != 0) {
        oldApprovals.put(a.label(), a.value());
        removedVotesMsg
            .append("* ")
            .append(a.label())
            .append(formatLabelValue(a.value()))
            .append(" by ")
            .append(userFactory.create(a.accountId()).getNameEmail())
            .append("\n");
        votesRemoved = true;
      }
    }

    if (votesRemoved) {
      msg.append(removedVotesMsg);
    } else {
      msg.append(".");
    }
    ChangeUpdate update = ctx.getUpdate(currPs.id());
    update.removeReviewer(reviewerId);

    changeMessage =
        ChangeMessagesUtil.newMessage(ctx, msg.toString(), ChangeMessagesUtil.TAG_DELETE_REVIEWER);
    cmUtil.addChangeMessage(update, changeMessage);

    return true;
  }

  @Override
  public void postUpdate(Context ctx) {
    NotifyResolver.Result notify = ctx.getNotify(currChange.getId());
    if (input.notify == null
        && currChange.isWorkInProgress()
        && !oldApprovals.isEmpty()
        && notify.handling().compareTo(NotifyHandling.OWNER) < 0) {
      // Override NotifyHandling from the context to notify owner if votes were removed on a WIP
      // change.
      notify = notify.withHandling(NotifyHandling.OWNER);
    }
    try {
      if (notify.shouldNotify()) {
        emailReviewers(ctx.getProject(), currChange, changeMessage, notify, ctx.getRepoView());
      }
    } catch (Exception err) {
      logger.atSevere().withCause(err).log("Cannot email update for change %s", currChange.getId());
    }
    reviewerDeleted.fire(
        currChange,
        currPs,
        reviewer,
        ctx.getAccount(),
        changeMessage.getMessage(),
        newApprovals,
        oldApprovals,
        notify.handling(),
        ctx.getWhen());
  }

  private Iterable<PatchSetApproval> approvals(ChangeContext ctx, Account.Id accountId) {
    Iterable<PatchSetApproval> approvals;
    approvals = approvalsUtil.byChange(ctx.getNotes()).values();
    return Iterables.filter(approvals, psa -> accountId.equals(psa.accountId()));
  }

  private String formatLabelValue(short value) {
    if (value > 0) {
      return "+" + value;
    }
    return Short.toString(value);
  }

  private void emailReviewers(
      Project.NameKey projectName,
      Change change,
      ChangeMessage changeMessage,
      NotifyResolver.Result notify,
      RepoView repoView)
      throws EmailException {
    Account.Id userId = user.get().getAccountId();
    if (userId.equals(reviewer.account().id())) {
      // The user knows they removed themselves, don't bother emailing them.
      return;
    }
    DeleteReviewerSender emailSender =
        deleteReviewerSenderFactory.create(projectName, change.getId());
    emailSender.setFrom(userId);
    emailSender.addReviewers(Collections.singleton(reviewer.account().id()));
    emailSender.setChangeMessage(changeMessage.getMessage(), changeMessage.getWrittenOn());
    emailSender.setNotify(notify);
    emailSender.setMessageId(
        messageIdGenerator.fromChangeUpdate(repoView, change.currentPatchSetId()));
    emailSender.send();
  }
}
