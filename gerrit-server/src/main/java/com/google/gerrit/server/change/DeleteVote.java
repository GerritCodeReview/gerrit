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
import com.google.gerrit.extensions.restapi.AuthException;
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
import com.google.gerrit.server.change.DeleteVote.Input;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.util.Collections;

@Singleton
public class DeleteVote implements RestModifyView<VoteResource, Input> {
  public static class Input {
  }

  private final Provider<ReviewDb> db;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeMessagesUtil cmUtil;
  private final IdentifiedUser.GenericFactory userFactory;

  @Inject
  DeleteVote(Provider<ReviewDb> db,
      BatchUpdate.Factory batchUpdateFactory,
      ApprovalsUtil approvalsUtil,
      ChangeMessagesUtil cmUtil,
      IdentifiedUser.GenericFactory userFactory) {
    this.db = db;
    this.batchUpdateFactory = batchUpdateFactory;
    this.approvalsUtil = approvalsUtil;
    this.cmUtil = cmUtil;
    this.userFactory = userFactory;
  }

  @Override
  public Response<?> apply(VoteResource rsrc, Input input)
      throws RestApiException, UpdateException {
    ReviewerResource r = rsrc.getReviewer();
    Change change = r.getChange();
    try (BatchUpdate bu = batchUpdateFactory.create(db.get(),
          change.getProject(), rsrc.getReviewer().getControl().getUser(),
          TimeUtil.nowTs())) {
      bu.addOp(change.getId(),
          new Op(r.getReviewerUser().getAccountId(), rsrc.getLabel()));
      bu.execute();
    }

    return Response.none();
  }

  private class Op extends BatchUpdate.Op {
    private final Account.Id accountId;
    private final String label;

    private Op(Account.Id accountId, String label) {
      this.accountId = accountId;
      this.label = label;
    }

    @Override
    public void updateChange(ChangeContext ctx)
        throws OrmException, AuthException, ResourceNotFoundException {
      IdentifiedUser user = ctx.getUser().asIdentifiedUser();
      Change change = ctx.getChange();
      ChangeControl ctl = ctx.getChangeControl();
      PatchSet.Id psId = change.currentPatchSetId();

      PatchSetApproval psa = null;
      StringBuilder msg = new StringBuilder();
      for (PatchSetApproval a : approvalsUtil.byPatchSetUser(
            ctx.getDb(), ctl, psId, accountId)) {
        if (ctl.canRemoveReviewer(a)) {
          if (a.getLabel().equals(label)) {
            msg.append("Removed ")
                .append(a.getLabel()).append(formatLabelValue(a.getValue()))
                .append(" by ").append(userFactory.create(user.getAccountId())
                    .getNameEmail())
                .append("\n");
            psa = a;
            a.setValue((short)0);
            ctx.getChangeUpdate().setPatchSetId(psId);
            ctx.getChangeUpdate().removeApproval(label);
            break;
          }
        } else {
          throw new AuthException("delete not permitted");
        }
      }
      if (psa == null) {
        throw new ResourceNotFoundException();
      }
      ChangeUtil.bumpRowVersionNotLastUpdatedOn(change.getId(), ctx.getDb());
      ctx.getDb().patchSetApprovals().update(Collections.singleton(psa));

      if (msg.length() > 0) {
        ChangeMessage changeMessage =
            new ChangeMessage(new ChangeMessage.Key(change.getId(),
                ChangeUtil.messageUUID(ctx.getDb())),
                user.getAccountId(),
                ctx.getWhen(),
                change.currentPatchSetId());
        changeMessage.setMessage(msg.toString());
        cmUtil.addChangeMessage(ctx.getDb(), ctx.getChangeUpdate(),
            changeMessage);
      }
    }
  }

  private static String formatLabelValue(short value) {
    if (value > 0) {
      return "+" + value;
    } else {
      return Short.toString(value);
    }
  }
}
