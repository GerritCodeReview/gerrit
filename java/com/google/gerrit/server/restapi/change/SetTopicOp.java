// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import com.google.common.base.Strings;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.extensions.api.changes.TopicInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.extensions.events.TopicEdited;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class SetTopicOp implements BatchUpdateOp {
  public interface Factory {
    SetTopicOp create(TopicInput input);
  }

  private final TopicInput input;
  private final TopicEdited topicEdited;
  private final ChangeMessagesUtil cmUtil;

  private Change change;
  private String oldTopicName;
  private String newTopicName;

  @Inject
  public SetTopicOp(
      TopicEdited topicEdited, ChangeMessagesUtil cmUtil, @Assisted TopicInput input) {
    this.input = input;
    this.topicEdited = topicEdited;
    this.cmUtil = cmUtil;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws BadRequestException {
    change = ctx.getChange();
    ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId());
    newTopicName = Strings.nullToEmpty(input.topic);
    oldTopicName = Strings.nullToEmpty(change.getTopic());
    if (oldTopicName.equals(newTopicName)) {
      return false;
    }

    String summary;
    if (oldTopicName.isEmpty()) {
      summary = "Topic set to " + newTopicName;
    } else if (newTopicName.isEmpty()) {
      summary = "Topic " + oldTopicName + " removed";
    } else {
      summary = String.format("Topic changed from %s to %s", oldTopicName, newTopicName);
    }
    change.setTopic(Strings.emptyToNull(newTopicName));
    update.setTopic(change.getTopic());

    ChangeMessage cmsg =
        ChangeMessagesUtil.newMessage(ctx, summary, ChangeMessagesUtil.TAG_SET_TOPIC);
    cmUtil.addChangeMessage(update, cmsg);
    return true;
  }

  @Override
  public void postUpdate(Context ctx) {
    if (change != null) {
      topicEdited.fire(change, ctx.getAccount(), oldTopicName, ctx.getWhen());
    }
  }
}
