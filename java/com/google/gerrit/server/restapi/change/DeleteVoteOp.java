// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.Objects.requireNonNull;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.DeleteVoteInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.extensions.events.VoteDeleted;
import com.google.gerrit.server.mail.send.DeleteVoteSender;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.mail.send.ReplyToChangeSender;
import com.google.gerrit.server.permissions.LabelRemovalPermission;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.DeleteVoteControl;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.RemoveReviewerControl;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.gerrit.server.util.AccountTemplateUtil;
import com.google.gerrit.server.util.LabelVote;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Updates the storage to delete vote(s). */
public class DeleteVoteOp implements BatchUpdateOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Factory to create {@link DeleteVoteOp} instances. */
  public interface Factory {
    DeleteVoteOp create(
        Project.NameKey projectState,
        AccountState reviewerToDeleteVoteFor,
        String label,
        DeleteVoteInput input,
        boolean enforcePermissions);
  }

  private final Project.NameKey projectName;
  private final AccountState reviewerToDeleteVoteFor;

  private final ProjectCache projectCache;
  private final ApprovalsUtil approvalsUtil;
  private final PatchSetUtil psUtil;
  private final ChangeMessagesUtil cmUtil;
  private final VoteDeleted voteDeleted;
  private final DeleteVoteSender.Factory deleteVoteSenderFactory;

  private final DeleteVoteControl deleteVoteControl;
  private final RemoveReviewerControl removeReviewerControl;
  private final MessageIdGenerator messageIdGenerator;

  private final String label;
  private final DeleteVoteInput input;
  private final boolean enforcePermissions;

  private String mailMessage;
  private Change change;
  private PatchSet ps;
  private Map<String, Short> newApprovals = new HashMap<>();
  private Map<String, Short> oldApprovals = new HashMap<>();

  @Inject
  public DeleteVoteOp(
      ProjectCache projectCache,
      ApprovalsUtil approvalsUtil,
      PatchSetUtil psUtil,
      ChangeMessagesUtil cmUtil,
      VoteDeleted voteDeleted,
      DeleteVoteSender.Factory deleteVoteSenderFactory,
      DeleteVoteControl deleteVoteControl,
      MessageIdGenerator messageIdGenerator,
      RemoveReviewerControl removeReviewerControl,
      @Assisted Project.NameKey projectName,
      @Assisted AccountState reviewerToDeleteVoteFor,
      @Assisted String label,
      @Assisted DeleteVoteInput input,
      @Assisted boolean enforcePermissions) {
    this.projectCache = projectCache;
    this.approvalsUtil = approvalsUtil;
    this.psUtil = psUtil;
    this.cmUtil = cmUtil;
    this.voteDeleted = voteDeleted;
    this.deleteVoteSenderFactory = deleteVoteSenderFactory;
    this.deleteVoteControl = deleteVoteControl;
    this.messageIdGenerator = messageIdGenerator;

    this.projectName = projectName;
    this.reviewerToDeleteVoteFor = reviewerToDeleteVoteFor;
    this.removeReviewerControl = removeReviewerControl;
    this.label = label;
    this.input = input;
    this.enforcePermissions = enforcePermissions;
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws AuthException, ResourceNotFoundException, IOException, PermissionBackendException {
    change = ctx.getChange();
    PatchSet.Id psId = change.currentPatchSetId();
    ps = psUtil.current(ctx.getNotes());

    boolean found = false;
    LabelTypes labelTypes =
        projectCache
            .get(projectName)
            .orElseThrow(illegalState(projectName))
            .getLabelTypes(ctx.getNotes());

    Account.Id accountId = reviewerToDeleteVoteFor.account().id();

    for (PatchSetApproval a : approvalsUtil.byPatchSetUser(ctx.getNotes(), psId, accountId)) {
      if (!labelTypes.byLabel(a.labelId()).isPresent()) {
        continue; // Ignore undefined labels.
      } else if (!a.label().equals(label)) {
        // Populate map for non-matching labels, needed by VoteDeleted.
        newApprovals.put(a.label(), a.value());
        continue;
      } else if (enforcePermissions) {
        checkPermissions(ctx, labelTypes.byLabel(a.labelId()).get(), a);
      }
      // Set the approval to 0 if vote is being removed.
      newApprovals.put(a.label(), (short) 0);
      found = true;

      // Set old value, as required by VoteDeleted.
      oldApprovals.put(a.label(), a.value());
      break;
    }
    if (!found) {
      throw new ResourceNotFoundException();
    }

    ctx.getUpdate(psId).removeApprovalFor(accountId, label);

    StringBuilder msg = new StringBuilder();
    msg.append("Removed ");
    LabelVote.appendTo(msg, label, requireNonNull(oldApprovals.get(label)));
    msg.append(" by ").append(AccountTemplateUtil.getAccountTemplate(accountId));
    if (input.reason != null) {
      msg.append("\n\n" + input.reason);
    }
    msg.append("\n");
    mailMessage = cmUtil.setChangeMessage(ctx, msg.toString(), ChangeMessagesUtil.TAG_DELETE_VOTE);
    return true;
  }

  @Override
  public void postUpdate(PostUpdateContext ctx) {
    if (mailMessage == null) {
      return;
    }

    CurrentUser user = ctx.getUser();
    try {
      NotifyResolver.Result notify = ctx.getNotify(change.getId());
      ReplyToChangeSender emailSender =
          deleteVoteSenderFactory.create(ctx.getProject(), change.getId());
      if (user.isIdentifiedUser()) {
        emailSender.setFrom(user.getAccountId());
      }
      emailSender.setChangeMessage(mailMessage, ctx.getWhen());
      emailSender.setNotify(notify);
      emailSender.setMessageId(
          messageIdGenerator.fromChangeUpdate(ctx.getRepoView(), change.currentPatchSetId()));
      emailSender.send();
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Cannot email update for change %s", change.getId());
    }
    voteDeleted.fire(
        ctx.getChangeData(change),
        ps,
        reviewerToDeleteVoteFor,
        newApprovals,
        oldApprovals,
        input.notify,
        mailMessage,
        user.isIdentifiedUser() ? user.asIdentifiedUser().state() : null,
        ctx.getWhen());
  }

  private void checkPermissions(ChangeContext ctx, LabelType labelType, PatchSetApproval approval)
      throws PermissionBackendException, AuthException {
    boolean permitted =
        removeReviewerControl.testRemoveReviewer(ctx.getNotes(), ctx.getUser(), approval)
            || deleteVoteControl.testDeleteVotePermissions(
                ctx.getUser(), ctx.getNotes(), approval, labelType);
    if (!permitted) {
      throw new AuthException(
          "Delete vote not permitted.",
          new AuthException(
              "Both "
                  + new LabelRemovalPermission.WithValue(labelType, approval.value())
                      .describeForException()
                  + " and remove-reviewer are not permitted"));
    }
  }
}
