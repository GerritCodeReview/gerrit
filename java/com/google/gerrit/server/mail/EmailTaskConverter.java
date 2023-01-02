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
import com.google.gerrit.server.mail.send.MergedSender;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import com.google.gerrit.server.mail.send.RestoredSender;
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

    @Inject
    Args(
        AbandonedSender.Factory emailSenderFactory,
        RestoredSender.Factory restoredSender,
        MergedSender.Factory mergedSender) {
      this.abandonedSender = emailSenderFactory;
      this.restoredSender = restoredSender;
      this.mergedSender = mergedSender;
    }
  }
}
