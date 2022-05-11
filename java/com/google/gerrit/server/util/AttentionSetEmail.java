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
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.config.SendEmailExecutor;
import com.google.gerrit.server.mail.send.AddToAttentionSetSender;
import com.google.gerrit.server.mail.send.AttentionSetSender;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.mail.send.MessageIdGenerator.MessageId;
import com.google.gerrit.server.mail.send.RemoveFromAttentionSetSender;
import com.google.gerrit.server.update.Context;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class AttentionSetEmail {
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
     * @param attentionUserId the user added/removed.
     */
    AttentionSetEmail create(
        AttentionSetSender sender,
        Context ctx,
        Change change,
        String reason,
        Account.Id attentionUserId);
  }

  private final ExecutorService sendEmailsExecutor;
  private final AsyncSender asyncSender;

  @Inject
  AttentionSetEmail(
      @SendEmailExecutor ExecutorService executor,
      ThreadLocalRequestContext requestContext,
      MessageIdGenerator messageIdGenerator,
      AccountTemplateUtil accountTemplateUtil,
      @Assisted AttentionSetSender sender,
      @Assisted Context ctx,
      @Assisted Change change,
      @Assisted String reason,
      @Assisted Account.Id attentionUserId) {
    this.sendEmailsExecutor = executor;

    MessageId messageId;
    try {
      messageId =
          messageIdGenerator.fromChangeUpdate(ctx.getRepoView(), change.currentPatchSetId());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    this.asyncSender =
        new AsyncSender(
            requestContext,
            ctx.getIdentifiedUser(),
            sender,
            messageId,
            ctx.getNotify(change.getId()),
            attentionUserId,
            accountTemplateUtil.replaceTemplates(reason),
            change.getId());
  }

  public void sendAsync() {
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError = sendEmailsExecutor.submit(asyncSender);
  }

  /**
   * {@link Runnable} that sends the email asynchonously.
   *
   * <p>Only pass objects into this class that are thread-safe (e.g. immutable) so that they can be
   * safely accessed from the background thread.
   */
  private static class AsyncSender implements Runnable, RequestContext {
    private final ThreadLocalRequestContext requestContext;
    private final IdentifiedUser user;
    private final AttentionSetSender sender;
    private final MessageIdGenerator.MessageId messageId;
    private final NotifyResolver.Result notify;
    private final Account.Id attentionUserId;
    private final String reason;
    private final Change.Id changeId;

    AsyncSender(
        ThreadLocalRequestContext requestContext,
        IdentifiedUser user,
        AttentionSetSender sender,
        MessageIdGenerator.MessageId messageId,
        NotifyResolver.Result notify,
        Account.Id attentionUserId,
        String reason,
        Change.Id changeId) {
      this.requestContext = requestContext;
      this.user = user;
      this.sender = sender;
      this.messageId = messageId;
      this.notify = notify;
      this.attentionUserId = attentionUserId;
      this.reason = reason;
      this.changeId = changeId;
    }

    @Override
    public void run() {
      RequestContext old = requestContext.setContext(this);
      try {
        Optional<Account.Id> accountId =
            user.isIdentifiedUser()
                ? Optional.of(user.asIdentifiedUser().getAccountId())
                : Optional.empty();
        if (accountId.isPresent()) {
          sender.setFrom(accountId.get());
        }
        sender.setNotify(notify);
        sender.setAttentionSetUser(attentionUserId);
        sender.setReason(reason);
        sender.setMessageId(messageId);
        sender.send();
      } catch (Exception e) {
        logger.atSevere().withCause(e).log("Cannot email update for change %s", changeId);
      } finally {
        requestContext.setContext(old);
      }
    }

    @Override
    public String toString() {
      return "send-email attention-set-update";
    }

    @Override
    public CurrentUser getUser() {
      return user;
    }
  }
}
