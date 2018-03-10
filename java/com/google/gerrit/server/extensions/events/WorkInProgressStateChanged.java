// Copyright (C) 2017 The Android Open Source Project
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
import com.google.gerrit.extensions.events.WorkInProgressStateChangedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.account.AccountState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
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

  public void fire(Change change, AccountState account, Timestamp when) {
    if (!listeners.iterator().hasNext()) {
      return;
    }
    try {
      Event event = new Event(util.changeInfo(change), util.accountInfo(account), when);
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

    protected Event(ChangeInfo change, AccountInfo who, Timestamp when) {
      super(change, who, when, NotifyHandling.ALL);
    }
  }
}
