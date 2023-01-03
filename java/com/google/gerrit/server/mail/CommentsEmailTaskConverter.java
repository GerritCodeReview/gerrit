package com.google.gerrit.server.mail;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.entities.converter.ChangeIdProtoConverter;
import com.google.gerrit.entities.converter.LabelVoteProtoConverter;
import com.google.gerrit.entities.converter.ObjectIdProtoConverter;
import com.google.gerrit.entities.converter.PatchSetIdProtoConverter;
import com.google.gerrit.entities.converter.ProjectNameKeyProtoConverter;
import com.google.gerrit.entities.converter.SubmitRequirementResultProtoConverter;
import com.google.gerrit.proto.Entities.EmailTask;
import com.google.gerrit.proto.Entities.EmailTask.Header.HeaderName;
import com.google.gerrit.server.mail.send.CommentSender;
import com.google.gerrit.server.mail.send.MessageIdGenerator.MessageId;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import java.time.Instant;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CommentsEmailTaskConverter extends EmailTaskConverter {
  private final CommentSender.Factory commentSenderFactory;

  public CommentsEmailTaskConverter(CommentSender.Factory commentSenderFactory) {
    this.commentSenderFactory = commentSenderFactory;
  }

  @Override
  public OutgoingEmail convert(EmailTask emailTask) {
    CommentSender emailSender =
        commentSenderFactory.create(
            ProjectNameKeyProtoConverter.INSTANCE.fromProto(emailTask.getProject()),
            ChangeIdProtoConverter.INSTANCE.fromProto(emailTask.getChangeId()),
            ObjectIdProtoConverter.INSTANCE.fromProto(emailTask.getPreUpdateMetaId()),
            emailTask.getPayload().getPostUpdateSubmitRequirementResultsList().stream()
                .map(sr -> SubmitRequirementResultProtoConverter.INSTANCE.fromProto(sr))
                .collect(
                    Collectors.toMap(
                        SubmitRequirementResult::submitRequirement, Function.identity())));
    emailSender.setFrom(
        Account.id(Integer.parseInt(getHeader(emailTask, HeaderName.FROM_ID).get())));
    emailSender.setPatchSetId(
        PatchSetIdProtoConverter.INSTANCE.fromProto(emailTask.getPatchsetId()));
    emailSender.setChangeMessage(
        emailTask.getMessage(),
        Instant.ofEpochMilli(Long.parseLong(getHeader(emailTask, HeaderName.TIMESTAMP).get())));
    emailSender.setComments(
        emailTask.getPayload().getCommentUuidsList().stream().collect(Collectors.toSet()));
    emailSender.setPatchSetComment(emailTask.getPayload().getPatchsetComment());
    emailSender.setLabels(
        emailTask.getPayload().getLabelVotesList().stream()
            .map(lv -> LabelVoteProtoConverter.INSTANCE.fromProto(lv))
            .collect(ImmutableList.toImmutableList()));
    emailSender.setNotify(getNotify(emailTask.getNotifyInput()));
    emailSender.setMessageId(MessageId.create(getHeader(emailTask, HeaderName.MESSAGE_ID).get()));
    return emailSender;
  }
}
