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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.events.ChangeAbandonedListener;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.patch.PatchListObjectTooLargeException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;

@Singleton
public class ChangeAbandoned {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PluginSetContext<ChangeAbandonedListener> listeners;
  private final EventUtil util;

  @Inject
  ChangeAbandoned(PluginSetContext<ChangeAbandonedListener> listeners, EventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  public void fire(
      Change change,
      PatchSet ps,
      AccountState abandoner,
      String reason,
      Timestamp when,
      NotifyHandling notifyHandling) {
    if (listeners.isEmpty()) {
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
      listeners.runEach(l -> l.onChangeAbandoned(event));
    } catch (PatchListObjectTooLargeException e) {
      logger.atWarning().log("Couldn't fire event: %s", e.getMessage());
    } catch (PatchListNotAvailableException
        | GpgException
        | IOException
        | StorageException
        | PermissionBackendException e) {
      logger.atSevere().withCause(e).log("Couldn't fire event");
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
