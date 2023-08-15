package com.google.gerrit.server.extensions.events;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.HumanComment;
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

  public void fire(
      HumanComment comment, ChangeData changeData, AccountState deleter, Instant when) {
    if (listeners.isEmpty()) {
      return;
    }
    try {
      DraftCommentAdded.Event event =
          new Event(comment, util.changeInfo(changeData), util.accountInfo(deleter), when);
      listeners.runEach(l -> l.onDraftCommentAdded(event));
    } catch (StorageException e) {
      logger.atSevere().withCause(e).log("Couldn't fire event");
    }
  }

  private static class Event extends AbstractChangeEvent
      implements DraftCommentAddedListener.Event {

    public HumanComment comment;

    Event(HumanComment comment, ChangeInfo changeInfo, AccountInfo who, Instant when) {
      super(changeInfo, who, when, NotifyHandling.ALL);
      this.comment = comment;
    }

    @Override
    public String getHumanComment() {
      return comment.toString();
    }
  }
}
