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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gwtorm.server.OrmException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

class StatusChangeEvent extends Event {
  private static final ImmutableMap<Change.Status, Pattern> PATTERNS =
      ImmutableMap.of(
          Change.Status.ABANDONED, Pattern.compile("^Abandoned(\n.*)*$"),
          Change.Status.MERGED,
              Pattern.compile(
                  "^Change has been successfully" + " (merged|cherry-picked|rebased|pushed).*$"),
          Change.Status.NEW, Pattern.compile("^Restored(\n.*)*$"));

  static Optional<StatusChangeEvent> parseFromMessage(
      ChangeMessage message, Change change, Change noteDbChange) {
    String msg = message.getMessage();
    if (msg == null) {
      return Optional.empty();
    }
    for (Map.Entry<Change.Status, Pattern> e : PATTERNS.entrySet()) {
      if (e.getValue().matcher(msg).matches()) {
        return Optional.of(new StatusChangeEvent(message, change, noteDbChange, e.getKey()));
      }
    }
    return Optional.empty();
  }

  private final Change.Status status;
  private final Change change;
  private final Change noteDbChange;

  private StatusChangeEvent(
      ChangeMessage message, Change change, Change noteDbChange, Change.Status status) {
    this(
        message.getPatchSetId(),
        message.getAuthor(),
        message.getWrittenOn(),
        change,
        noteDbChange,
        message.getTag(),
        status);
  }

  private StatusChangeEvent(
      PatchSet.Id psId,
      Account.Id author,
      Timestamp when,
      Change change,
      Change noteDbChange,
      String tag,
      Change.Status status) {
    super(psId, author, author, when, change.getCreatedOn(), tag);
    this.change = change;
    this.noteDbChange = noteDbChange;
    this.status = status;
  }

  @Override
  boolean uniquePerUpdate() {
    return true;
  }

  @SuppressWarnings("deprecation")
  @Override
  void apply(ChangeUpdate update) throws OrmException {
    checkUpdate(update);
    update.fixStatus(status);
    noteDbChange.setStatus(status);
    if (status == Change.Status.MERGED) {
      update.setSubmissionId(change.getSubmissionId());
      noteDbChange.setSubmissionId(change.getSubmissionId());
    }
  }

  @Override
  protected boolean isSubmit() {
    return status == Change.Status.MERGED;
  }
}
