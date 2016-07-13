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
import com.google.gerrit.extensions.events.ChangeMergedListener;
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

public class ChangeMerged {
  private static final Logger log =
      LoggerFactory.getLogger(ChangeMerged.class);

  private final DynamicSet<ChangeMergedListener> listeners;
  private final EventUtil util;

  @Inject
  ChangeMerged(DynamicSet<ChangeMergedListener> listeners,
      EventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  public void fire(ChangeInfo change, RevisionInfo revision,
      AccountInfo merger, String newRevisionId, Timestamp when) {
    if (!listeners.iterator().hasNext()) {
      return;
    }
    Event event = new Event(change, revision, merger, newRevisionId, when);
    for (ChangeMergedListener l : listeners) {
      try {
        l.onChangeMerged(event);
      } catch (Exception e) {
        log.warn("Error in event listener", e);
      }
    }
  }

  public void fire(Change change, PatchSet ps, Account merger,
      String newRevisionId, Timestamp when) {
    if (!listeners.iterator().hasNext()) {
      return;
    }
    try {
      fire(util.changeInfo(change),
          util.revisionInfo(change.getProject(), ps),
          util.accountInfo(merger),
          newRevisionId, when);
    } catch (PatchListNotAvailableException | GpgException | IOException
        | OrmException e) {
      log.error("Couldn't fire event", e);
    }
  }

  private static class Event extends AbstractRevisionEvent
      implements ChangeMergedListener.Event {
    private final AccountInfo merger;
    private final String newRevisionId;

    Event(ChangeInfo change, RevisionInfo revision, AccountInfo merger,
        String newRevisionId, Timestamp when) {
      super(change, revision, merger, when, NotifyHandling.ALL);
      this.merger = merger;
      this.newRevisionId = newRevisionId;
    }

    @Override
    public AccountInfo getMerger() {
      return merger;
    }

    @Override
    public String getNewRevisionId() {
      return newRevisionId;
    }
  }
}
