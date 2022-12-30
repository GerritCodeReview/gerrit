package com.google.gerrit.server.mail;

import com.google.gerrit.proto.Entities.EmailTask;

/** An interface for dispatching email task events. */
public interface EmailTaskDispatcher {
  /** Dispatch an {@code emailTask} for sending email notifications. */
  void dispatch(EmailTask emailTask);
}
