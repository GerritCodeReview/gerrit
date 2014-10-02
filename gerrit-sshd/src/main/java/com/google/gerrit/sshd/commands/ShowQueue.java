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

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;

import com.google.common.base.MoreObjects;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.config.ListTasks;
import com.google.gerrit.server.config.ListTasks.TaskInfo;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.gerrit.sshd.AdminHighPriorityCommand;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** Display the current work queue. */
@AdminHighPriorityCommand
@CommandMetaData(name = "show-queue", description = "Display the background work queues",
  runsAt = MASTER_OR_SLAVE)
final class ShowQueue extends SshCommand {
  @Option(name = "--wide", aliases = {"-w"}, usage = "display without line width truncation")
  private boolean wide;

  @Inject
  private ListTasks listTasks;

  @Inject
  private IdentifiedUser currentUser;

  private int columns = 80;
  private int taskNameWidth;

  @Override
  public void start(Environment env) throws IOException {
    String s = env.getEnv().get(Environment.ENV_COLUMNS);
    if (s != null && !s.isEmpty()) {
      try {
        columns = Integer.parseInt(s);
      } catch (NumberFormatException err) {
        columns = 80;
      }
    }
    super.start(env);
  }

  @Override
  protected void run() throws UnloggedFailure {
    taskNameWidth = wide ? Integer.MAX_VALUE : columns - 8 - 12 - 12 - 4 - 4;
    stdout.print(String.format("%-8s %-12s %-12s %-4s %s\n", //
        "Task", "State", "StartTime", "", "Command"));
    stdout.print("----------------------------------------------"
        + "--------------------------------\n");

    try {
      List<TaskInfo> tasks = listTasks.apply(new ConfigResource());
      long now = TimeUtil.nowMs();
      boolean viewAll = currentUser.getCapabilities().canViewQueue();
      for (TaskInfo task : tasks) {
        String start;
        switch (task.state) {
          case DONE:
          case CANCELLED:
          case RUNNING:
          case READY:
            start = format(task.state);
            break;
          default:
            start = time(now, task.delay);
            break;
        }

        // Shows information about tasks depending on the user rights
        if (viewAll || task.projectName == null) {
          String command = task.command.length() < taskNameWidth
              ? task.command
              : task.command.substring(0, taskNameWidth);

          stdout.print(String.format("%8s %-12s %-12s %-4s %s\n",
              task.id, start, startTime(task.startTime), "", command));
        } else {
          String remoteName = task.remoteName != null
              ? task.remoteName + "/" + task.projectName
              : task.projectName;

          stdout.print(String.format("%8s %-12s %-4s %s\n",
              task.id, start, startTime(task.startTime),
              MoreObjects.firstNonNull(remoteName, "n/a")));
        }
      }
      stdout.print("----------------------------------------------"
          + "--------------------------------\n");
      stdout.print("  " + tasks.size() + " tasks\n");
    } catch (AuthException e) {
      throw die(e);
    }
  }

  private static String time(long now, long delay) {
    Date when = new Date(now + delay);
    return format(when, delay);
  }

  private static String startTime(final Date when) {
    return format(when, TimeUtil.nowMs() - when.getTime());
  }

  private static String format(Date when, long timeFromNow) {
    if (timeFromNow < 24 * 60 * 60 * 1000L) {
      return new SimpleDateFormat("HH:mm:ss.SSS").format(when);
    }
    return new SimpleDateFormat("MMM-dd HH:mm").format(when);
  }

  private static String format(Task.State state) {
    switch (state) {
      case DONE:
        return "....... done";
      case CANCELLED:
        return "..... killed";
      case RUNNING:
        return "";
      case READY:
        return "waiting ....";
      case SLEEPING:
        return "sleeping";
      default:
        return state.toString();
    }
  }
}
