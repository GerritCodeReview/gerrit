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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.proto.Entities.EmailTask.Header.HeaderName.MESSAGE_ID;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.converter.AccountIdProtoConverter;
import com.google.gerrit.entities.converter.ChangeIdProtoConverter;
import com.google.gerrit.entities.converter.ProjectNameKeyProtoConverter;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.proto.Entities.EmailTask;
import com.google.gerrit.proto.Entities.EmailTask.Header.HeaderName;
import com.google.gerrit.proto.Entities.EmailTask.NotifyInput;
import com.google.gerrit.proto.Entities.EmailTask.NotifyInput.NotifyEntry;
import com.google.gerrit.proto.Entities.EmailTask.NotifyInput.NotifyHandling;
import com.google.gerrit.proto.Entities.EmailTask.Payload;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.mail.EmailTaskDispatcher;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.mail.send.MessageIdGenerator.MessageId;
import com.google.gerrit.server.update.Context;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

public class AttentionSetEmail {
  public interface Factory {
    AttentionSetEmail create(
        EmailTask.Type emailTaskType,
        Context ctx,
        Change change,
        String reason,
        Account.Id attentionUserId);
  }

  private final EmailTaskDispatcher emailTaskDispatcher;
  private final EmailTask.Type emailTaskType;
  private final AccountTemplateUtil accountTemplateUtil;
  private final Context ctx;
  private final Change change;
  private final String messageId;

  private final String attentionReason;
  private final Account.Id attentionUserId;

  @Inject
  AttentionSetEmail(
      MessageIdGenerator messageIdGenerator,
      EmailTaskDispatcher emailTaskDispatcher,
      AccountTemplateUtil accountTemplateUtil,
      @Assisted EmailTask.Type emailTaskType,
      @Assisted Context ctx,
      @Assisted Change change,
      @Assisted String reason,
      @Assisted Account.Id attentionUserId) {
    checkArgument(
        emailTaskType.equals(EmailTask.Type.ADD_TO_ATTENTION_SET)
            || emailTaskType.equals(EmailTask.Type.REMOVE_FROM_ATTENTION_SET));

    this.accountTemplateUtil = accountTemplateUtil;
    this.emailTaskDispatcher = emailTaskDispatcher;
    MessageId messageId;
    try {
      messageId =
          messageIdGenerator.fromChangeUpdateAndReason(
              ctx.getRepoView(), change.currentPatchSetId(), "AttentionSetEmail");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    this.emailTaskType = emailTaskType;
    this.ctx = ctx;
    this.change = change;
    this.messageId = messageId.id();
    this.attentionReason = reason;
    this.attentionUserId = attentionUserId;
  }

  public void dispatch() {
    EmailTask.Builder emailTaskBuilder =
        EmailTask.newBuilder()
            .setEventType(emailTaskType)
            .setProject(ProjectNameKeyProtoConverter.INSTANCE.toProto(ctx.getProject()))
            .setChangeId(ChangeIdProtoConverter.INSTANCE.toProto(change.getId()))
            .setPayload(
                Payload.newBuilder()
                    .setAttentionSetReason(accountTemplateUtil.replaceTemplates(attentionReason))
                    .setAttentionSetUser(AccountIdProtoConverter.INSTANCE.toProto(attentionUserId))
                    .build())
            .setNotifyInput(getNotify(ctx.getNotify(change.getId())))
            .addHeader(header(MESSAGE_ID, messageId));
    IdentifiedUser user = ctx.getIdentifiedUser();
    Optional<Account.Id> accountId =
        user.isIdentifiedUser()
            ? Optional.of(user.asIdentifiedUser().getAccountId())
            : Optional.empty();
    if (accountId.isPresent()) {
      emailTaskBuilder.addHeader(header(HeaderName.FROM_ID, accountId.get().toString()));
    }
    emailTaskDispatcher.dispatch(emailTaskBuilder.build());
  }

  private NotifyInput getNotify(NotifyResolver.Result notify) {
    NotifyInput.Builder builder =
        NotifyInput.newBuilder()
            .setNotifyHandling(NotifyHandling.valueOf(notify.handling().name()));
    for (RecipientType recipientType : notify.accounts().keySet()) {
      notify.accounts().get(recipientType).stream()
          .forEach(
              a ->
                  builder.addNotifyEntry(
                      NotifyEntry.newBuilder()
                          .setAccount(AccountIdProtoConverter.INSTANCE.toProto(a))
                          .setRecipientType(EmailTask.RecipientType.valueOf(recipientType.name()))
                          .build()));
    }
    return builder.build();
  }

  private EmailTask.Header header(EmailTask.Header.HeaderName headerName, String value) {
    return EmailTask.Header.newBuilder().setName(headerName).setValue(value).build();
  }
}
