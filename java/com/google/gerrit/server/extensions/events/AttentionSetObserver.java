// Copyright (C) 2022 The Android Open Source Project
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
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.events.AttentionSetListener;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** Helper class to fire an event when an attention set changes. */
@Singleton
public class AttentionSetObserver {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PluginSetContext<AttentionSetListener> listeners;
  private final EventUtil util;
  private final AccountCache accountCache;

  @Inject
  AttentionSetObserver(
      PluginSetContext<AttentionSetListener> listeners, EventUtil util, AccountCache accountCache) {
    this.listeners = listeners;
    this.util = util;
    this.accountCache = accountCache;
  }

  /**
   * Notify all listening plugins
   *
   * @param changeData is current data of the change
   * @param accountState is the initiator of the change
   * @param update is the update that caused the event
   * @param when is the time of the event
   */
  public void fire(
      ChangeData changeData, AccountState accountState, AttentionSetUpdate update, Instant when) {
    if (listeners.isEmpty()) {
      return;
    }
    AccountState target = accountCache.get(update.account()).get();

    HashSet<Integer> added = new HashSet<>();
    HashSet<Integer> removed = new HashSet<>();
    switch (update.operation()) {
      case ADD:
        added.add(target.account().id().get());
        break;
      case REMOVE:
        removed.add(target.account().id().get());
        break;
    }

    try {
      Event event =
          new Event(
              util.changeInfo(changeData), util.accountInfo(accountState), added, removed, when);
      listeners.runEach(l -> l.onAttentionSetChanged(event));
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Exception while firing AttentionSet changed event");
    }
  }

  /** Event to be fired when an attention set changes */
  private static class Event extends AbstractChangeEvent implements AttentionSetListener.Event {
    private final Set<Integer> added;
    private final Set<Integer> removed;

    Event(
        ChangeInfo change,
        AccountInfo editor,
        Set<Integer> added,
        Set<Integer> removed,
        Instant when) {
      super(change, editor, when, NotifyHandling.ALL);
      this.added = added;
      this.removed = removed;
    }

    @Override
    public Set<Integer> usersAdded() {
      return added;
    }

    @Override
    public Set<Integer> usersRemoved() {
      return removed;
    }
  }
}
