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

package com.google.gerrit.server.change;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.extensions.api.changes.DeleteVoteInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.extensions.events.VoteDeleted;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.mail.DeleteVoteSender;
import com.google.gerrit.server.mail.ReplyToChangeSender;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class DeleteVote
    implements RestModifyView<VoteResource, DeleteVoteInput> {
  private static final Logger log = LoggerFactory.getLogger(DeleteVote.class);

  private final Provider<ReviewDb> db;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final ApprovalsUtil approvalsUtil;
  private final PatchSetUtil psUtil;
  private final ChangeMessagesUtil cmUtil;
  private final IdentifiedUser.GenericFactory userFactory;
  private final VoteDeleted voteDeleted;
  private final DeleteVoteSender.Factory deleteVoteSenderFactory;

  @Inject
  DeleteVote(Provider<ReviewDb> db,
      BatchUpdate.Factory batchUpdateFactory,
      ApprovalsUtil approvalsUtil,
      PatchSetUtil psUtil,
      ChangeMessagesUtil cmUtil,
      IdentifiedUser.GenericFactory userFactory,
      VoteDeleted voteDeleted,
      DeleteVoteSender.Factory deleteVoteSenderFactory) {
    this.db = db;
    this.batchUpdateFactory = batchUpdateFactory;
    this.approvalsUtil = approvalsUtil;
    this.psUtil = psUtil;
    this.cmUtil = cmUtil;
    this.userFactory = userFactory;
    this.voteDeleted = voteDeleted;
    this.deleteVoteSenderFactory = deleteVoteSenderFactory;
  }

  @Override
  public Response<?> apply(VoteResource rsrc, DeleteVoteInput input)
      throws RestApiException, UpdateException {
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
    try (BatchUpdate bu = batchUpdateFactory.create(db.get(),
          change.getProject(), r.getControl().getUser(), TimeUtil.nowTs())) {
      bu.addOp(change.getId(),
          new Op(r.getReviewerUser().getAccountId(), rsrc.getLabel(), input));
      bu.execute();
    }

    return Response.none();
  }

  private class Op extends BatchUpdate.Op {
    private final Account.Id accountId;
    private final String label;
    private final DeleteVoteInput input;
    private ChangeMessage changeMessage;
    private Change change;
    private PatchSet ps;
    private Map<String, Short> newApprovals = new HashMap<>();
    private Map<String, Short> oldApprovals = new HashMap<>();

    private Op(Account.Id accountId, String label, DeleteVoteInput input) {
      this.accountId = accountId;
      this.label = label;
      this.input = input;
    }

    @Override
    public boolean updateChange(ChangeContext ctx)
        throws OrmException, AuthException, ResourceNotFoundException {
      ChangeControl ctl = ctx.getControl();
      change = ctl.getChange();
      PatchSet.Id psId = change.currentPatchSetId();
      ps = psUtil.current(db.get(), ctl.getNotes());

      PatchSetApproval psa = null;
      StringBuilder msg = new StringBuilder();

      // get all of the current approvals
      LabelTypes labelTypes = ctx.getControl().getLabelTypes();
      Map<String, Short> currentApprovals = new HashMap<>();
      for (LabelType lt : labelTypes.getLabelTypes()) {
        currentApprovals.put(lt.getName(), (short) 0);
        for (PatchSetApproval a : approvalsUtil.byPatchSetUser(
            ctx.getDb(), ctl, psId, accountId)) {
          if (lt.getLabelId().equals(a.getLabelId())) {
            currentApprovals.put(lt.getName(), a.getValue());
          }
        }
      }
      // removing votes so we need to determine the new set of approval scores
      newApprovals.putAll(currentApprovals);
      for (PatchSetApproval a : approvalsUtil.byPatchSetUser(
            ctx.getDb(), ctl, psId, accountId)) {
        if (ctl.canRemoveReviewer(a)) {
          if (a.getLabel().equals(label)) {
            // set the approval to 0 if vote is being removed
            newApprovals.put(a.getLabel(), (short) 0);
            // set old value only if the vote changed
            oldApprovals.put(a.getLabel(), a.getValue());
            msg.append("Removed ")
                .append(a.getLabel()).append(formatLabelValue(a.getValue()))
                .append(" by ").append(userFactory.create(a.getAccountId())
                    .getNameEmail())
                .append("\n");
            psa = a;
            a.setValue((short)0);
            ctx.getUpdate(psId).removeApprovalFor(a.getAccountId(), label);
            break;
          }
        } else {
          throw new AuthException("delete vote not permitted");
        }
      }
      if (psa == null) {
        throw new ResourceNotFoundException();
      }
      ctx.getDb().patchSetApprovals().update(Collections.singleton(psa));

      if (msg.length() > 0) {
        changeMessage =
            new ChangeMessage(new ChangeMessage.Key(change.getId(),
                ChangeUtil.messageUUID(ctx.getDb())),
                ctx.getAccountId(),
                ctx.getWhen(),
                change.currentPatchSetId());
        changeMessage.setMessage(msg.toString());
        cmUtil.addChangeMessage(ctx.getDb(), ctx.getUpdate(psId),
            changeMessage);
      }
      return true;
    }

    @Override
    public void postUpdate(Context ctx) {
      if (changeMessage == null) {
        return;
      }

      IdentifiedUser user = ctx.getIdentifiedUser();
      if (input.notify.compareTo(NotifyHandling.NONE) > 0) {
        try {
          ReplyToChangeSender cm = deleteVoteSenderFactory.create(
              ctx.getProject(), change.getId());
          cm.setFrom(user.getAccountId());
          cm.setChangeMessage(changeMessage.getMessage(), ctx.getWhen());
          cm.setNotify(input.notify);
          cm.send();
        } catch (Exception e) {
          log.error("Cannot email update for change " + change.getId(), e);
        }
      }

      voteDeleted.fire(change, ps,
          newApprovals, oldApprovals, input.notify, changeMessage.getMessage(),
          user.getAccount(), ctx.getWhen());
    }
  }

  private static String formatLabelValue(short value) {
    if (value > 0) {
      return "+" + value;
    }
    return Short.toString(value);
  }
}
