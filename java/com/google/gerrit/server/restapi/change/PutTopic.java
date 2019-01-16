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
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.TopicInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.extensions.events.TopicEdited;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class PutTopic extends RetryingRestModifyView<ChangeResource, TopicInput, Response<String>>
    implements UiAction<ChangeResource> {
  private final ChangeMessagesUtil cmUtil;
  private final TopicEdited topicEdited;

  @Inject
  PutTopic(ChangeMessagesUtil cmUtil, RetryHelper retryHelper, TopicEdited topicEdited) {
    super(retryHelper);
    this.cmUtil = cmUtil;
    this.topicEdited = topicEdited;
  }

  @Override
  protected Response<String> applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource req, TopicInput input)
      throws UpdateException, RestApiException, PermissionBackendException {
    req.permissions().check(ChangePermission.EDIT_TOPIC_NAME);

    if (input != null
        && input.topic != null
        && input.topic.length() > ChangeUtil.TOPIC_MAX_LENGTH) {
      throw new BadRequestException(
          String.format("topic length exceeds the limit (%s)", ChangeUtil.TOPIC_MAX_LENGTH));
    }

    TopicInput sanitizedInput = input == null ? new TopicInput() : input;
    if (sanitizedInput.topic != null) {
      sanitizedInput.topic = sanitizedInput.topic.trim();
    }

    Op op = new Op(sanitizedInput);
    try (BatchUpdate u =
        updateFactory.create(req.getChange().getProject(), req.getUser(), TimeUtil.nowTs())) {
      u.addOp(req.getId(), op);
      u.execute();
    }
    return Strings.isNullOrEmpty(op.newTopicName) ? Response.none() : Response.ok(op.newTopicName);
  }

  private class Op implements BatchUpdateOp {
    private final TopicInput input;

    private Change change;
    private String oldTopicName;
    private String newTopicName;

    Op(TopicInput input) {
      this.input = input;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws StorageException {
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

  @Override
  public UiAction.Description getDescription(ChangeResource rsrc) {
    return new UiAction.Description()
        .setLabel("Edit Topic")
        .setVisible(rsrc.permissions().testCond(ChangePermission.EDIT_TOPIC_NAME));
  }
}
