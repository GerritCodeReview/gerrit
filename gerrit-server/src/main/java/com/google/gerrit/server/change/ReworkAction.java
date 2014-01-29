// Copyright (C) 2014 The Android Open Source Project
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

import java.io.IOException;
import java.util.Collections;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

abstract class ReworkAction {
  static class Input {
    String message;
  }

  private final Provider<ReviewDb> dbProvider;
  private final Provider<CurrentUser> userProvider;
  private final ChangeIndexer indexer;

  @Inject
  ReworkAction(Provider<ReviewDb> dbProvider,
      Provider<CurrentUser> userProvider,
      ChangeIndexer indexer) {
    this.dbProvider = dbProvider;
    this.userProvider = userProvider;
    this.indexer = indexer;
  }

  protected void changeStatus(Change change, Input input, final Status from,
      final Status to) throws OrmException, ResourceConflictException,
      IOException {
    ReviewDb db = dbProvider.get();
    Change.Id changeId = change.getId();
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

      CheckedFuture<?, IOException> indexFuture =
          indexer.indexAsync(change.getId());
      indexFuture.checkedGet();

      db.commit();
    } finally {
      db.rollback();
    }
  }

  private ChangeMessage newMessage(Input input,
      Change change) throws OrmException {
    StringBuilder buf = new StringBuilder(change.getStatus() == Status.DRAFT
        ? "Work In Progress"
        : "Ready For Review");
    String msg = Strings.nullToEmpty(input.message).trim();
    if (!msg.isEmpty()) {
      buf.append("\n\n");
      buf.append(msg);
    }

    ChangeMessage message = new ChangeMessage(
        new ChangeMessage.Key(
            change.getId(),
            ChangeUtil.messageUUID(dbProvider.get())),
        ((IdentifiedUser)userProvider.get()).getAccountId(),
        change.getLastUpdatedOn(),
        change.currentPatchSetId());
    message.setMessage(buf.toString());
    return message;
  }

  protected static String status(Change change) {
    return change != null
        ? change.getStatus().name().toLowerCase()
        : "deleted";
  }
}
