
package com.google.gerrit.server.extensions.events;

import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.events.PrivateStateChangedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.sql.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrivateStateChanged {
  private static final Logger log = LoggerFactory.getLogger(PrivateStateChanged.class);

  private final DynamicSet<PrivateStateChangedListener> listeners;
  private final EventUtil util;

  @Inject
  PrivateStateChanged(DynamicSet<PrivateStateChangedListener> listeners, EventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  public void fire(Change change, Account account, Timestamp when, boolean isPrivate) {

    if (!listeners.iterator().hasNext()) {
      return;
    }
    try {
      Event event =
          new Event(util.changeInfo(change), util.accountInfo(account), isPrivate, when);
      for (PrivateStateChangedListener l : listeners) {
        try {
          l.onPrivateStateChanged(event);
        } catch (Exception e) {
          util.logEventListenerError(event, l, e);
        }
      }
    } catch (OrmException e) {
      log.error("Couldn't fire event", e);
    }
  }

  private static class Event extends AbstractChangeEvent
      implements PrivateStateChangedListener.Event {
    boolean isPrivate;

    protected Event(ChangeInfo change, AccountInfo who, boolean isPrivate, Timestamp when) {
      super(change, who, when, NotifyHandling.ALL);
      this.isPrivate = isPrivate;
    }

    @Override
    public boolean isPrivate() {
      return isPrivate;
    }
  }
}
