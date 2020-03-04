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

import com.google.common.base.Strings;
import com.google.gerrit.extensions.api.changes.TopicInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.extensions.events.TopicEdited;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.restapi.change.SetTopicOp;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gerrit.sshd.BaseCommand.UnloggedFailure;
import com.google.inject.Inject;
import org.kohsuke.args4j.Argument;

@CommandMetaData(name = "set-topic", description = "Set the topic for a change")
public class SetTopicCommand extends SshCommand {
  private final BatchUpdate.Factory updateFactory;
  private final ChangeMessagesUtil cmUtil;
  private final TopicEdited topicEdited;
  private final ChangeIdParser changeIdParser;

  @Inject
  SetTopicCommand(BatchUpdate.Factory updateFactory, ChangeMessagesUtil cmUtil, TopicEdited topicEdited, ChangeIdParser changeIdParser) {
    this.updateFactory = updateFactory;
    this.cmUtil = cmUtil;
    this.topicEdited = topicEdited;
	this.changeIdParser = changeIdParser;
  }

  @Argument(
      index = 0,
      required = true,
      metaVar = "{TOPIC}",
      usage = "topic to be set")
  private String topic;

  @Argument(
      index = 1,
      required = true,
      metaVar = "{PROJECT}",
      usage = "project containing the specified change" )
  private ProjectState projectState;

  @Argument(
      index = 2,
      required = true,
      metaVar = "{BRANCH}",
      usage = "branch containing the specified change" )
  private String branch;

  @Argument(
      index = 3,
      required = true,
      metaVar = "{CHANGE-ID}",
      usage = "list of changes to set topic for")
  private String changeId;

  @Override
  public void run() throws Exception {
    TopicInput input = new TopicInput();
    if (topic != null) {
      input.topic = topic.trim();
    }

    if (input.topic != null && input.topic.length() > ChangeUtil.TOPIC_MAX_LENGTH) {
      throw new BadRequestException(
        String.format(
          "topic length exceeds the limit (%s)", ChangeUtil.TOPIC_MAX_LENGTH));
    }

    Change change = changeIdParser.parseChangeId(changeId, projectState, branch);
    SetTopicOp op = new SetTopicOp(input, topicEdited, cmUtil);
    try (BatchUpdate u =
        updateFactory.create(change.getProject(), user, TimeUtil.nowTs())) {
      u.addOp(change.getId(), op);
      u.execute();
    }

	return;
  }
}
