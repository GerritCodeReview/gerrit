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
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.restapi.config.ListTasks;
import com.google.gerrit.server.restapi.config.ListTasks.TaskInfo;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.sshd.AdminHighPriorityCommand;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.channel.ChannelSession;
import org.kohsuke.args4j.Option;

/** Display the current work queue. */
@AdminHighPriorityCommand
@CommandMetaData(
    name = "show-queue",
    description = "Display the background work queues",
    runsAt = MASTER_OR_SLAVE)
final class ShowQueue extends SshCommand {
  @Option(
      name = "--wide",
      aliases = {"-w"},
      usage = "display without line width truncation")
  private boolean wide;

  @Option(
      name = "--by-queue",
      aliases = {"-q"},
      usage = "group tasks by queue and print queue info")
  private boolean groupByQueue;

  @Inject private PermissionBackend permissionBackend;
  @Inject private ListTasks listTasks;
  @Inject private IdentifiedUser currentUser;
  @Inject private WorkQueue workQueue;

  private int columns = 80;
  private int maxCommandWidth;

  @Override
  public void start(ChannelSession channel, Environment env) throws IOException {
    String s = env.getEnv().get(Environment.ENV_COLUMNS);
    if (s != null && !s.isEmpty()) {
      try {
        columns = Integer.parseInt(s);
      } catch (NumberFormatException err) {
        columns = 80;
      }
    }
    super.start(channel, env);
  }

  @Override
  protected void run() throws Failure {
    enableGracefulStop();
    maxCommandWidth = wide ? Integer.MAX_VALUE : columns - 8 - 12 - 12 - 4 - 4;
    stdout.print(
        String.format(
            "%-8s %-12s %-12s %-4s %s\n", //
            "Task", "State", "StartTime", "", "Command"));
    stdout.print(
        "------------------------------------------------------------------------------\n");

    List<TaskInfo> tasks;
    try {
      tasks = listTasks.apply(new ConfigResource()).value();
    } catch (AuthException e) {
      throw die(e);
    } catch (PermissionBackendException e) {
      throw new Failure(1, "permission backend unavailable", e);
    } catch (Exception e) {
      throw new Failure(1, "unavailable", e);
    }

    boolean viewAll = permissionBackend.user(currentUser).testOrFalse(GlobalPermission.VIEW_QUEUE);
    long now = TimeUtil.nowMs();
    if (groupByQueue) {
      ListMultimap<String, TaskInfo> byQueue = byQueue(tasks);
      for (String queueName : byQueue.keySet()) {
        ScheduledThreadPoolExecutor e = workQueue.getExecutor(queueName);
        stdout.print(String.format("Queue: %s\n", queueName));
        print(byQueue.get(queueName), now, viewAll, e.getCorePoolSize());
      }
    } else {
      print(tasks, now, viewAll, 0);
    }
  }

  private ListMultimap<String, TaskInfo> byQueue(List<TaskInfo> tasks) {
    ListMultimap<String, TaskInfo> byQueue = LinkedListMultimap.create();
    for (TaskInfo task : tasks) {
      byQueue.put(task.queueName, task);
    }
    return byQueue;
  }

  private void print(List<TaskInfo> tasks, long now, boolean viewAll, int threadPoolSize) {
    for (TaskInfo task : tasks) {
      String start;
      switch (task.state) {
        case DONE:
        case CANCELLED:
        case STARTING:
        case RUNNING:
        case STOPPING:
        case READY:
          start = format(task.state);
          break;
        case OTHER:
        case SLEEPING:
        default:
          start = time(now, task.delay);
          break;
      }

      // Shows information about tasks depending on the user rights
      if (viewAll || task.projectName == null) {
        String command =
            task.command.length() < maxCommandWidth
                ? task.command
                : task.command.substring(0, maxCommandWidth);

        stdout.print(
            String.format(
                "%8s %-12s %-12s %-4s %s\n",
                task.id, start, startTime(task.startTime.toInstant()), "", command));
      } else {
        String remoteName =
            task.remoteName != null ? task.remoteName + "/" + task.projectName : task.projectName;

        stdout.print(
            String.format(
                "%8s %-12s %-4s %s\n",
                task.id,
                start,
                startTime(task.startTime.toInstant()),
                MoreObjects.firstNonNull(remoteName, "n/a")));
      }
    }
    stdout.print(
        "------------------------------------------------------------------------------\n");
    stdout.print("  " + tasks.size() + " tasks");
    if (threadPoolSize > 0) {
      stdout.print(", " + threadPoolSize + " worker threads");
    }
    stdout.print("\n\n");
  }

  private static String time(long now, long delay) {
    Instant when = Instant.ofEpochMilli(now + delay);
    return format(when, delay);
  }

  private static String startTime(Instant when) {
    return format(when, TimeUtil.nowMs() - when.toEpochMilli());
  }

  private static String format(Instant when, long timeFromNow) {
    if (timeFromNow < 24 * 60 * 60 * 1000L) {
      return DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
          .withZone(ZoneId.systemDefault())
          .format(when);
    }
    return DateTimeFormatter.ofPattern("MMM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(when);
  }

  private static String format(Task.State state) {
    switch (state) {
      case DONE:
        return "....... done";
      case CANCELLED:
        return "..... killed";
      case STOPPING:
        return "... stopping";
      case RUNNING:
        return "";
      case STARTING:
        return "starting ...";
      case READY:
        return "waiting ....";
      case SLEEPING:
        return "sleeping";
      case OTHER:
      default:
        return state.toString();
    }
  }
}
