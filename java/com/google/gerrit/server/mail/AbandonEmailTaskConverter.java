package com.google.gerrit.server.mail;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.converter.AccountIdProtoConverter;
import com.google.gerrit.entities.converter.ChangeIdProtoConverter;
import com.google.gerrit.entities.converter.ProjectNameKeyProtoConverter;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.proto.Entities.EmailTask;
import com.google.gerrit.proto.Entities.EmailTask.Header.HeaderName;
import com.google.gerrit.proto.Entities.EmailTask.NotifyInput;
import com.google.gerrit.proto.Entities.EmailTask.NotifyInput.NotifyEntry;
import com.google.gerrit.proto.Entities.EmailTask.NotifyInput.NotifyHandling;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.mail.send.AbandonedSender;
import com.google.gerrit.server.mail.send.MessageIdGenerator.MessageId;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import com.google.gerrit.server.mail.send.ReplyToChangeSender;
import java.time.Instant;
import java.util.Optional;

public class AbandonEmailTaskConverter extends EmailTaskConverter {
  private final ReplyToChangeSender.Factory emailSenderFactory;

  public AbandonEmailTaskConverter(AbandonedSender.Factory emailSenderFactory) {
    this.emailSenderFactory = emailSenderFactory;
  }

  @Override
  public OutgoingEmail convert(EmailTask emailTask) {
    ReplyToChangeSender emailSender =
        emailSenderFactory.create(
            ProjectNameKeyProtoConverter.INSTANCE.fromProto(emailTask.getProject()),
            ChangeIdProtoConverter.INSTANCE.fromProto(emailTask.getChangeId()));
    if (getHeader(emailTask, HeaderName.FROM_ID).isPresent()) {
      emailSender.setFrom(
          Account.id(Integer.parseInt(getHeader(emailTask, HeaderName.FROM_ID).get())));
    }
    emailSender.setChangeMessage(
        emailTask.getMessage(),
        Instant.ofEpochMilli(Long.parseLong(getHeader(emailTask, HeaderName.TIMESTAMP).get())));
    emailSender.setNotify(getNotify(emailTask.getNotifyInput()));
    emailSender.setMessageId(MessageId.create(getHeader(emailTask, HeaderName.MESSAGE_ID).get()));
    return emailSender;
  }

  Optional<String> getHeader(EmailTask task, HeaderName headerName) {
    for (EmailTask.Header header : task.getHeaderList()) {
      if (header.getName().equals(headerName)) {
        return Optional.of(header.getValue());
      }
    }
    return Optional.empty();
  }

  NotifyResolver.Result getNotify(NotifyInput notifyInput) {
    NotifyHandling handling = notifyInput.getNotifyHandling();
    ImmutableSetMultimap.Builder<RecipientType, Account.Id> recipients =
        ImmutableSetMultimap.builder();
    for (NotifyEntry notifyEntry : notifyInput.getNotifyEntryList()) {
      recipients.put(
          RecipientType.valueOf(notifyEntry.getRecipientType().name()),
          AccountIdProtoConverter.INSTANCE.fromProto(notifyEntry.getAccount()));
    }
    return NotifyResolver.Result.create(
        com.google.gerrit.extensions.api.changes.NotifyHandling.valueOf(handling.name()),
        recipients.build());
  }
}
