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

package com.google.gerrit.server.ssh;

import com.google.gerrit.git.WorkQueue;
import com.google.gerrit.git.WorkQueue.Task;

import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/** Display the current work queue. */
class AdminShowQueue extends AbstractCommand {
  PrintWriter p;

  @Override
  protected void run() throws Failure, UnsupportedEncodingException {
    assertIsAdministrator();
    p = toPrintWriter(out);

    final Task<?>[] pending = WorkQueue.getTasks();
    Arrays.sort(pending, new Comparator<Task<?>>() {
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

    p.print(String.format(" %1s  %-12s  %s\n", "S", "Start", "Task"));
    p.print("--------------------------------------------------------------\n");

    final long now = System.currentTimeMillis();
    for (final Task<?> task : pending) {
      final long delay = task.getDelay(TimeUnit.MILLISECONDS);
      final Task.State state = task.getState();

      final String start;
      switch (state) {
        case DONE:
        case CANCELLED:
        case RUNNING:
        case READY:
          start = "";
          break;
        default:
          start = time(now, delay);
          break;
      }

      p.print(String.format(" %1s  %12s  %s\n", format(state), start,
          format(task)));
    }
    p.print("--------------------------------------------------------------\n");
    p.print("  " + pending.length + " tasks\n");

    p.flush();
  }

  private static String time(final long now, final long delay) {
    final Date when = new Date(now + delay);
    if (delay < 24 * 60 * 60 * 1000L) {
      return new SimpleDateFormat("HH:mm:ss.SSS").format(when);
    }
    return new SimpleDateFormat("MMM-dd HH:mm").format(when);
  }

  private static String format(final Task<?> task) {
    return task.getRunnable().toString();
  }

  private static String format(final Task.State state) {
    switch (state) {
      case DONE:
        return "D";
      case CANCELLED:
        return "C";
      case RUNNING:
        return "R";
      case READY:
        return "W";
      case SLEEPING:
        return "S";
      default:
        return " ";
    }
  }
}
