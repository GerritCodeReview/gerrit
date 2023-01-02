package com.google.gerrit.server.mail;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.converter.ChangeIdProtoConverter;
import com.google.gerrit.entities.converter.ProjectNameKeyProtoConverter;
import com.google.gerrit.proto.Entities.EmailTask;
import com.google.gerrit.proto.Entities.EmailTask.Header.HeaderName;
import com.google.gerrit.server.mail.send.MessageIdGenerator.MessageId;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import com.google.gerrit.server.mail.send.RevertedSender;

public class RevertedEmailTaskConverter extends EmailTaskConverter {

  private final RevertedSender.Factory revertedSenderFactory;

  public RevertedEmailTaskConverter(RevertedSender.Factory revertedSenderFactory) {
    this.revertedSenderFactory = revertedSenderFactory;
  }

  @Override
  public OutgoingEmail convert(EmailTask emailTask) {
    RevertedSender emailSender =
        revertedSenderFactory.create(
            ProjectNameKeyProtoConverter.INSTANCE.fromProto(emailTask.getProject()),
            ChangeIdProtoConverter.INSTANCE.fromProto(emailTask.getChangeId()));
    emailSender.setFrom(
        Account.id(Integer.parseInt(getHeader(emailTask, HeaderName.FROM_ID).get())));
    emailSender.setNotify(getNotify(emailTask.getNotifyInput()));
    emailSender.setMessageId(MessageId.create(getHeader(emailTask, HeaderName.MESSAGE_ID).get()));
    return emailSender;
  }
}
