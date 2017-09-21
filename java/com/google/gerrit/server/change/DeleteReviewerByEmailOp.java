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

import com.google.gerrit.extensions.api.changes.DeleteReviewerInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.mail.send.DeleteReviewerSender;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteReviewerByEmailOp implements BatchUpdateOp {
  private static final Logger log = LoggerFactory.getLogger(DeleteReviewer.class);

  public interface Factory {
    DeleteReviewerByEmailOp create(Address reviewer, DeleteReviewerInput input);
  }

  private final DeleteReviewerSender.Factory deleteReviewerSenderFactory;
  private final NotifyUtil notifyUtil;
  private final Address reviewer;
  private final DeleteReviewerInput input;

  private ChangeMessage changeMessage;
  private Change change;

  @Inject
  DeleteReviewerByEmailOp(
      DeleteReviewerSender.Factory deleteReviewerSenderFactory,
      NotifyUtil notifyUtil,
      @Assisted Address reviewer,
      @Assisted DeleteReviewerInput input) {
    this.deleteReviewerSenderFactory = deleteReviewerSenderFactory;
    this.notifyUtil = notifyUtil;
    this.reviewer = reviewer;
    this.input = input;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws OrmException {
    change = ctx.getChange();
    PatchSet.Id psId = ctx.getChange().currentPatchSetId();
    String msg = "Removed reviewer " + reviewer;
    changeMessage =
        new ChangeMessage(
            new ChangeMessage.Key(change.getId(), ChangeUtil.messageUuid()),
            ctx.getAccountId(),
            ctx.getWhen(),
            psId);
    changeMessage.setMessage(msg);

    ctx.getUpdate(psId).setChangeMessage(msg);
    ctx.getUpdate(psId).removeReviewerByEmail(reviewer);
    return true;
  }

  @Override
  public void postUpdate(Context ctx) {
    if (input.notify == null) {
      if (change.isWorkInProgress()) {
        input.notify = NotifyHandling.NONE;
      } else {
        input.notify = NotifyHandling.ALL;
      }
    }
    if (!NotifyUtil.shouldNotify(input.notify, input.notifyDetails)) {
      return;
    }
    try {
      DeleteReviewerSender cm =
          deleteReviewerSenderFactory.create(ctx.getProject(), change.getId());
      cm.setFrom(ctx.getAccountId());
      cm.addReviewersByEmail(Collections.singleton(reviewer));
      cm.setChangeMessage(changeMessage.getMessage(), changeMessage.getWrittenOn());
      cm.setNotify(input.notify);
      cm.setAccountsToNotify(notifyUtil.resolveAccounts(input.notifyDetails));
      cm.send();
    } catch (Exception err) {
      log.error("Cannot email update for change " + change.getId(), err);
    }
  }
}
