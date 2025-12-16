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

import static com.google.gerrit.server.mail.EmailFactories.REVIEWER_DELETED;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.mail.EmailFactories;
import com.google.gerrit.server.mail.send.ChangeEmail;
import com.google.gerrit.server.mail.send.DeleteReviewerChangeEmailDecorator;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.RemoveReviewerControl;
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

  private final EmailFactories emailFactories;
  private final MessageIdGenerator messageIdGenerator;
  private final ChangeMessagesUtil changeMessagesUtil;
  private final RemoveReviewerControl removeReviewerControl;

  private final Address reviewer;
  private String mailMessage;
  private Change change;

  @Inject
  DeleteReviewerByEmailOp(
      EmailFactories emailFactories,
      MessageIdGenerator messageIdGenerator,
      ChangeMessagesUtil changeMessagesUtil,
      RemoveReviewerControl removeReviewerControl,
      @Assisted Address reviewer) {
    this.emailFactories = emailFactories;
    this.messageIdGenerator = messageIdGenerator;
    this.changeMessagesUtil = changeMessagesUtil;
    this.removeReviewerControl = removeReviewerControl;
    this.reviewer = reviewer;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws PermissionBackendException, AuthException {
    removeReviewerControl.checkRemoveReviewer(ctx.getNotes(), ctx.getUser(), null);
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
        DeleteReviewerChangeEmailDecorator deleteReviewerEmail =
            emailFactories.createDeleteReviewerChangeEmail();
        deleteReviewerEmail.addReviewersByEmail(Collections.singleton(reviewer));
        ChangeEmail changeEmail =
            emailFactories.createChangeEmail(ctx.getProject(), change.getId(), deleteReviewerEmail);
        changeEmail.setChangeMessage(mailMessage, ctx.getWhen());
        OutgoingEmail outgoingEmail =
            emailFactories.createOutgoingEmail(REVIEWER_DELETED, changeEmail);
        outgoingEmail.setFrom(ctx.getAccountId());
        outgoingEmail.setNotify(notify);
        outgoingEmail.setMessageId(
            messageIdGenerator.fromChangeUpdate(ctx.getRepoView(), change.currentPatchSetId()));

        try (TraceTimer timer =
            TraceContext.newTimer(
                "DeleteReviewerByEmailSynchronousEmailNotification",
                Metadata.builder()
                    .projectName(change.getProject().get())
                    .changeId(change.getId().get())
                    .build())) {
          outgoingEmail.send();
        }
      } catch (Exception err) {
        logger.atSevere().withCause(err).log("Cannot email update for change %s", change.getId());
      }
    }
  }
}
