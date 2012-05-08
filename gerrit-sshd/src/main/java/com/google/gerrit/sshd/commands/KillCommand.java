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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.gerrit.server.util.IdGenerator;
import com.google.gerrit.sshd.AdminHighPriorityCommand;
import com.google.gerrit.sshd.RequiresCapability;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

import org.kohsuke.args4j.Argument;

import java.util.HashSet;
import java.util.Set;

/** Kill a task in the work queue. */
@AdminHighPriorityCommand
@RequiresCapability(GlobalCapability.KILL_TASK)
final class KillCommand extends SshCommand {
  @Inject
  private WorkQueue workQueue;

  private final Set<Integer> taskIds = new HashSet<Integer>();

  @Argument(index = 0, multiValued = true, required = true, metaVar = "ID")
  void addTaskId(final String taskId) {
    int p = 0;
    while (p < taskId.length() - 1 && taskId.charAt(p) == '0') {
      p++;
    }
    taskIds.add((int) Long.parseLong(taskId.substring(p), 16));
  }

  @Override
  protected void run() {
    for (final Integer id : taskIds) {
      final Task<?> task = workQueue.getTask(id);
      if (task != null) {
        task.cancel(true);
      } else {
        stderr.print("kill: " + IdGenerator.format(id) + ": No such task\n");
      }
    }
  }
}
