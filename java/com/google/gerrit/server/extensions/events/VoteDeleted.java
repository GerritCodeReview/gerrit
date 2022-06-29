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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.events.VoteDeletedListener;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/** Helper class to fire an event when a vote has been deleted from a change. */
@Singleton
public class VoteDeleted {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PluginSetContext<VoteDeletedListener> listeners;
  private final EventUtil util;

  @Inject
  VoteDeleted(PluginSetContext<VoteDeletedListener> listeners, EventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  public void fire(
      ChangeData changeData,
      PatchSet ps,
      AccountState reviewer,
      Map<String, Short> approvals,
      Map<String, Short> oldApprovals,
      NotifyHandling notify,
      String message,
      @Nullable AccountState remover,
      Instant when) {
    if (listeners.isEmpty()) {
      return;
    }
    try {
      Event event =
          new Event(
              util.changeInfo(changeData),
              util.revisionInfo(changeData.project(), ps),
              util.accountInfo(reviewer),
              util.approvals(reviewer, approvals, when),
              util.approvals(reviewer, oldApprovals, when),
              notify,
              message,
              util.accountInfo(remover),
              when);
      listeners.runEach(l -> l.onVoteDeleted(event));
    } catch (DiffNotAvailableException
        | GpgException
        | IOException
        | StorageException
        | PermissionBackendException e) {
      logger.atSevere().withCause(e).log("Couldn't fire event");
    }
  }

  /** Event to be fired when a vote has been deleted from a change. */
  private static class Event extends AbstractRevisionEvent implements VoteDeletedListener.Event {
    private final AccountInfo reviewer;
    private final Map<String, ApprovalInfo> approvals;
    private final Map<String, ApprovalInfo> oldApprovals;
    private final String message;

    Event(
        ChangeInfo change,
        RevisionInfo revision,
        AccountInfo reviewer,
        Map<String, ApprovalInfo> approvals,
        Map<String, ApprovalInfo> oldApprovals,
        NotifyHandling notify,
        String message,
        AccountInfo remover,
        Instant when) {
      super(change, revision, remover, when, notify);
      this.reviewer = reviewer;
      this.approvals = approvals;
      this.oldApprovals = oldApprovals;
      this.message = message;
    }

    @Override
    public Map<String, ApprovalInfo> getApprovals() {
      return approvals;
    }

    @Override
    public Map<String, ApprovalInfo> getOldApprovals() {
      return oldApprovals;
    }

    @Override
    public String getMessage() {
      return message;
    }

    @Override
    public AccountInfo getReviewer() {
      return reviewer;
    }
  }
}
