package com.google.gerrit.server.mail;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.converter.ChangeIdProtoConverter;
import com.google.gerrit.entities.converter.ProjectNameKeyProtoConverter;
import com.google.gerrit.proto.Entities.EmailTask;
import com.google.gerrit.proto.Entities.EmailTask.Header.HeaderName;
import com.google.gerrit.server.mail.send.DeleteVoteSender;
import com.google.gerrit.server.mail.send.MessageIdGenerator.MessageId;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import com.google.gerrit.server.mail.send.ReplyToChangeSender;
import java.time.Instant;

public class DeleteVoteEmailTaskConverter extends EmailTaskConverter {
  private final DeleteVoteSender.Factory deleteVoteSender;

  public DeleteVoteEmailTaskConverter(DeleteVoteSender.Factory deleteVoteSender) {
    this.deleteVoteSender = deleteVoteSender;
  }

  @Override
  public OutgoingEmail convert(EmailTask emailTask) {
    ReplyToChangeSender emailSender =
        deleteVoteSender.create(
            ProjectNameKeyProtoConverter.INSTANCE.fromProto(emailTask.getProject()),
            ChangeIdProtoConverter.INSTANCE.fromProto(emailTask.getChangeId()));
    if (getHeader(emailTask, HeaderName.FROM_ID).isPresent()) {
      emailSender.setFrom(
          Account.id(Integer.parseInt(getHeader(emailTask, HeaderName.FROM_ID).get())));
    }
    emailSender.setChangeMessage(
        emailTask.getMessage(),
        Instant.ofEpochMilli(Long.parseLong(getHeader(emailTask, HeaderName.FROM_ID).get())));
    emailSender.setNotify(getNotify(emailTask.getNotifyInput()));
    emailSender.setMessageId(MessageId.create(getHeader(emailTask, HeaderName.MESSAGE_ID).get()));
    return emailSender;
  }
}
