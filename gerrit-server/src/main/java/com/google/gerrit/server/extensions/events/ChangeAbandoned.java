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
import com.google.gerrit.extensions.events.ChangeAbandonedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.sql.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangeAbandoned {
  private static final Logger log = LoggerFactory.getLogger(ChangeAbandoned.class);

  private final DynamicSet<ChangeAbandonedListener> listeners;
  private final EventUtil util;

  @Inject
  ChangeAbandoned(DynamicSet<ChangeAbandonedListener> listeners, EventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  public void fire(
      Change change,
      PatchSet ps,
      Account abandoner,
      String reason,
      Timestamp when,
      NotifyHandling notifyHandling) {
    if (!listeners.iterator().hasNext()) {
      return;
    }
    try {
      Event event =
          new Event(
              util.changeInfo(change),
              util.revisionInfo(change.getProject(), ps),
              util.accountInfo(abandoner),
              reason,
              when,
              notifyHandling);
      for (ChangeAbandonedListener l : listeners) {
        try {
          l.onChangeAbandoned(event);
        } catch (Exception e) {
          util.logEventListenerError(this, l, e);
        }
      }
    } catch (PatchListNotAvailableException | GpgException | IOException | OrmException e) {
      log.error("Couldn't fire event", e);
    }
  }

  private static class Event extends AbstractRevisionEvent
      implements ChangeAbandonedListener.Event {
    private final String reason;

    Event(
        ChangeInfo change,
        RevisionInfo revision,
        AccountInfo abandoner,
        String reason,
        Timestamp when,
        NotifyHandling notifyHandling) {
      super(change, revision, abandoner, when, notifyHandling);
      this.reason = reason;
    }

    @Override
    public String getReason() {
      return reason;
    }
  }
}
