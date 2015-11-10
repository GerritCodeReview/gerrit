package com.google.gerrit.server.events;

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.events.DraftPublishedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.extensions.events.AbstractRevisionEvent;
import com.google.gerrit.server.extensions.events.ChangeEventUtil;
import com.google.inject.Inject;

public class DraftPublished {

  private final DynamicSet<DraftPublishedListener> listeners;
  private final ChangeEventUtil util;

  @Inject
  public DraftPublished(DynamicSet<DraftPublishedListener> listeners,
      ChangeEventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  public void fire(ChangeInfo change, RevisionInfo revision, AccountInfo changer) {
    Event e = new Event(change, revision, changer);
    for (DraftPublishedListener l : listeners) {
      l.onDraftPublished(e);
    }
  }

  private static class Event extends AbstractRevisionEvent
      implements DraftPublishedListener.Event {
    private final AccountInfo changer;

    Event(ChangeInfo change, RevisionInfo revision, AccountInfo changer) {
      super(change, revision);
      this.changer = changer;
    }

    @Override
    public AccountInfo getChanger() {
      return changer;
    }
  }

  public void fire(Change change, PatchSet patchSet, Account.Id accountId) {
  }
}
