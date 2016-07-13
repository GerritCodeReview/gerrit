package com.google.gerrit.server.extensions.events;

import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.events.GerritEvent;

/** Intermediate class for events that do not support notification type. */
public abstract class AbstractNoNotifyEvent implements GerritEvent {
  @Override
  public NotifyHandling getNotify() {
    return NotifyHandling.NONE;
  }
}
