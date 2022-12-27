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
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.mail.send.DeleteReviewerSender;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Collections;

public class DeleteReviewerByEmailOp extends ReviewerOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    DeleteReviewerByEmailOp create(Address reviewer);
  }

  private final DeleteReviewerSender.Factory deleteReviewerSenderFactory;
  private final MessageIdGenerator messageIdGenerator;
  private final ChangeMessagesUtil changeMessagesUtil;

  private final Address reviewer;
  private String mailMessage;
  private Change change;

  @Inject
  DeleteReviewerByEmailOp(
      DeleteReviewerSender.Factory deleteReviewerSenderFactory,
      MessageIdGenerator messageIdGenerator,
      ChangeMessagesUtil changeMessagesUtil,
      @Assisted Address reviewer) {
    this.deleteReviewerSenderFactory = deleteReviewerSenderFactory;
    this.messageIdGenerator = messageIdGenerator;
    this.changeMessagesUtil = changeMessagesUtil;
    this.reviewer = reviewer;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) {
    change = ctx.getChange();
    PatchSet.Id psId = ctx.getChange().currentPatchSetId();
    ChangeUpdate update = ctx.getUpdate(psId);
    update.removeReviewerByEmail(reviewer);
    // The reviewer is not a registered Gerrit user, thus the email address can be used in
    // ChangeMessage without replacement (it does not classify as Gerrit user identifiable
    // information).
    String msg = "Removed reviewer " + reviewer;
    mailMessage =
        changeMessagesUtil.setChangeMessage(ctx, msg, ChangeMessagesUtil.TAG_DELETE_REVIEWER);
    return true;
  }

  @Override
  public void postUpdate(PostUpdateContext ctx) {
    opResult = Result.builder().setDeletedReviewerByEmail(reviewer).build();
    if (sendEmail) {
      try {
        NotifyResolver.Result notify = ctx.getNotify(change.getId());
        DeleteReviewerSender emailSender =
            deleteReviewerSenderFactory.create(ctx.getProject(), change.getId());
        emailSender.setFrom(ctx.getAccountId());
        emailSender.addReviewersByEmail(Collections.singleton(reviewer));
        emailSender.setChangeMessage(mailMessage, ctx.getWhen());
        emailSender.setNotify(notify);
        emailSender.setMessageId(
            messageIdGenerator.fromChangeUpdate(ctx.getRepoView(), change.currentPatchSetId()));
        emailSender.send();
      } catch (Exception err) {
        logger.atSevere().withCause(err).log("Cannot email update for change %s", change.getId());
      }
    }
  }
}
