package com.google.gerrit.server.extensions.events;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.events.DraftCommentAddedListener;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;

@Singleton
public class DraftCommentAdded {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PluginSetContext<DraftCommentAddedListener> listeners;
  private final EventUtil util;

  @Inject
  DraftCommentAdded(PluginSetContext<DraftCommentAddedListener> listeners, EventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  public void fire(ChangeData changeData, AccountState deleter, Instant when) {
    if (listeners.isEmpty()) {
      return;
    }
    try {
      DraftCommentAdded.Event event =
          new DraftCommentAdded.Event(util.changeInfo(changeData), util.accountInfo(deleter), when);
      listeners.runEach(l -> l.onDraftCommentAdded(event));
    } catch (StorageException e) {
      logger.atSevere().withCause(e).log("Couldn't fire event");
    }
  }

  private static class Event extends AbstractChangeEvent
      implements DraftCommentAddedListener.Event {
    Event(ChangeInfo change, AccountInfo deleter, Instant when) {
      super(change, deleter, when, NotifyHandling.ALL);
    }
  }
}
