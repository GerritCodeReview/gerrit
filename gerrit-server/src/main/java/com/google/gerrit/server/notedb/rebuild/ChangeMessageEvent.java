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

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gwtorm.server.OrmException;
import java.sql.Timestamp;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ChangeMessageEvent extends Event {
  private static final Pattern TOPIC_SET_REGEXP = Pattern.compile("^Topic set to (.+)$");
  private static final Pattern TOPIC_CHANGED_REGEXP =
      Pattern.compile("^Topic changed from (.+) to (.+)$");
  private static final Pattern TOPIC_REMOVED_REGEXP = Pattern.compile("^Topic (.+) removed$");

  private final ChangeMessage message;
  private final Change noteDbChange;

  ChangeMessageEvent(ChangeMessage message, Change noteDbChange, Timestamp changeCreatedOn) {
    super(
        message.getPatchSetId(),
        message.getAuthor(),
        message.getRealAuthor(),
        message.getWrittenOn(),
        changeCreatedOn,
        message.getTag());
    this.message = message;
    this.noteDbChange = noteDbChange;
  }

  @Override
  boolean uniquePerUpdate() {
    return true;
  }

  @Override
  void apply(ChangeUpdate update) throws OrmException {
    checkUpdate(update);
    update.setChangeMessage(message.getMessage());
    setTopic(update);
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
}
