// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.util;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.SendEmailExecutor;
import com.google.gerrit.server.mail.send.AddToAttentionSetSender;
import com.google.gerrit.server.mail.send.AttentionSetSender;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.mail.send.RemoveFromAttentionSetSender;
import com.google.gerrit.server.update.Context;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class AttentionSetEmail implements Runnable, RequestContext {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {

    /**
     * factory for sending an email when adding users to the attention set or removing them from it.
     *
     * @param sender sender in charge of sending the email, can be {@link AddToAttentionSetSender}
     *     or {@link RemoveFromAttentionSetSender}.
     * @param ctx context for sending the email.
     * @param change the change that the user was added/removed in.
     * @param reason reason for adding/removing the user.
     * @param messageId messageId for tracking the email.
     * @param attentionUserId the user added/removed.
     */
    AttentionSetEmail create(
        AttentionSetSender sender,
        Context ctx,
        Change change,
        String reason,
        MessageIdGenerator.MessageId messageId,
        Account.Id attentionUserId);
  }

  private ExecutorService sendEmailsExecutor;
  private AttentionSetSender sender;
  private Context ctx;
  private Change change;
  private String reason;

  private MessageIdGenerator.MessageId messageId;
  private Account.Id attentionUserId;

  @Inject
  AttentionSetEmail(
      @SendEmailExecutor ExecutorService executor,
      @Assisted AttentionSetSender sender,
      @Assisted Context ctx,
      @Assisted Change change,
      @Assisted String reason,
      @Assisted MessageIdGenerator.MessageId messageId,
      @Assisted Account.Id attentionUserId) {
    this.sendEmailsExecutor = executor;
    this.sender = sender;
    this.ctx = ctx;
    this.change = change;
    this.reason = reason;
    this.messageId = messageId;
    this.attentionUserId = attentionUserId;
  }

  public void sendAsync() {
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError = sendEmailsExecutor.submit(this);
  }

  @Override
  public void run() {
    try {
      AccountState accountState =
          ctx.getUser().isIdentifiedUser() ? ctx.getUser().asIdentifiedUser().state() : null;
      if (accountState != null) {
        sender.setFrom(accountState.account().id());
      }
      sender.setNotify(ctx.getNotify(change.getId()));
      sender.setAttentionSetUser(attentionUserId);
      sender.setReason(reason);
      sender.setMessageId(messageId);
      sender.send();
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Cannot email update for change %s", change.getId());
    }
  }

  @Override
  public String toString() {
    return "send-email comments";
  }

  @Override
  public CurrentUser getUser() {
    return ctx.getUser();
  }
}
