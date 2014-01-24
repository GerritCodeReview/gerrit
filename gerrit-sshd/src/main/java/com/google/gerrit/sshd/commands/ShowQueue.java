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

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.TaskInfoFactory;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.ProjectTask;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.util.IdGenerator;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gerrit.sshd.AdminHighPriorityCommand;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Display the current work queue. */
@AdminHighPriorityCommand
@CommandMetaData(name = "show-queue", description = "Display the background work queues")
final class ShowQueue extends SshCommand {
  @Option(name = "--wide", aliases = {"-w"}, usage = "display without line width truncation")
  private boolean wide;

  @Inject
  private WorkQueue workQueue;

  @Inject
  private ProjectCache projectCache;

  @Inject
  private IdentifiedUser currentUser;

  private int columns = 80;
  private int taskNameWidth;

  @Override
  public void start(final Environment env) throws IOException {
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
  protected void run() {
    taskNameWidth = wide ? Integer.MAX_VALUE : columns - 8 - 12 - 12 - 4 - 4;
    final List<QueueTaskInfo> pending = getSortedTaskInfoList();

    stdout.print(String.format("%-8s %-12s %-12s %-4s %s\n", //
        "Task", "State", "StartTime", "", "Command"));
    stdout.print("----------------------------------------------"
        + "--------------------------------\n");

    int numberOfPendingTasks = 0;
    final long now = TimeUtil.nowMs();
    final boolean viewAll = currentUser.getCapabilities().canViewQueue();

    for (final QueueTaskInfo taskInfo : pending) {
      final long delay = taskInfo.delayMillis;
      final Task.State state = taskInfo.state;

      final String start;
      switch (state) {
        case DONE:
        case CANCELLED:
        case RUNNING:
        case READY:
          start = format(state);
          break;
        default:
          start = time(now, delay);
          break;
      }

      boolean regularUserCanSee = false;
      boolean hasCustomizedPrint = true;

      // If the user is not administrator, check if has rights to see
      // the Task
      Project.NameKey projectName = null;
      String remoteName = null;

      if (!viewAll) {
        projectName = taskInfo.getProjectNameKey();
        remoteName = taskInfo.getRemoteName();
        hasCustomizedPrint = taskInfo.hasCustomizedPrint();

        ProjectState e = null;
        if (projectName != null) {
          e = projectCache.get(projectName);
        }

        regularUserCanSee = e != null && e.controlFor(currentUser).isVisible();

        if (regularUserCanSee) {
          numberOfPendingTasks++;
        }
      }

      String startTime = startTime(taskInfo.getStartTime());

      // Shows information about tasks depending on the user rights
      if (viewAll || (!hasCustomizedPrint && regularUserCanSee)) {
        stdout.print(String.format("%8s %-12s %-12s %-4s %s\n", //
            id(taskInfo.getTaskId()), start, startTime, "",
            taskInfo.getTaskString(taskNameWidth)));
      } else if (regularUserCanSee) {
        if (remoteName == null) {
          remoteName = projectName.get();
        } else {
          remoteName = remoteName + "/" + projectName;
        }

        stdout.print(String.format("%8s %-12s %-4s %s\n", //
            id(taskInfo.getTaskId()), start, startTime, remoteName));
      }
    }
    stdout.print("----------------------------------------------"
        + "--------------------------------\n");

    if (viewAll) {
      numberOfPendingTasks = pending.size();
    }

    stdout.print("  " + numberOfPendingTasks + " tasks\n");
  }

  private List<QueueTaskInfo> getSortedTaskInfoList() {
    final List<QueueTaskInfo> taskInfos =
        workQueue.getTaskInfos(new TaskInfoFactory<QueueTaskInfo>() {
          @Override
          public QueueTaskInfo getTaskInfo(Task<?> task) {
            return new QueueTaskInfo(task);
          }
        });
    Collections.sort(taskInfos, new Comparator<QueueTaskInfo>() {
      @Override
      public int compare(QueueTaskInfo a, QueueTaskInfo b) {
        if (a.state != b.state) {
          return a.state.ordinal() - b.state.ordinal();
        }

        int cmp = Long.signum(a.delayMillis - b.delayMillis);
        if (cmp != 0) {
          return cmp;
        }

        return a.getTaskString(taskNameWidth)
            .compareTo(b.getTaskString(taskNameWidth));
      }
    });
    return taskInfos;
  }

  private static String id(final int id) {
    return IdGenerator.format(id);
  }

  private static String time(final long now, final long delay) {
    final Date when = new Date(now + delay);
    return format(when, delay);
  }

  private static String startTime(final Date when) {
    return format(when, TimeUtil.nowMs() - when.getTime());
  }

  private static String format(final Date when, final long timeFromNow) {
    if (timeFromNow < 24 * 60 * 60 * 1000L) {
      return new SimpleDateFormat("HH:mm:ss.SSS").format(when);
    }
    return new SimpleDateFormat("MMM-dd HH:mm").format(when);
  }

  private static String format(final Task.State state) {
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

  private static class QueueTaskInfo {
    private final long delayMillis;
    private final Task.State state;
    private final Task<?> task;

    QueueTaskInfo(Task<?> task) {
      this.task = task;
      this.delayMillis = task.getDelay(TimeUnit.MILLISECONDS);
      this.state = task.getState();
    }

    String getRemoteName() {
      if (task instanceof ProjectTask) {
        return ((ProjectTask<?>) task).getRemoteName();
      }
      return null;
    }

    Project.NameKey getProjectNameKey() {
      if (task instanceof ProjectTask<?>) {
        return ((ProjectTask<?>) task).getProjectNameKey();
      }
      return null;
    }

    boolean hasCustomizedPrint() {
      if (task instanceof ProjectTask<?>) {
        return ((ProjectTask<?>) task).hasCustomizedPrint();
      }
      return false;
    }

    int getTaskId() {
      return task.getTaskId();
    }

    Date getStartTime() {
      return task.getStartTime();
    }

    String getTaskString(int maxLength) {
      String s = task.toString();
      return s.length() < maxLength ? s : s.substring(0, maxLength);
    }
  }
}
