// Copyright (C) 2014 The Android Open Source Project
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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.Objects.requireNonNull;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.extensions.api.changes.DeleteVoteInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.ReviewerResource;
import com.google.gerrit.server.change.VoteResource;
import com.google.gerrit.server.extensions.events.VoteDeleted;
import com.google.gerrit.server.mail.send.DeleteVoteSender;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.mail.send.ReplyToChangeSender;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.RemoveReviewerControl;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.LabelVote;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class DeleteVote implements RestModifyView<VoteResource, DeleteVoteInput> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final BatchUpdate.Factory updateFactory;
  private final ApprovalsUtil approvalsUtil;
  private final PatchSetUtil psUtil;
  private final ChangeMessagesUtil cmUtil;
  private final IdentifiedUser.GenericFactory userFactory;
  private final VoteDeleted voteDeleted;
  private final DeleteVoteSender.Factory deleteVoteSenderFactory;
  private final NotifyResolver notifyResolver;
  private final RemoveReviewerControl removeReviewerControl;
  private final ProjectCache projectCache;
  private final MessageIdGenerator messageIdGenerator;

  @Inject
  DeleteVote(
      BatchUpdate.Factory updateFactory,
      ApprovalsUtil approvalsUtil,
      PatchSetUtil psUtil,
      ChangeMessagesUtil cmUtil,
      IdentifiedUser.GenericFactory userFactory,
      VoteDeleted voteDeleted,
      DeleteVoteSender.Factory deleteVoteSenderFactory,
      NotifyResolver notifyResolver,
      RemoveReviewerControl removeReviewerControl,
      ProjectCache projectCache,
      MessageIdGenerator messageIdGenerator) {
    this.updateFactory = updateFactory;
    this.approvalsUtil = approvalsUtil;
    this.psUtil = psUtil;
    this.cmUtil = cmUtil;
    this.userFactory = userFactory;
    this.voteDeleted = voteDeleted;
    this.deleteVoteSenderFactory = deleteVoteSenderFactory;
    this.notifyResolver = notifyResolver;
    this.removeReviewerControl = removeReviewerControl;
    this.projectCache = projectCache;
    this.messageIdGenerator = messageIdGenerator;
  }

  @Override
  public Response<Object> apply(VoteResource rsrc, DeleteVoteInput input)
      throws RestApiException, UpdateException, IOException, ConfigInvalidException {
    if (input == null) {
      input = new DeleteVoteInput();
    }
    if (input.label != null && !rsrc.getLabel().equals(input.label)) {
      throw new BadRequestException("label must match URL");
    }
    if (input.notify == null) {
      input.notify = NotifyHandling.ALL;
    }
    ReviewerResource r = rsrc.getReviewer();
    Change change = r.getChange();

    if (r.getRevisionResource() != null && !r.getRevisionResource().isCurrent()) {
      throw new MethodNotAllowedException("Cannot delete vote on non-current patch set");
    }

    try (BatchUpdate bu =
        updateFactory.create(
            change.getProject(), r.getChangeResource().getUser(), TimeUtil.nowTs())) {
      bu.setNotify(
          notifyResolver.resolve(
              firstNonNull(input.notify, NotifyHandling.ALL), input.notifyDetails));
      bu.addOp(
          change.getId(),
          new Op(
              projectCache
                  .get(r.getChange().getProject())
                  .orElseThrow(illegalState(r.getChange().getProject())),
              r.getReviewerUser().state(),
              rsrc.getLabel(),
              input));
      bu.execute();
    }

    return Response.none();
  }

  private class Op implements BatchUpdateOp {
    private final ProjectState projectState;
    private final AccountState accountState;
    private final String label;
    private final DeleteVoteInput input;

    private ChangeMessage changeMessage;
    private Change change;
    private PatchSet ps;
    private Map<String, Short> newApprovals = new HashMap<>();
    private Map<String, Short> oldApprovals = new HashMap<>();

    private Op(
        ProjectState projectState, AccountState accountState, String label, DeleteVoteInput input) {
      this.projectState = projectState;
      this.accountState = accountState;
      this.label = label;
      this.input = input;
    }

    @Override
    public boolean updateChange(ChangeContext ctx)
        throws AuthException, ResourceNotFoundException, IOException, PermissionBackendException {
      change = ctx.getChange();
      PatchSet.Id psId = change.currentPatchSetId();
      ps = psUtil.current(ctx.getNotes());

      boolean found = false;
      LabelTypes labelTypes = projectState.getLabelTypes(ctx.getNotes());

      Account.Id accountId = accountState.account().id();

      for (PatchSetApproval a :
          approvalsUtil.byPatchSetUser(
              ctx.getNotes(), psId, accountId, ctx.getRevWalk(), ctx.getRepoView().getConfig())) {
        if (labelTypes.byLabel(a.labelId()) == null) {
          continue; // Ignore undefined labels.
        } else if (!a.label().equals(label)) {
          // Populate map for non-matching labels, needed by VoteDeleted.
          newApprovals.put(a.label(), a.value());
          continue;
        } else {
          try {
            removeReviewerControl.checkRemoveReviewer(ctx.getNotes(), ctx.getUser(), a);
          } catch (AuthException e) {
            throw new AuthException("delete vote not permitted", e);
          }
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
      msg.append(" by ").append(userFactory.create(accountId).getNameEmail()).append("\n");
      changeMessage =
          ChangeMessagesUtil.newMessage(ctx, msg.toString(), ChangeMessagesUtil.TAG_DELETE_VOTE);
      cmUtil.addChangeMessage(ctx.getUpdate(psId), changeMessage);

      return true;
    }

    @Override
    public void postUpdate(Context ctx) {
      if (changeMessage == null) {
        return;
      }

      IdentifiedUser user = ctx.getIdentifiedUser();
      try {
        NotifyResolver.Result notify = ctx.getNotify(change.getId());
        if (notify.shouldNotify()) {
          ReplyToChangeSender emailSender =
              deleteVoteSenderFactory.create(ctx.getProject(), change.getId());
          emailSender.setFrom(user.getAccountId());
          emailSender.setChangeMessage(changeMessage.getMessage(), ctx.getWhen());
          emailSender.setNotify(notify);
          emailSender.setMessageId(
              messageIdGenerator.fromChangeUpdate(ctx.getRepoView(), change.currentPatchSetId()));
          emailSender.send();
        }
      } catch (Exception e) {
        logger.atSevere().withCause(e).log("Cannot email update for change %s", change.getId());
      }

      voteDeleted.fire(
          change,
          ps,
          accountState,
          newApprovals,
          oldApprovals,
          input.notify,
          changeMessage.getMessage(),
          user.state(),
          ctx.getWhen());
    }
  }
}
