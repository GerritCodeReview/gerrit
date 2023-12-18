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

import static com.google.gerrit.server.mail.EmailFactories.ATTENTION_SET_ADDED;
import static com.google.gerrit.server.mail.EmailFactories.ATTENTION_SET_REMOVED;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.config.SendEmailExecutor;
import com.google.gerrit.server.mail.EmailFactories;
import com.google.gerrit.server.mail.send.AttentionSetChangeEmailDecorator;
import com.google.gerrit.server.mail.send.AttentionSetChangeEmailDecorator.AttentionSetChange;
import com.google.gerrit.server.mail.send.ChangeEmail;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.mail.send.MessageIdGenerator.MessageId;
import com.google.gerrit.server.mail.send.OutgoingEmail;
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
     * @param attentionSetChange whether the user is added or removed.
     * @param ctx context for sending the email.
     * @param change the change that the user was added/removed in.
     * @param reason reason for adding/removing the user.
     * @param attentionUserId the user added/removed.
     */
    AttentionSetEmail create(
        AttentionSetChange attentionSetChange,
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
      EmailFactories emailFactories,
      @Assisted AttentionSetChange attentionSetChange,
      @Assisted Context ctx,
      @Assisted Change change,
      @Assisted String reason,
      @Assisted Account.Id attentionUserId) {
    this.sendEmailsExecutor = executor;

    MessageId messageId;
    try {
      messageId =
          messageIdGenerator.fromChangeUpdateAndReason(
              ctx.getRepoView(), change.currentPatchSetId(), "AttentionSetEmail");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    this.asyncSender =
        new AsyncSender(
            requestContext,
            emailFactories,
            ctx.getUser(),
            ctx.getProject(),
            attentionSetChange,
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
    private final EmailFactories emailFactories;
    private final CurrentUser user;
    private final AttentionSetChange attentionSetChange;
    private final Project.NameKey projectId;
    private final MessageIdGenerator.MessageId messageId;
    private final NotifyResolver.Result notify;
    private final Account.Id attentionUserId;
    private final String reason;
    private final Change.Id changeId;

    AsyncSender(
        ThreadLocalRequestContext requestContext,
        EmailFactories emailFactories,
        CurrentUser user,
        Project.NameKey projectId,
        AttentionSetChange attentionSetChange,
        MessageIdGenerator.MessageId messageId,
        NotifyResolver.Result notify,
        Account.Id attentionUserId,
        String reason,
        Change.Id changeId) {
      this.requestContext = requestContext;
      this.emailFactories = emailFactories;
      this.user = user;
      this.projectId = projectId;
      this.attentionSetChange = attentionSetChange;
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
        AttentionSetChangeEmailDecorator attentionSetChangeEmail =
            emailFactories.createAttentionSetChangeEmail();
        attentionSetChangeEmail.setAttentionSetChange(attentionSetChange);
        attentionSetChangeEmail.setAttentionSetUser(attentionUserId);
        attentionSetChangeEmail.setReason(reason);
        ChangeEmail changeEmail =
            emailFactories.createChangeEmail(projectId, changeId, attentionSetChangeEmail);
        OutgoingEmail outgoingEmail =
            emailFactories.createOutgoingEmail(
                attentionSetChange.equals(AttentionSetChange.USER_ADDED)
                    ? ATTENTION_SET_ADDED
                    : ATTENTION_SET_REMOVED,
                changeEmail);

        Optional<Account.Id> accountId =
            user.isIdentifiedUser()
                ? Optional.of(user.asIdentifiedUser().getAccountId())
                : Optional.empty();
        if (accountId.isPresent()) {
          outgoingEmail.setFrom(accountId.get());
        }
        outgoingEmail.setNotify(notify);
        outgoingEmail.setMessageId(messageId);
        outgoingEmail.send();
      } catch (Exception e) {
        logger.atSevere().withCause(e).log("Cannot email update for change %s", changeId);
      } finally {
        @SuppressWarnings("unused")
        var unused = requestContext.setContext(old);
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
