// Copyright (C) 2009 The Android Open Source Project
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

import static com.google.gerrit.common.data.GlobalCapability.KILL_TASK;
import static com.google.gerrit.common.data.GlobalCapability.MAINTAIN_SERVER;

import com.google.gerrit.extensions.annotations.RequiresAnyCapability;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.config.DeleteTask;
import com.google.gerrit.server.config.TaskResource;
import com.google.gerrit.server.config.TasksCollection;
import com.google.gerrit.sshd.AdminHighPriorityCommand;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.args4j.Argument;

/** Kill a task in the work queue. */
@AdminHighPriorityCommand
@RequiresAnyCapability({KILL_TASK, MAINTAIN_SERVER})
final class KillCommand extends SshCommand {
  @Inject private TasksCollection tasksCollection;

  @Inject private DeleteTask deleteTask;

  @Argument(index = 0, multiValued = true, required = true, metaVar = "ID")
  private final List<String> taskIds = new ArrayList<>();

  @Override
  protected void run() {
    ConfigResource cfgRsrc = new ConfigResource();
    for (String id : taskIds) {
      try {
        TaskResource taskRsrc = tasksCollection.parse(cfgRsrc, IdString.fromDecoded(id));
        deleteTask.apply(taskRsrc, null);
      } catch (AuthException | ResourceNotFoundException e) {
        stderr.print("kill: " + id + ": No such task\n");
      }
    }
  }
}
