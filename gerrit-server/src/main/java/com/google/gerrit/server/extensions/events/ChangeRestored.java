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

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.events.ChangeRestoredListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangeRestored {
  private static final Logger log =
      LoggerFactory.getLogger(ChangeRestored.class);

  private final DynamicSet<ChangeRestoredListener> listeners;
  private final EventUtil util;

  @Inject
  ChangeRestored(DynamicSet<ChangeRestoredListener> listeners,
      EventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  public void fire(ChangeInfo change, RevisionInfo revision,
      AccountInfo restorer, String reason) {
    Event e = new Event(change, revision, restorer, reason);
    for (ChangeRestoredListener l : listeners) {
      l.onChangeRestored(e);
    }
  }

  public void fire(Change change, PatchSet ps, Account restorer, String reason) {
    try {
      fire(util.changeInfo(change),
          util.revisionInfo(ps),
          util.accountInfo(restorer),
          reason);
    } catch (PatchListNotAvailableException | GpgException | IOException
        | OrmException e) {
      log.error("Couldn't fire event", e);
    }
  }

  private static class Event extends AbstractRevisionEvent
      implements ChangeRestoredListener.Event {

    private AccountInfo restorer;
    private String reason;

    Event(ChangeInfo change, RevisionInfo revision, AccountInfo restorer,
        String reason) {
      super(change, revision);
      this.restorer = restorer;
      this.reason = reason;
    }

    @Override
    public AccountInfo getRestorer() {
      return restorer;
    }

    @Override
    public String getReason() {
      return reason;
    }
  }
}
