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

package com.google.gerrit.server.change;

import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.extensions.events.TopicEdited;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class SetTopicOp implements BatchUpdateOp {
  public interface Factory {
    SetTopicOp create(@Nullable String topic);
  }

  private final String topic;
  private final TopicEdited topicEdited;
  private final ChangeMessagesUtil cmUtil;

  private Change change;
  private String oldTopicName;
  private String newTopicName;

  @Inject
  public SetTopicOp(
      TopicEdited topicEdited, ChangeMessagesUtil cmUtil, @Nullable @Assisted String topic) {
    this.topic = topic;
    this.topicEdited = topicEdited;
    this.cmUtil = cmUtil;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws BadRequestException {
    change = ctx.getChange();
    ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId());
    newTopicName = Strings.nullToEmpty(topic);
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
    try {
      update.setTopic(change.getTopic());
    } catch (ValidationException ex) {
      throw new BadRequestException(ex.getMessage());
    }
    cmUtil.setChangeMessage(ctx, summary, ChangeMessagesUtil.TAG_SET_TOPIC);
    return true;
  }

  @Override
  public void postUpdate(PostUpdateContext ctx) {
    if (change != null) {
      topicEdited.fire(ctx.getChangeData(change), ctx.getAccount(), oldTopicName, ctx.getWhen());
    }
  }
}
