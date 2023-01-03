package com.google.gerrit.server.mail;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.entities.converter.AccountIdProtoConverter;
import com.google.gerrit.entities.converter.ChangeIdProtoConverter;
import com.google.gerrit.entities.converter.ObjectIdProtoConverter;
import com.google.gerrit.entities.converter.PatchSetApprovalProtoConverter;
import com.google.gerrit.entities.converter.PatchSetIdProtoConverter;
import com.google.gerrit.entities.converter.ProjectNameKeyProtoConverter;
import com.google.gerrit.entities.converter.SubmitRequirementResultProtoConverter;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.proto.Entities.EmailTask;
import com.google.gerrit.proto.Entities.EmailTask.Header.HeaderName;
import com.google.gerrit.server.mail.send.MessageIdGenerator.MessageId;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import com.google.gerrit.server.mail.send.ReplacePatchSetSender;
import java.time.Instant;
import java.util.function.Function;
import java.util.stream.Collectors;

public class NewPatchsetEmailTaskConverter extends EmailTaskConverter {
  private final ReplacePatchSetSender.Factory replacePatchSetFactory;

  public NewPatchsetEmailTaskConverter(ReplacePatchSetSender.Factory replacePatchSetFactory) {
    this.replacePatchSetFactory = replacePatchSetFactory;
  }

  @Override
  public OutgoingEmail convert(EmailTask emailTask) {
    ReplacePatchSetSender sender =
        replacePatchSetFactory.create(
            ProjectNameKeyProtoConverter.INSTANCE.fromProto(emailTask.getProject()),
            ChangeIdProtoConverter.INSTANCE.fromProto(emailTask.getChangeId()),
            ChangeKind.valueOf(emailTask.getPayload().getChangeKind().name()),
            ObjectIdProtoConverter.INSTANCE.fromProto(emailTask.getPreUpdateMetaId()),
            emailTask.getPayload().getPostUpdateSubmitRequirementResultsList().stream()
                .map(sr -> SubmitRequirementResultProtoConverter.INSTANCE.fromProto(sr))
                .collect(
                    Collectors.toMap(
                        SubmitRequirementResult::submitRequirement, Function.identity())));

    sender.setFrom(Account.id(Integer.parseInt(getHeader(emailTask, HeaderName.FROM_ID).get())));
    sender.setPatchSetId(PatchSetIdProtoConverter.INSTANCE.fromProto(emailTask.getPatchsetId()));
    sender.setChangeMessage(
        emailTask.getMessage(),
        Instant.ofEpochMilli(Long.parseLong(getHeader(emailTask, HeaderName.TIMESTAMP).get())));
    sender.setNotify(getNotify(emailTask.getNotifyInput()));
    sender.addReviewers(
        emailTask.getExtraReviewersList().stream()
            .map(a -> AccountIdProtoConverter.INSTANCE.fromProto(a))
            .collect(Collectors.toSet()));
    sender.addExtraCC(
        emailTask.getExtraCcList().stream()
            .map(a -> AccountIdProtoConverter.INSTANCE.fromProto(a))
            .collect(Collectors.toSet()));
    sender.addOutdatedApproval(
        emailTask.getPayload().getOutdatedApprovalsList().stream()
            .map(a -> PatchSetApprovalProtoConverter.INSTANCE.fromProto(a))
            .collect(Collectors.toUnmodifiableSet()));
    sender.setMessageId(MessageId.create(getHeader(emailTask, HeaderName.MESSAGE_ID).get()));
    return sender;
  }
}
