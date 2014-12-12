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
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.DeleteVote.Input;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.util.LabelVote;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.IOException;
import java.util.Collections;

@Singleton
public class DeleteVote implements RestModifyView<VoteResource, Input> {
  public static class Input {
  }

  private final Provider<ReviewDb> dbProvider;
  private final ChangeUpdate.Factory updateFactory;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeMessagesUtil cmUtil;
  private final ChangeIndexer indexer;
  private final IdentifiedUser.GenericFactory userFactory;

  @Inject
  DeleteVote(Provider<ReviewDb> dbProvider,
      ChangeUpdate.Factory updateFactory,
      ApprovalsUtil approvalsUtil,
      ChangeMessagesUtil cmUtil,
      ChangeIndexer indexer,
      IdentifiedUser.GenericFactory userFactory) {
    this.dbProvider = dbProvider;
    this.updateFactory = updateFactory;
    this.approvalsUtil = approvalsUtil;
    this.cmUtil = cmUtil;
    this.indexer = indexer;
    this.userFactory = userFactory;
  }

  @Override
  public Response<?> apply(VoteResource rsrc, Input input)
      throws AuthException, ResourceNotFoundException, OrmException,
      IOException {
    ChangeControl ctl = rsrc.getReviewer().getControl();
    Change change = rsrc.getReviewer().getChange();
    Change.Id changeId = change.getId();
    ReviewDb db = dbProvider.get();
    ChangeUpdate update = updateFactory.create(ctl);
    LabelVote labelVote = rsrc.getVote();
    Account.Id accountId = rsrc.getReviewer().getUser().getAccountId();

    StringBuilder msg = new StringBuilder();
    db.changes().beginTransaction(changeId);
    try {
      PatchSetApproval psa = null;
      for (PatchSetApproval a : approvalsUtil.byPatchSetUser(
           db, ctl, change.currentPatchSetId(), accountId)) {
        // TODO(davido): Extend the ACL system to allow fine grained removal
        // of votes. E.g. allow removing a VRFY-1, but not a CRVW-2 by
        // implementing ctl.canRemoveVote(v) method.
        if (ctl.canRemoveReviewer(a)) {
          if (a.getLabel().equals(labelVote.label())
              && a.getValue() == rsrc.getVote().value()) {
            msg.append("Removed ")
                .append(a.getLabel()).append(formatLabelValue(a.getValue()))
                .append(" by ").append(userFactory.create(accountId)
                    .getNameEmail())
                .append("\n");
            psa = a;
            a.setValue((short)0);
            update.putApproval(labelVote.label(), (short)0);
            break;
          }
        } else {
          throw new AuthException("delete not permitted");
        }
      }
      if (psa == null) {
        throw new ResourceNotFoundException();
      }
      ChangeUtil.bumpRowVersionNotLastUpdatedOn(changeId, db);
      db.patchSetApprovals().update(Collections.singleton(psa));

      if (msg.length() > 0) {
        ChangeMessage changeMessage =
            new ChangeMessage(new ChangeMessage.Key(change.getId(),
                ChangeUtil.messageUUID(db)),
                ctl.getUser().getAccountId(),
                TimeUtil.nowTs(),
                change.currentPatchSetId());
        changeMessage.setMessage(msg.toString());
        cmUtil.addChangeMessage(db, update, changeMessage);
      }

      db.commit();
    } finally {
      db.rollback();
    }

    update.commit();
    indexer.index(db, change);
    return Response.none();
  }

  private static String formatLabelValue(short value) {
    if (value > 0) {
      return "+" + value;
    } else {
      return Short.toString(value);
    }
  }
}
