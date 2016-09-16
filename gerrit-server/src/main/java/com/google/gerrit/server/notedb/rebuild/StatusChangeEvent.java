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

package com.google.gerrit.server.notedb.rebuild;

import com.google.common.base.Optional;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gwtorm.server.OrmException;

import java.sql.Timestamp;
import java.util.regex.Pattern;

class StatusChangeEvent extends Event {
  private static final Pattern STATUS_ABANDONED_REGEXP =
      Pattern.compile("^Abandoned(\n.*)*$");
  private static final Pattern STATUS_RESTORED_REGEXP =
      Pattern.compile("^Restored(\n.*)*$");

  static Optional<StatusChangeEvent> parseFromMessage(ChangeMessage message,
      Change noteDbChange, Timestamp changeCreatedOn) {
    String msg = message.getMessage();
    if (msg == null) {
      return Optional.absent();
    }
    if (STATUS_ABANDONED_REGEXP.matcher(msg).matches()) {
      return Optional.of(new StatusChangeEvent(
          message, noteDbChange, changeCreatedOn, Change.Status.ABANDONED));
    }
    if (STATUS_RESTORED_REGEXP.matcher(msg).matches()) {
      return Optional.of(new StatusChangeEvent(
          message, noteDbChange, changeCreatedOn, Change.Status.NEW));
    }
    return Optional.absent();
  }

  private final Change noteDbChange;
  private final Change.Status status;

  private StatusChangeEvent(ChangeMessage message, Change noteDbChange,
      Timestamp changeCreatedOn, Change.Status status) {
    this(message.getPatchSetId(), message.getAuthor(),
        message.getWrittenOn(), noteDbChange, changeCreatedOn, message.getTag(),
        status);
  }

  private StatusChangeEvent(PatchSet.Id psId, Account.Id author,
      Timestamp when, Change noteDbChange, Timestamp changeCreatedOn,
      String tag, Change.Status status) {
    super(psId, author, when, changeCreatedOn, tag);
    this.noteDbChange = noteDbChange;
    this.status = status;
  }

  @Override
  boolean uniquePerUpdate() {
    return true;
  }

  @Override
  void apply(ChangeUpdate update) throws OrmException {
    checkUpdate(update);
    update.fixStatus(status);
    noteDbChange.setStatus(status);
  }
}
