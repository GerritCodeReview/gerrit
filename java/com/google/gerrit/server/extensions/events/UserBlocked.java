// Copyright (C) 2023 The Android Open Source Project
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
import com.google.gerrit.extensions.events.UserBlockedListener;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;

/** Helper class to fire an event when a user gets blocked. */
@Singleton
public class UserBlocked {
  private final PluginSetContext<UserBlockedListener> listeners;
  private final EventUtil util;

  @Inject
  UserBlocked(PluginSetContext<UserBlockedListener> listeners, EventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  public void fire(
      AccountState blocker,
      AccountState user,
      String blockedUsersGroup,
      NotifyHandling notify,
      Instant when) {
    if (listeners.isEmpty()) {
      return;
    }

    Event event =
        new Event(
            util.accountInfo(blocker), util.accountInfo(user), blockedUsersGroup, notify, when);
    listeners.runEach(l -> l.onUserBlocked(event));
  }

  private static class Event extends AbstractNoNotifyEvent implements UserBlockedListener.Event {
    private final AccountInfo blocker;
    private final AccountInfo user;
    private final String blockedUsersGroup;
    private NotifyHandling notify;
    private final Instant when;

    private Event(
        AccountInfo blocker,
        AccountInfo user,
        String blockedUsersGroup,
        NotifyHandling notify,
        Instant when) {
      this.blocker = blocker;
      this.user = user;
      this.blockedUsersGroup = blockedUsersGroup;
      this.notify = notify;
      this.when = when;
    }

    @Override
    public AccountInfo getBlocker() {
      return blocker;
    }

    @Override
    public AccountInfo getUser() {
      return user;
    }

    @Override
    public Instant getWhen() {
      return when;
    }

    @Override
    public String getBlockedUsersGroup() {
      return blockedUsersGroup;
    }

    @Override
    public NotifyHandling getNotify() {
      return notify;
    }
  }
}
