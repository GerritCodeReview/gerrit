package com.google.gerrit.server.mail;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.converter.ChangeIdProtoConverter;
import com.google.gerrit.entities.converter.PatchSetIdProtoConverter;
import com.google.gerrit.entities.converter.ProjectNameKeyProtoConverter;
import com.google.gerrit.proto.Entities.EmailTask;
import com.google.gerrit.proto.Entities.EmailTask.Header.HeaderName;
import com.google.gerrit.server.mail.send.MergedSender;
import com.google.gerrit.server.mail.send.MessageIdGenerator.MessageId;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import java.util.Optional;

public class MergeEmailTaskConverter extends EmailTaskConverter {
  private final MergedSender.Factory emailSenderFactory;

  public MergeEmailTaskConverter(MergedSender.Factory emailSenderFactory) {
    this.emailSenderFactory = emailSenderFactory;
  }

  @Override
  public OutgoingEmail convert(EmailTask emailTask) {
    String stickyApprovalDiff =
        (emailTask.hasPayload() && emailTask.getPayload().hasStickyApprovalDiff())
            ? emailTask.getPayload().getStickyApprovalDiff()
            : null;
    MergedSender emailSender =
        emailSenderFactory.create(
            ProjectNameKeyProtoConverter.INSTANCE.fromProto(emailTask.getProject()),
            ChangeIdProtoConverter.INSTANCE.fromProto(emailTask.getChangeId()),
            Optional.ofNullable(stickyApprovalDiff));
    if (getHeader(emailTask, HeaderName.FROM_ID).isPresent()) {
      emailSender.setFrom(
          Account.id(Integer.parseInt(getHeader(emailTask, HeaderName.FROM_ID).get())));
    }
    if (emailTask.hasPatchsetId()) {
      emailSender.setPatchSetId(
          PatchSetIdProtoConverter.INSTANCE.fromProto(emailTask.getPatchsetId()));
    }
    if (emailTask.hasNotifyInput()) {
      emailSender.setNotify(getNotify(emailTask.getNotifyInput()));
    }
    emailSender.setMessageId(MessageId.create(getHeader(emailTask, HeaderName.MESSAGE_ID).get()));
    return emailSender;
  }
}
