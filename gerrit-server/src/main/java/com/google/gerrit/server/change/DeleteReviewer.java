// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
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
import com.google.gerrit.server.change.DeleteReviewer.Input;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.IOException;
import java.util.List;

@Singleton
public class DeleteReviewer implements RestModifyView<ReviewerResource, Input> {
  public static class Input {
  }

  private final Provider<ReviewDb> dbProvider;
  private final ChangeUpdate.Factory updateFactory;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeMessagesUtil cmUtil;
  private final ChangeIndexer indexer;
  private final IdentifiedUser.GenericFactory userFactory;

  @Inject
  DeleteReviewer(Provider<ReviewDb> dbProvider,
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
  public Response<?> apply(ReviewerResource rsrc, Input input)
      throws AuthException, ResourceNotFoundException, OrmException,
      IOException {
    ChangeControl control = rsrc.getControl();
    Change.Id changeId = rsrc.getChangeId();
    ReviewDb db = dbProvider.get();
    ChangeUpdate update = updateFactory.create(rsrc.getControl());

    StringBuilder msg = new StringBuilder();
    db.changes().beginTransaction(changeId);
    try {
      List<PatchSetApproval> del = Lists.newArrayList();
      for (PatchSetApproval a : approvals(db, rsrc)) {
        if (control.canRemoveReviewer(a)) {
          del.add(a);
          if (a.getPatchSetId().equals(control.getChange().currentPatchSetId())
              && a.getValue() != 0) {
            if (msg.length() == 0) {
              msg.append("Removed the following votes:\n\n");
            }
            msg.append("* ")
                .append(a.getLabel()).append(formatLabelValue(a.getValue()))
                .append(" by ").append(userFactory.create(a.getAccountId()).getNameEmail())
                .append("\n");
          }
        } else {
          throw new AuthException("delete not permitted");
        }
      }
      if (del.isEmpty()) {
        throw new ResourceNotFoundException();
      }
      ChangeUtil.bumpRowVersionNotLastUpdatedOn(rsrc.getChangeId(), db);
      db.patchSetApprovals().delete(del);
      update.removeReviewer(rsrc.getReviewerUser().getAccountId());

      if (msg.length() > 0) {
        ChangeMessage changeMessage =
            new ChangeMessage(new ChangeMessage.Key(rsrc.getChangeId(),
                ChangeUtil.messageUUID(db)),
                control.getUser().getAccountId(),
                TimeUtil.nowTs(), rsrc.getChange().currentPatchSetId());
        changeMessage.setMessage(msg.toString());
        cmUtil.addChangeMessage(db, update, changeMessage);
      }

      db.commit();
    } finally {
      db.rollback();
    }
    update.commit();
    indexer.index(db, rsrc.getChange());
    return Response.none();
  }

  private static String formatLabelValue(short value) {
    if (value > 0) {
      return "+" + value;
    } else {
      return Short.toString(value);
    }
  }

  private Iterable<PatchSetApproval> approvals(ReviewDb db,
      ReviewerResource rsrc) throws OrmException {
    final Account.Id user = rsrc.getReviewerUser().getAccountId();
    return Iterables.filter(
        approvalsUtil.byChange(db, rsrc.getChangeResource().getNotes())
            .values(),
        new Predicate<PatchSetApproval>() {
          @Override
          public boolean apply(PatchSetApproval input) {
            return user.equals(input.getAccountId());
          }
        });
  }
}
