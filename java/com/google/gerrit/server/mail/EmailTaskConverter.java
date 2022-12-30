package com.google.gerrit.server.mail;

import com.google.gerrit.proto.Entities.EmailTask;
import com.google.gerrit.server.mail.send.AbandonedSender;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import com.google.gerrit.server.mail.send.RestoredSender;
import com.google.inject.Inject;

/** Converts a {@link com.google.gerrit.proto.Entities.EmailTask} to {@link OutgoingEmail}. */
public abstract class EmailTaskConverter {
  public abstract OutgoingEmail convert(EmailTask emailTask);

  public static EmailTaskConverter getInstance(EmailTask.Type eventType, Args args) {
    switch (eventType) {
      case ABANDON:
        return new AbandonEmailTaskConverter(args.abandonedSender);
      case RESTORE:
        return new RestoreEmailTaskConverter(args.restoredSender);
      default:
        throw new RuntimeException("Unrecognized");
    }
  }

  public static class Args {
    private final AbandonedSender.Factory abandonedSender;
    private final RestoredSender.Factory restoredSender;

    @Inject
    Args(AbandonedSender.Factory emailSenderFactory, RestoredSender.Factory restoredSender) {
      this.abandonedSender = emailSenderFactory;
      this.restoredSender = restoredSender;
    }
  }
}
