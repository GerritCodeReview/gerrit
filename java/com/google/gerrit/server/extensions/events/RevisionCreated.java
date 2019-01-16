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
import com.google.gerrit.extensions.events.RevisionCreatedListener;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.patch.PatchListObjectTooLargeException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;

@Singleton
public class RevisionCreated {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final RevisionCreated DISABLED =
      new RevisionCreated() {
        @Override
        public void fire(
            Change change,
            PatchSet patchSet,
            AccountState uploader,
            Timestamp when,
            NotifyResolver.Result notify) {}
      };

  private final PluginSetContext<RevisionCreatedListener> listeners;
  private final EventUtil util;

  @Inject
  RevisionCreated(PluginSetContext<RevisionCreatedListener> listeners, EventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  private RevisionCreated() {
    this.listeners = null;
    this.util = null;
  }

  public void fire(
      Change change,
      PatchSet patchSet,
      AccountState uploader,
      Timestamp when,
      NotifyResolver.Result notify) {
    if (listeners.isEmpty()) {
      return;
    }
    try {
      Event event =
          new Event(
              util.changeInfo(change),
              util.revisionInfo(change.getProject(), patchSet),
              util.accountInfo(uploader),
              when,
              notify.handling());
      listeners.runEach(l -> l.onRevisionCreated(event));
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
      implements RevisionCreatedListener.Event {

    Event(
        ChangeInfo change,
        RevisionInfo revision,
        AccountInfo uploader,
        Timestamp when,
        NotifyHandling notify) {
      super(change, revision, uploader, when, notify);
    }
  }
}
