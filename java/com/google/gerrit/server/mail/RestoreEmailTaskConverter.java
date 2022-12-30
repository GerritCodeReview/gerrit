package com.google.gerrit.server.mail;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.converter.ChangeIdProtoConverter;
import com.google.gerrit.entities.converter.ProjectNameKeyProtoConverter;
import com.google.gerrit.proto.Entities.EmailTask;
import com.google.gerrit.proto.Entities.EmailTask.Header.HeaderName;
import com.google.gerrit.server.mail.send.MessageIdGenerator.MessageId;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import com.google.gerrit.server.mail.send.ReplyToChangeSender;
import com.google.gerrit.server.mail.send.RestoredSender;
import java.time.Instant;
import java.util.Optional;

public class RestoreEmailTaskConverter extends EmailTaskConverter {
  private final ReplyToChangeSender.Factory emailSenderFactory;

  public RestoreEmailTaskConverter(RestoredSender.Factory emailSenderFactory) {
    this.emailSenderFactory = emailSenderFactory;
  }

  @Override
  public OutgoingEmail convert(EmailTask emailTask) {
    ReplyToChangeSender emailSender =
        emailSenderFactory.create(
            ProjectNameKeyProtoConverter.INSTANCE.fromProto(emailTask.getProject()),
            ChangeIdProtoConverter.INSTANCE.fromProto(emailTask.getChangeId()));
    emailSender.setFrom(
        Account.id(Integer.parseInt(getHeader(emailTask, HeaderName.FROM_ID).get())));
    emailSender.setChangeMessage(
        emailTask.getMessage(),
        Instant.ofEpochMilli(Long.parseLong(getHeader(emailTask, HeaderName.TIMESTAMP).get())));
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
}
