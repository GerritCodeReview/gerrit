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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.events.PrivateStateChangedListener;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;

@Singleton
public class PrivateStateChanged {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PluginSetContext<PrivateStateChangedListener> listeners;
  private final EventUtil util;

  @Inject
  PrivateStateChanged(PluginSetContext<PrivateStateChangedListener> listeners, EventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  public void fire(Change change, PatchSet patchSet, AccountState account, Timestamp when) {
    if (listeners.isEmpty()) {
      return;
    }
    try {
      Event event =
          new Event(
              util.changeInfo(change),
              util.revisionInfo(change.getProject(), patchSet),
              util.accountInfo(account),
              when);
      listeners.runEach(l -> l.onPrivateStateChanged(event));
    } catch (OrmException
        | PatchListNotAvailableException
        | GpgException
        | IOException
        | PermissionBackendException e) {
      logger.atSevere().withCause(e).log("Couldn't fire event");
    }
  }

  private static class Event extends AbstractRevisionEvent
      implements PrivateStateChangedListener.Event {

    protected Event(ChangeInfo change, RevisionInfo revision, AccountInfo who, Timestamp when) {
      super(change, revision, who, when, NotifyHandling.ALL);
    }
  }
}
