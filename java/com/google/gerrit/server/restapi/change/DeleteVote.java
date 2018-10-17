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

import static java.util.Objects.requireNonNull;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.extensions.api.changes.DeleteVoteInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.change.NotifyUtil;
import com.google.gerrit.server.change.ReviewerResource;
import com.google.gerrit.server.change.VoteResource;
import com.google.gerrit.server.extensions.events.VoteDeleted;
import com.google.gerrit.server.mail.send.DeleteVoteSender;
import com.google.gerrit.server.mail.send.ReplyToChangeSender;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.RemoveReviewerControl;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.LabelVote;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class DeleteVote extends RetryingRestModifyView<VoteResource, DeleteVoteInput, Response<?>> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<ReviewDb> db;
  private final ApprovalsUtil approvalsUtil;
  private final PatchSetUtil psUtil;
  private final ChangeMessagesUtil cmUtil;
  private final IdentifiedUser.GenericFactory userFactory;
  private final VoteDeleted voteDeleted;
  private final DeleteVoteSender.Factory deleteVoteSenderFactory;
  private final NotifyUtil notifyUtil;
  private final RemoveReviewerControl removeReviewerControl;
  private final ProjectCache projectCache;

  @Inject
  DeleteVote(
      Provider<ReviewDb> db,
      RetryHelper retryHelper,
      ApprovalsUtil approvalsUtil,
      PatchSetUtil psUtil,
      ChangeMessagesUtil cmUtil,
      IdentifiedUser.GenericFactory userFactory,
      VoteDeleted voteDeleted,
      DeleteVoteSender.Factory deleteVoteSenderFactory,
      NotifyUtil notifyUtil,
      RemoveReviewerControl removeReviewerControl,
      ProjectCache projectCache) {
    super(retryHelper);
    this.db = db;
    this.approvalsUtil = approvalsUtil;
    this.psUtil = psUtil;
    this.cmUtil = cmUtil;
    this.userFactory = userFactory;
    this.voteDeleted = voteDeleted;
    this.deleteVoteSenderFactory = deleteVoteSenderFactory;
    this.notifyUtil = notifyUtil;
    this.removeReviewerControl = removeReviewerControl;
    this.projectCache = projectCache;
  }

  @Override
  protected Response<?> applyImpl(
      BatchUpdate.Factory updateFactory, VoteResource rsrc, DeleteVoteInput input)
      throws RestApiException, UpdateException, IOException {
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
            db.get(), change.getProject(), r.getChangeResource().getUser(), TimeUtil.nowTs())) {
      bu.addOp(
          change.getId(),
          new Op(
              projectCache.checkedGet(r.getChange().getProject()),
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
        throws OrmException, AuthException, ResourceNotFoundException, IOException,
            PermissionBackendException {
      change = ctx.getChange();
      PatchSet.Id psId = change.currentPatchSetId();
      ps = psUtil.current(db.get(), ctx.getNotes());

      boolean found = false;
      LabelTypes labelTypes = projectState.getLabelTypes(ctx.getNotes());

      Account.Id accountId = accountState.getAccount().getId();

      for (PatchSetApproval a :
          approvalsUtil.byPatchSetUser(
              ctx.getDb(),
              ctx.getNotes(),
              psId,
              accountId,
              ctx.getRevWalk(),
              ctx.getRepoView().getConfig())) {
        if (labelTypes.byLabel(a.getLabelId()) == null) {
          continue; // Ignore undefined labels.
        } else if (!a.getLabel().equals(label)) {
          // Populate map for non-matching labels, needed by VoteDeleted.
          newApprovals.put(a.getLabel(), a.getValue());
          continue;
        } else {
          try {
            removeReviewerControl.checkRemoveReviewer(ctx.getNotes(), ctx.getUser(), a);
          } catch (AuthException e) {
            throw new AuthException("delete vote not permitted", e);
          }
        }
        // Set the approval to 0 if vote is being removed.
        newApprovals.put(a.getLabel(), (short) 0);
        found = true;

        // Set old value, as required by VoteDeleted.
        oldApprovals.put(a.getLabel(), a.getValue());
        break;
      }
      if (!found) {
        throw new ResourceNotFoundException();
      }

      ctx.getUpdate(psId).removeApprovalFor(accountId, label);
      ctx.getDb().patchSetApprovals().upsert(Collections.singleton(deletedApproval(ctx)));

      StringBuilder msg = new StringBuilder();
      msg.append("Removed ");
      LabelVote.appendTo(msg, label, requireNonNull(oldApprovals.get(label)));
      msg.append(" by ").append(userFactory.create(accountId).getNameEmail()).append("\n");
      changeMessage =
          ChangeMessagesUtil.newMessage(ctx, msg.toString(), ChangeMessagesUtil.TAG_DELETE_VOTE);
      cmUtil.addChangeMessage(ctx.getDb(), ctx.getUpdate(psId), changeMessage);

      return true;
    }

    private PatchSetApproval deletedApproval(ChangeContext ctx) {
      // Set the effective user to the account we're trying to remove, and don't
      // set the real user; this preserves the calling user as the NoteDb
      // committer.
      return new PatchSetApproval(
          new PatchSetApproval.Key(
              ps.getId(), accountState.getAccount().getId(), new LabelId(label)),
          (short) 0,
          ctx.getWhen());
    }

    @Override
    public void postUpdate(Context ctx) {
      if (changeMessage == null) {
        return;
      }

      IdentifiedUser user = ctx.getIdentifiedUser();
      if (NotifyUtil.shouldNotify(input.notify, input.notifyDetails)) {
        try {
          ReplyToChangeSender cm = deleteVoteSenderFactory.create(ctx.getProject(), change.getId());
          cm.setFrom(user.getAccountId());
          cm.setChangeMessage(changeMessage.getMessage(), ctx.getWhen());
          cm.setNotify(input.notify);
          cm.setAccountsToNotify(notifyUtil.resolveAccounts(input.notifyDetails));
          cm.send();
        } catch (Exception e) {
          logger.atSevere().withCause(e).log("Cannot email update for change %s", change.getId());
        }
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
