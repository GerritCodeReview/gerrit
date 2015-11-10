package com.google.gerrit.server.events;

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.events.DraftPublishedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.extensions.events.AbstractRevisionEvent;
import com.google.gerrit.server.extensions.events.ChangeEventUtil;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import java.io.IOException;

public class DraftPublished {

  private final DynamicSet<DraftPublishedListener> listeners;
  private final ChangeEventUtil util;

  @Inject
  public DraftPublished(DynamicSet<DraftPublishedListener> listeners,
      ChangeEventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  public void fire(ChangeInfo change, RevisionInfo revision,
      AccountInfo publisher) {
    Event e = new Event(change, revision, publisher);
    for (DraftPublishedListener l : listeners) {
      l.onDraftPublished(e);
    }
  }

  public void fire(Change change, PatchSet patchSet, Account.Id accountId)
      throws OrmException {
    try {
      fire(util.changeInfo(change),
          util.revisionInfo(patchSet),
          util.accountInfo(accountId));
    } catch (PatchListNotAvailableException | GpgException | IOException e) {
      throw new OrmException(e);
    }
  }

  private static class Event extends AbstractRevisionEvent
      implements DraftPublishedListener.Event {
    private final AccountInfo publisher;

    Event(ChangeInfo change, RevisionInfo revision, AccountInfo publisher) {
      super(change, revision);
      this.publisher = publisher;
    }

    @Override
    public AccountInfo getPublisher() {
      return publisher;
    }
  }
}
