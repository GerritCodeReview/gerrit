// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.extensions.events;

import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.events.DraftPublishedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Timestamp;

public class DraftPublished {
  private static final Logger log =
      LoggerFactory.getLogger(DraftPublished.class);

  private final DynamicSet<DraftPublishedListener> listeners;
  private final EventUtil util;

  @Inject
  public DraftPublished(DynamicSet<DraftPublishedListener> listeners,
      EventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  public void fire(ChangeInfo change, RevisionInfo revision,
      AccountInfo publisher, Timestamp when) {
    if (!listeners.iterator().hasNext()) {
      return;
    }
    Event event = new Event(change, revision, publisher, when);
    for (DraftPublishedListener l : listeners) {
      try {
        l.onDraftPublished(event);
      } catch (Exception e) {
        log.warn("Error in event listener", e);
      }
    }
  }

  public void fire(Change change, PatchSet patchSet, Account.Id accountId,
      Timestamp when) {
    try {
      fire(util.changeInfo(change),
          util.revisionInfo(change.getProject(), patchSet),
          util.accountInfo(accountId),
          when);
    } catch (PatchListNotAvailableException | GpgException | IOException
        | OrmException e) {
      log.error("Couldn't fire event", e);
    }
  }

  private static class Event extends AbstractRevisionEvent
      implements DraftPublishedListener.Event {
    private final AccountInfo publisher;

    Event(ChangeInfo change, RevisionInfo revision, AccountInfo publisher,
        Timestamp when) {
      super(change, revision, publisher, when, NotifyHandling.ALL);
      this.publisher = publisher;
    }

    @Override
    public AccountInfo getPublisher() {
      return publisher;
    }
  }
}
