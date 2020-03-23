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
import com.google.gerrit.extensions.api.changes.TopicInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class PutTopic
    implements RestModifyView<ChangeResource, TopicInput>, UiAction<ChangeResource> {
  private final BatchUpdate.Factory updateFactory;
  private final SetTopicOp.Factory topicOpFactory;

  @Inject
  PutTopic(BatchUpdate.Factory updateFactory, SetTopicOp.Factory topicOpFactory) {
    this.updateFactory = updateFactory;
    this.topicOpFactory = topicOpFactory;
  }

  @Override
  public Response<String> apply(ChangeResource req, TopicInput input)
      throws UpdateException, RestApiException, PermissionBackendException {
    req.permissions().check(ChangePermission.EDIT_TOPIC_NAME);

    if (input != null
        && input.topic != null
        && input.topic.length() > ChangeUtil.TOPIC_MAX_LENGTH) {
      throw new BadRequestException(
          String.format("topic length exceeds the limit (%s)", ChangeUtil.TOPIC_MAX_LENGTH));
    }

    if (input != null && input.topic != null && input.topic.contains("\"")) {
      throw new BadRequestException("topic can't contain the character \".");
    }

    TopicInput sanitizedInput = input == null ? new TopicInput() : input;
    if (sanitizedInput.topic != null) {
      sanitizedInput.topic = sanitizedInput.topic.trim();
    }

    SetTopicOp op = topicOpFactory.create(sanitizedInput);
    try (BatchUpdate u =
        updateFactory.create(req.getChange().getProject(), req.getUser(), TimeUtil.nowTs())) {
      u.addOp(req.getId(), op);
      u.execute();
    }

    if (Strings.isNullOrEmpty(sanitizedInput.topic)) {
      return Response.none();
    }

    return Response.ok(sanitizedInput.topic);
  }

  @Override
  public UiAction.Description getDescription(ChangeResource rsrc) {
    return new UiAction.Description()
        .setLabel("Edit Topic")
        .setVisible(rsrc.permissions().testCond(ChangePermission.EDIT_TOPIC_NAME));
  }
}
