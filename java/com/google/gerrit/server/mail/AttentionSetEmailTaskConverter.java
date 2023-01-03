package com.google.gerrit.server.mail;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.converter.AccountIdProtoConverter;
import com.google.gerrit.entities.converter.ChangeIdProtoConverter;
import com.google.gerrit.entities.converter.ProjectNameKeyProtoConverter;
import com.google.gerrit.proto.Entities.EmailTask;
import com.google.gerrit.proto.Entities.EmailTask.Header.HeaderName;
import com.google.gerrit.server.mail.send.AddToAttentionSetSender;
import com.google.gerrit.server.mail.send.AttentionSetSender;
import com.google.gerrit.server.mail.send.MessageIdGenerator.MessageId;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import com.google.gerrit.server.mail.send.RemoveFromAttentionSetSender;
import java.util.Optional;

/** Add or remove user from attention set. */
public class AttentionSetEmailTaskConverter extends EmailTaskConverter {
  private final AddToAttentionSetSender.Factory addToAttentionSetFactory;
  private final RemoveFromAttentionSetSender.Factory removeFromAttentionSetFactory;
  private final EmailTask.Type emailTaskType;

  public AttentionSetEmailTaskConverter(
      EmailTask.Type emailTaskType,
      AddToAttentionSetSender.Factory addToAttentionSetFactory,
      RemoveFromAttentionSetSender.Factory removeFromAttentionSetFactory) {
    this.addToAttentionSetFactory = addToAttentionSetFactory;
    this.removeFromAttentionSetFactory = removeFromAttentionSetFactory;
    this.emailTaskType = emailTaskType;
  }

  @Override
  public OutgoingEmail convert(EmailTask emailTask) {
    AttentionSetSender sender =
        emailTaskType.equals(EmailTask.Type.ADD_TO_ATTENTION_SET)
            ? addToAttentionSetFactory.create(
                ProjectNameKeyProtoConverter.INSTANCE.fromProto(emailTask.getProject()),
                ChangeIdProtoConverter.INSTANCE.fromProto(emailTask.getChangeId()))
            : removeFromAttentionSetFactory.create(
                ProjectNameKeyProtoConverter.INSTANCE.fromProto(emailTask.getProject()),
                ChangeIdProtoConverter.INSTANCE.fromProto(emailTask.getChangeId()));
    Optional<String> fromId = getHeader(emailTask, HeaderName.FROM_ID);
    if (fromId.isPresent()) {
      sender.setFrom(Account.id(Integer.parseInt(fromId.get())));
    }
    sender.setNotify(getNotify(emailTask.getNotifyInput()));
    sender.setAttentionSetUser(
        AccountIdProtoConverter.INSTANCE.fromProto(emailTask.getPayload().getAttentionSetUser()));
    sender.setReason(emailTask.getPayload().getAttentionSetReason());
    sender.setMessageId(MessageId.create(getHeader(emailTask, HeaderName.MESSAGE_ID).get()));
    return sender;
  }
}
