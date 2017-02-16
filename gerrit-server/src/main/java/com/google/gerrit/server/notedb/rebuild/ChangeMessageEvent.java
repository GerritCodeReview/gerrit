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

import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gwtorm.server.OrmException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ChangeMessageEvent extends Event {
  private static final ImmutableMap<Change.Status, Pattern> STATUS_PATTERNS =
      ImmutableMap.of(
          Change.Status.ABANDONED, Pattern.compile("^Abandoned(\n.*)*$"),
          Change.Status.MERGED,
              Pattern.compile(
                  "^Change has been successfully (merged|cherry-picked|rebased|pushed).*$"),
          Change.Status.NEW, Pattern.compile("^Restored(\n.*)*$"));

  private static final Pattern TOPIC_SET_REGEXP = Pattern.compile("^Topic set to (.+)$");
  private static final Pattern TOPIC_CHANGED_REGEXP =
      Pattern.compile("^Topic changed from (.+) to (.+)$");
  private static final Pattern TOPIC_REMOVED_REGEXP = Pattern.compile("^Topic (.+) removed$");

  private final Change change;
  private final Change noteDbChange;
  private final Optional<Change.Status> status;
  private final ChangeMessage message;

  ChangeMessageEvent(
      Change change, Change noteDbChange, ChangeMessage message, Timestamp changeCreatedOn) {
    super(
        message.getPatchSetId(),
        message.getAuthor(),
        message.getRealAuthor(),
        message.getWrittenOn(),
        changeCreatedOn,
        message.getTag());
    this.change = change;
    this.noteDbChange = noteDbChange;
    this.message = message;
    this.status = parseStatus(message);
  }

  @Override
  boolean uniquePerUpdate() {
    return true;
  }

  @Override
  protected boolean isSubmit() {
    return status.isPresent() && status.get() == Change.Status.MERGED;
  }

  @Override
  protected boolean canHaveTag() {
    return true;
  }

  @SuppressWarnings("deprecation")
  @Override
  void apply(ChangeUpdate update) throws OrmException {
    checkUpdate(update);
    update.setChangeMessage(message.getMessage());
    setTopic(update);

    if (status.isPresent()) {
      Change.Status s = status.get();
      update.fixStatus(s);
      noteDbChange.setStatus(s);
      if (s == Change.Status.MERGED) {
        update.setSubmissionId(change.getSubmissionId());
        noteDbChange.setSubmissionId(change.getSubmissionId());
      }
    }
  }

  private static Optional<Change.Status> parseStatus(ChangeMessage message) {
    String msg = message.getMessage();
    if (msg == null) {
      return Optional.empty();
    }
    for (Map.Entry<Change.Status, Pattern> e : STATUS_PATTERNS.entrySet()) {
      if (e.getValue().matcher(msg).matches()) {
        return Optional.of(e.getKey());
      }
    }
    return Optional.empty();
  }

  private void setTopic(ChangeUpdate update) {
    String msg = message.getMessage();
    if (msg == null) {
      return;
    }
    Matcher m = TOPIC_SET_REGEXP.matcher(msg);
    if (m.matches()) {
      String topic = m.group(1);
      update.setTopic(topic);
      noteDbChange.setTopic(topic);
      return;
    }

    m = TOPIC_CHANGED_REGEXP.matcher(msg);
    if (m.matches()) {
      String topic = m.group(2);
      update.setTopic(topic);
      noteDbChange.setTopic(topic);
      return;
    }

    if (TOPIC_REMOVED_REGEXP.matcher(msg).matches()) {
      update.setTopic(null);
      noteDbChange.setTopic(null);
    }
  }

  @Override
  protected void addToString(ToStringHelper helper) {
    helper.add("message", message);
  }
}
