// Copyright (C) 2016 The Android Open Source Project
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
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.events.ChangeRevertedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.sql.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangeReverted {
  private static final Logger log = LoggerFactory.getLogger(ChangeReverted.class);

  private final DynamicSet<ChangeRevertedListener> listeners;
  private final EventUtil util;

  @Inject
  ChangeReverted(DynamicSet<ChangeRevertedListener> listeners, EventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  public void fire(Change change, Change revertChange, Timestamp when) {
    if (!listeners.iterator().hasNext()) {
      return;
    }
    try {
      Event event = new Event(util.changeInfo(change), util.changeInfo(revertChange), when);
      for (ChangeRevertedListener l : listeners) {
        try {
          l.onChangeReverted(event);
        } catch (Exception e) {
          util.logEventListenerError(this, l, e);
        }
      }
    } catch (OrmException e) {
      log.error("Couldn't fire event", e);
    }
  }

  private static class Event extends AbstractChangeEvent implements ChangeRevertedListener.Event {
    private final ChangeInfo revertChange;

    Event(ChangeInfo change, ChangeInfo revertChange, Timestamp when) {
      super(change, revertChange.owner, when, NotifyHandling.ALL);
      this.revertChange = revertChange;
    }

    @Override
    public ChangeInfo getRevertChange() {
      return revertChange;
    }
  }
}
