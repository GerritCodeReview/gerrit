
package com.google.gerrit.server.extensions.events;

import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.events.WorkInProgressStateChangedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.sql.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkInProgressStateChanged {
  private static final Logger log = LoggerFactory.getLogger(WorkInProgressStateChanged.class);

  private final DynamicSet<WorkInProgressStateChangedListener> listeners;
  private final EventUtil util;

  @Inject
  WorkInProgressStateChanged(
      DynamicSet<WorkInProgressStateChangedListener> listeners, EventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  public void fire(Change change, Account account, Timestamp when, boolean isWip) {

    if (!listeners.iterator().hasNext()) {
      return;
    }
    try {
      Event event = new Event(util.changeInfo(change), util.accountInfo(account), isWip, when);
      for (WorkInProgressStateChangedListener l : listeners) {
        try {
          l.onWorkInProgressStateChanged(event);
        } catch (Exception e) {
          util.logEventListenerError(event, l, e);
        }
      }
    } catch (OrmException e) {
      log.error("Couldn't fire event", e);
    }
  }

  private static class Event extends AbstractChangeEvent
      implements WorkInProgressStateChangedListener.Event {
    boolean isWip;

    protected Event(ChangeInfo change, AccountInfo who, boolean isWip, Timestamp when) {
      super(change, who, when, NotifyHandling.ALL);
      this.isWip = isWip;
    }

    @Override
    public boolean isWip() {
      return isWip;
    }
  }
}
