package com.google.gerrit.server.mail;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.converter.AccountIdProtoConverter;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.proto.Entities.EmailTask;
import com.google.gerrit.proto.Entities.EmailTask.Header.HeaderName;
import com.google.gerrit.proto.Entities.EmailTask.NotifyInput;
import com.google.gerrit.proto.Entities.EmailTask.NotifyInput.NotifyEntry;
import com.google.gerrit.proto.Entities.EmailTask.NotifyInput.NotifyHandling;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.mail.send.AbandonedSender;
import com.google.gerrit.server.mail.send.AddToAttentionSetSender;
import com.google.gerrit.server.mail.send.MergedSender;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import com.google.gerrit.server.mail.send.RemoveFromAttentionSetSender;
import com.google.gerrit.server.mail.send.RestoredSender;
import com.google.gerrit.server.mail.send.RevertedSender;
import com.google.inject.Inject;
import java.util.Optional;

/** Converts a {@link com.google.gerrit.proto.Entities.EmailTask} to {@link OutgoingEmail}. */
public abstract class EmailTaskConverter {
  public abstract OutgoingEmail convert(EmailTask emailTask);

  public static EmailTaskConverter getInstance(EmailTask.Type eventType, Args args) {
    switch (eventType) {
      case ABANDON:
        return new AbandonEmailTaskConverter(args.abandonedSender);
      case RESTORE:
        return new RestoreEmailTaskConverter(args.restoredSender);
      case MERGED:
        return new MergeEmailTaskConverter(args.mergedSender);
      case REVERTED:
        return new RevertedEmailTaskConverter(args.revertedSender);
      case ADD_TO_ATTENTION_SET:
      case REMOVE_FROM_ATTENTION_SET:
        return new AttentionSetEmailTaskConverter(
            eventType, args.addToAttentionSetSender, args.removeFromAttentionSetSender);
      default:
        throw new RuntimeException("Unrecognized");
    }
  }

  protected Optional<String> getHeader(EmailTask task, HeaderName headerName) {
    for (EmailTask.Header header : task.getHeaderList()) {
      if (header.getName().equals(headerName)) {
        return Optional.of(header.getValue());
      }
    }
    return Optional.empty();
  }

  protected NotifyResolver.Result getNotify(NotifyInput notifyInput) {
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

  public static class Args {
    private final AbandonedSender.Factory abandonedSender;
    private final RestoredSender.Factory restoredSender;
    private final MergedSender.Factory mergedSender;
    private final RevertedSender.Factory revertedSender;
    private final AddToAttentionSetSender.Factory addToAttentionSetSender;
    private final RemoveFromAttentionSetSender.Factory removeFromAttentionSetSender;

    @Inject
    Args(
        AbandonedSender.Factory emailSenderFactory,
        RestoredSender.Factory restoredSender,
        MergedSender.Factory mergedSender,
        RevertedSender.Factory revertedSender,
        AddToAttentionSetSender.Factory addToAttentionSetSender,
        RemoveFromAttentionSetSender.Factory removeFromAttentionSetSender) {
      this.abandonedSender = emailSenderFactory;
      this.restoredSender = restoredSender;
      this.mergedSender = mergedSender;
      this.revertedSender = revertedSender;
      this.addToAttentionSetSender = addToAttentionSetSender;
      this.removeFromAttentionSetSender = removeFromAttentionSetSender;
    }
  }
}
