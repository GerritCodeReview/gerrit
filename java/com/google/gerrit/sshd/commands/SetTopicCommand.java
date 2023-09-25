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
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.restapi.change.PutTopic;
import com.google.gerrit.sshd.ChangeArgumentParser;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@CommandMetaData(name = "set-topic", description = "Set the topic for one or more changes")
public class SetTopicCommand extends SshCommand {
  private final ChangeArgumentParser changeArgumentParser;
  private final PutTopic putTopic;

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
    } catch (UnloggedFailure | StorageException | PermissionBackendException e) {
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
  SetTopicCommand(ChangeArgumentParser changeArgumentParser, PutTopic putTopic) {
    this.changeArgumentParser = changeArgumentParser;
    this.putTopic = putTopic;
  }

  @Override
  public void run() throws Exception {
    boolean ok = true;
    if (topic != null) {
      topic = topic.trim();
    }

    if (topic != null && topic.length() > ChangeUtil.TOPIC_MAX_LENGTH) {
      throw new BadRequestException(
          String.format("topic length exceeds the limit (%s)", ChangeUtil.TOPIC_MAX_LENGTH));
    }

    for (ChangeResource r : changes.values()) {
      TopicInput input = new TopicInput();
      input.topic = topic;
      try {
        putTopic.apply(r, input);
      } catch (ResourceNotFoundException e) {
        ok = false;
        writeError(
            "error",
            String.format(
                "could not add topic to change %d: not found", r.getChange().getChangeId()));
      } catch (Exception e) {
        ok = false;
        writeError(
            "error",
            String.format(
                "could not add topic to change %d: %s",
                r.getChange().getChangeId(), e.getMessage()));
      }
    }

    if (!ok) {
      throw die("one or more updates failed");
    }
  }
}
