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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.mail.Address;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.mail.send.DeleteReviewerSender;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Collections;

public class DeleteReviewerByEmailOp implements BatchUpdateOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    DeleteReviewerByEmailOp create(Address reviewer);
  }

  private final DeleteReviewerSender.Factory deleteReviewerSenderFactory;
  private final Address reviewer;

  private ChangeMessage changeMessage;
  private Change change;

  @Inject
  DeleteReviewerByEmailOp(
      DeleteReviewerSender.Factory deleteReviewerSenderFactory, @Assisted Address reviewer) {
    this.deleteReviewerSenderFactory = deleteReviewerSenderFactory;
    this.reviewer = reviewer;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) {
    change = ctx.getChange();
    PatchSet.Id psId = ctx.getChange().currentPatchSetId();
    String msg = "Removed reviewer " + reviewer;
    changeMessage =
        new ChangeMessage(
            ChangeMessage.key(change.getId(), ChangeUtil.messageUuid()),
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
    try {
      NotifyResolver.Result notify = ctx.getNotify(change.getId());
      if (!notify.shouldNotify()) {
        return;
      }
      DeleteReviewerSender cm =
          deleteReviewerSenderFactory.create(ctx.getProject(), change.getId());
      cm.setFrom(ctx.getAccountId());
      cm.addReviewersByEmail(Collections.singleton(reviewer));
      cm.setChangeMessage(changeMessage.getMessage(), changeMessage.getWrittenOn());
      cm.setNotify(notify);
      cm.send();
    } catch (Exception err) {
      logger.atSevere().withCause(err).log("Cannot email update for change %s", change.getId());
    }
  }
}
