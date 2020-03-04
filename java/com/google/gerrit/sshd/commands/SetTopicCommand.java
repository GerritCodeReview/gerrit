// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.entities.Change;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.TopicInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.extensions.events.TopicEdited;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.restapi.change.SetTopicOp;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.sshd.ChangeArgumentParser;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@CommandMetaData(name = "set-topic", description = "Set the topic for one or more changes")
public class SetTopicCommand extends SshCommand {
  private final BatchUpdate.Factory updateFactory;
  private final ChangeMessagesUtil cmUtil;
  private final TopicEdited topicEdited;
  private final ChangeArgumentParser changeArgumentParser;
  private final SetTopicOp.Factory topicOpFactory;

  private Map<Change.Id, ChangeResource> changes = new LinkedHashMap<>();

  @Argument(
      index = 0,
      required = true,
      multiValued = true,
      metaVar = "CHANGE",
      usage = "changes to index")
  void addChange(String token) {
    try {
      changeArgumentParser.addChange(token, changes, null, true);
    } catch (UnloggedFailure | StorageException | PermissionBackendException | IOException e) {
      writeError("warning", e.getMessage());
    }
  }

  @Option(
      name = "--topic",
      aliases = "-t",
      usage = "applies a topic to the given changes",
      metaVar = "TOPIC")
  private String topic;

  @Inject
  SetTopicCommand(
      BatchUpdate.Factory updateFactory,
      ChangeMessagesUtil cmUtil,
      TopicEdited topicEdited,
      ChangeArgumentParser changeArgumentParser,
      SetTopicOp.Factory topicOpFactory) {
    this.updateFactory = updateFactory;
    this.cmUtil = cmUtil;
    this.topicEdited = topicEdited;
    this.changeArgumentParser = changeArgumentParser;
    this.topicOpFactory = topicOpFactory;
  }

  @Override
  public void run() throws Exception {
    TopicInput input = new TopicInput();
    if (topic != null) {
      input.topic = topic.trim();
    }

    if (input.topic != null && input.topic.length() > ChangeUtil.TOPIC_MAX_LENGTH) {
      throw new BadRequestException(
          String.format("topic length exceeds the limit (%s)", ChangeUtil.TOPIC_MAX_LENGTH));
    }

    for (ChangeResource r : changes.values()) {
      SetTopicOp op = topicOpFactory.create(input);
      try (BatchUpdate u =
          updateFactory.create(r.getChange().getProject(), user, TimeUtil.nowTs())) {
        u.addOp(r.getId(), op);
        u.execute();
      }
      return;
    }
  }
}
