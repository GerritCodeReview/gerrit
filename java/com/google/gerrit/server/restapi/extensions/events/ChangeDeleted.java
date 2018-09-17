// Copyright (C) 2018 The Android Open Source Project
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
import com.google.gerrit.extensions.events.ChangeDeletedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ChangeDeleted {
  private static final Logger log = LoggerFactory.getLogger(ChangeDeleted.class);

  private final DynamicSet<ChangeDeletedListener> listeners;
  private final EventUtil util;

  @Inject
  ChangeDeleted(DynamicSet<ChangeDeletedListener> listeners, EventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  public void fire(Change change, Account deleter, Timestamp when) {
    if (!listeners.iterator().hasNext()) {
      return;
    }
    try {
      Event event = new Event(util.changeInfo(change), util.accountInfo(deleter), when);
      for (ChangeDeletedListener l : listeners) {
        try {
          l.onChangeDeleted(event);
        } catch (Exception e) {
          util.logEventListenerError(this, l, e);
        }
      }
    } catch (OrmException e) {
      log.error("Couldn't fire event", e);
    }
  }

  private static class Event extends AbstractChangeEvent implements ChangeDeletedListener.Event {
    Event(ChangeInfo change, AccountInfo deleter, Timestamp when) {
      super(change, deleter, when, NotifyHandling.ALL);
    }
  }
}
