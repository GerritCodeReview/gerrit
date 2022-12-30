package com.google.gerrit.server.mail;

import com.google.gerrit.proto.Entities.EmailTask;
import com.google.gerrit.server.mail.send.AbandonedSender;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import com.google.inject.Inject;

/** Converts a {@link com.google.gerrit.proto.Entities.EmailTask} to {@link OutgoingEmail}. */
public abstract class EmailTaskConverter {
  public abstract OutgoingEmail convert(EmailTask emailTask);

  public static EmailTaskConverter getInstance(EmailTask.Type eventType, Args args) {
    switch (eventType) {
      case ABANDON:
        return new AbandonEmailTaskConverter(args.abandonedSender);
      default:
        throw new RuntimeException("Unrecognized");
    }
  }

  public static class Args {
    private final AbandonedSender.Factory abandonedSender;

    @Inject
    Args(AbandonedSender.Factory emailSenderFactory) {
      this.abandonedSender = emailSenderFactory;
    }
  }
}
