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

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.ProjectTask;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.util.IdGenerator;
import com.google.gerrit.sshd.AdminHighPriorityCommand;
import com.google.gerrit.sshd.BaseCommand;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Option;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Display the current work queue. */
@AdminHighPriorityCommand
final class ShowQueue extends BaseCommand {
  @Option(name = "-w", usage = "display without line width truncation")
  private boolean wide;

  @Inject
  private WorkQueue workQueue;

  @Inject
  private ProjectCache projectCache;

  @Inject
  private IdentifiedUser currentUser;

  private PrintWriter p;
  private int columns = 80;
  private int taskNameWidth;

  @Override
  public void start(final Environment env) {
    String s = env.getEnv().get(Environment.ENV_COLUMNS);
    if (s != null && !s.isEmpty()) {
      try {
        columns = Integer.parseInt(s);
      } catch (NumberFormatException err) {
        columns = 80;
      }
    }

    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();
        ShowQueue.this.display();
      }
    });
  }

  private void display() {
    p = toPrintWriter(out);

    final List<Task<?>> pending = workQueue.getTasks();
    Collections.sort(pending, new Comparator<Task<?>>() {
      public int compare(Task<?> a, Task<?> b) {
        final Task.State aState = a.getState();
        final Task.State bState = b.getState();

        if (aState != bState) {
          return aState.ordinal() - bState.ordinal();
        }

        final long aDelay = a.getDelay(TimeUnit.MILLISECONDS);
        final long bDelay = b.getDelay(TimeUnit.MILLISECONDS);

        if (aDelay < bDelay) {
          return -1;
        } else if (aDelay > bDelay) {
          return 1;
        }
        return format(a).compareTo(format(b));
      }
    });

    taskNameWidth = wide ? Integer.MAX_VALUE : columns - 8 - 12 - 8 - 4;

    p.print(String.format("%-8s %-12s %-8s %s\n", //
        "Task", "State", "", "Command"));
    p.print("----------------------------------------------"
        + "--------------------------------\n");

    int numberOfPendingTasks = 0;
    final long now = System.currentTimeMillis();
    final boolean viewAll = currentUser.getCapabilities().canViewQueue();

    for (final Task<?> task : pending) {
      final long delay = task.getDelay(TimeUnit.MILLISECONDS);
      final Task.State state = task.getState();

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
        if (task instanceof ProjectTask<?>) {
          projectName = ((ProjectTask<?>)task).getProjectNameKey();
          remoteName = ((ProjectTask<?>)task).getRemoteName();
          hasCustomizedPrint = ((ProjectTask<?>)task).hasCustomizedPrint();
        }

        ProjectState e = null;
        if (projectName != null) {
          e = projectCache.get(projectName);
        }

        regularUserCanSee = e != null && e.controlFor(currentUser).isVisible();

        if (regularUserCanSee) {
          numberOfPendingTasks++;
        }
      }

      // Shows information about tasks depending on the user rights
      if (viewAll || (!hasCustomizedPrint && regularUserCanSee)) {
        p.print(String.format("%8s %-12s %-8s %s\n", //
            id(task.getTaskId()), start, "", format(task)));
      } else if (regularUserCanSee) {
        if (remoteName == null) {
          remoteName = projectName.get();
        } else {
          remoteName = remoteName + "/" + projectName;
        }

        p.print(String.format("%8s %-12s %-8s %s\n", //
            id(task.getTaskId()), start, "", remoteName));
      }
    }
    p.print("----------------------------------------------"
        + "--------------------------------\n");

    if (viewAll) {
      numberOfPendingTasks = pending.size();
    }

    p.print("  " + numberOfPendingTasks + " tasks\n");

    p.flush();
  }

  private static String id(final int id) {
    return IdGenerator.format(id);
  }

  private static String time(final long now, final long delay) {
    final Date when = new Date(now + delay);
    if (delay < 24 * 60 * 60 * 1000L) {
      return new SimpleDateFormat("HH:mm:ss.SSS").format(when);
    }
    return new SimpleDateFormat("MMM-dd HH:mm").format(when);
  }

  private String format(final Task<?> task) {
    String s = task.toString();
    if (s.length() < taskNameWidth) {
      return s;
    } else {
      return s.substring(0, taskNameWidth);
    }
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
}
