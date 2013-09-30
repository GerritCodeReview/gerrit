// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;

import java.util.Collections;

abstract class BaseWipAction {
  public static class Input {
    public String message;
  }

  private final Provider<ReviewDb> dbProvider;
  private final Provider<CurrentUser> userProvider;
  private final boolean wipWorkflowEnabled;

  @Inject
  BaseWipAction(@GerritServerConfig Config config,
      Provider<ReviewDb> dbProvider,
      Provider<CurrentUser> userProvider) {
    this.dbProvider = dbProvider;
    this.userProvider = userProvider;
    this.wipWorkflowEnabled = config.getBoolean("gerrit",
        null, "wipWorkflowEnabled", false);
  }

  protected void changeStatus(Change change, Input input, final Status from,
      final Status to) throws OrmException,
      ResourceConflictException {
    ReviewDb db = dbProvider.get();
    Id changeId = change.getId();
    db.changes().beginTransaction(changeId);
    try {
      change = db.changes().atomicUpdate(
        changeId,
        new AtomicUpdate<Change>() {
          @Override
          public Change update(Change change) {
            if (change.getStatus() == from) {
              change.setStatus(to);
              ChangeUtil.updated(change);
              return change;
            }
            return null;
          }
        });

      if (change == null) {
        throw new ResourceConflictException("change is "
            + status(db.changes().get(changeId)));
      }

      db.changeMessages().insert(Collections.singleton(
          newMessage(input, change)));
      new ApprovalsUtil(db).syncChangeStatus(change);
      db.commit();
    } finally {
      db.rollback();
    }
  }

  private ChangeMessage newMessage(Input input,
      Change change) throws OrmException {
    StringBuilder msg = new StringBuilder(
        "Change "
        + change.getId().get()
        + ": "
        + ((change.getStatus() == Status.WORKINPROGRESS)
            ? "Work In Progress"
            : "Ready For Review"));
    if (!Strings.nullToEmpty(input.message).trim().isEmpty()) {
      msg.append("\n\n");
      msg.append(input.message.trim());
    }

    ChangeMessage message = new ChangeMessage(
        new ChangeMessage.Key(
            change.getId(),
            ChangeUtil.messageUUID(dbProvider.get())),
        ((IdentifiedUser)userProvider.get()).getAccountId(),
        change.getLastUpdatedOn(),
        change.currentPatchSetId());
    message.setMessage(msg.toString());
    return message;
  }

  protected static String status(Change change) {
    return change != null
        ? change.getStatus().name().toLowerCase()
        : "deleted";
  }

  protected boolean isWipWorkflowEnabled() {
    return wipWorkflowEnabled;
  }
}
