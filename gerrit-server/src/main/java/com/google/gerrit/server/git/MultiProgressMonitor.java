// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.git;

import org.eclipse.jgit.lib.Constants;

import java.io.IOException;
import java.io.OutputStream;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Progress reporting interface that multiplexes multiple sub-tasks.
 * <p>
 * Output is of the format:
 * <pre>
 *   Task: (-) subA: 1, subB: 75% (3/4)\r
 *   Task: (\) subA: 2, subB: 75% (3/4), subC: 1\r
 *   Task: (|) subA: 2, subB: 100% (4/4), subC: 1\r
 *   Task: (/) subA: 4, subB: 100% (4/4), subC: 4, done    \n
 * </pre>
 * <p>
 * Callers should try to keep task and sub-task descriptions short, since the
 * output should fit on one terminal line. (Note that git clients do not accept
 * terminal control characters, so true multi-line progress messages would be
 * impossible.)
 */
public class MultiProgressMonitor {
  private static final ScheduledThreadPoolExecutor alarmQueue;

  static final Object alarmQueueKiller;

  // TODO(dborowitz): This is currently copied from BatchingProgressMonitor.
  // Consider something more reusable, e.g. exposing that executor for use from
  // this class (straightforward but a bit ugly).
  static {
    // To support garbage collection, start our thread but
    // swap out the thread factory. When our class is GC'd
    // the alarmQueueKiller will finalize and ask the executor
    // to shutdown, ending the worker.
    //
    int threads = 1;
    alarmQueue = new ScheduledThreadPoolExecutor(threads,
        new ThreadFactory() {
          private final ThreadFactory baseFactory = Executors
              .defaultThreadFactory();

          @Override
          public Thread newThread(Runnable taskBody) {
            Thread thr = baseFactory.newThread(taskBody);
            thr.setName("Gerrit-AlarmQueue");
            thr.setDaemon(true);
            return thr;
          }
        });
    alarmQueue.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    alarmQueue.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    alarmQueue.prestartAllCoreThreads();

    // Now that the threads are running, its critical to swap out
    // our own thread factory for one that isn't in the ClassLoader.
    // This allows the class to GC.
    //
    alarmQueue.setThreadFactory(Executors.defaultThreadFactory());

    alarmQueueKiller = new Object() {
      @Override
      protected void finalize() {
        alarmQueue.shutdownNow();
      }
    };
  }

  /** Constant indicating the total work units cannot be predicted. */
  public static final int UNKNOWN = 0;

  private static final char[] SPINNER_STATES = new char[]{'-', '\\', '|', '/'};
  private static final char NO_SPINNER = ' ';

  /** Handle for a sub-task. */
  public class Task {
    private final int total;
    private int count;
    private int lastPercent;

    Task(final int totalWork) {
      this.total = totalWork;
    }

    /**
     * Indicate that work has been completed on this sub-task.
     *
     * @param completed number of work units completed.
     */
    public void update(final int completed) {
      count += completed;
      if (total != UNKNOWN) {
        int percent = count * 100 / total;
        if (percent > lastPercent) {
          lastPercent = percent;
          sendUpdate();
        }
      }
    }

    /** Indicate that this sub-task is finished. */
    public void end() {
      sendUpdate();
    }
  }

  private class UpdateCallback implements Runnable {
    @Override
    public void run() {
      spinnerIndex = (spinnerIndex + 1) % SPINNER_STATES.length;
      spinnerState = SPINNER_STATES[spinnerIndex];
      sendUpdate();
      restartTimer();
    }
  }

  private final OutputStream out;
  private final String taskName;
  private final Map<String, Task> tasks = new LinkedHashMap<String, Task>();
  private volatile int spinnerIndex;
  private volatile char spinnerState = NO_SPINNER;
  private boolean write = true;

  private final long maxIntervalTime;
  private final TimeUnit maxIntervalUnit;

  private Future<?> timerFuture;

  /**
   * Create a new progress monitor for multiple sub-tasks.
   *
   * @param out stream for writing progress messages.
   * @param taskName name of the overall task.
   */
  public MultiProgressMonitor(final OutputStream out, final String taskName) {
    this(out, taskName, 500, TimeUnit.MILLISECONDS);
  }

  /**
   * Create a new progress monitor for multiple sub-tasks.
   *
   * @param out stream for writing progress messages.
   * @param taskName name of the overall task.
   * @param maxIntervalTime maximum interval between progress messages.
   * @param maxIntervalUnit time unit for progress interval.
   */
  public MultiProgressMonitor(final OutputStream out, final String taskName,
      long maxIntervalTime, TimeUnit maxIntervalUnit) {
    this.out = out;
    this.taskName = taskName;
    this.maxIntervalTime = maxIntervalTime;
    this.maxIntervalUnit = maxIntervalUnit;
    restartTimer();
  }

  /**
   * Begin a sub-task.
   *
   * @param subTask sub-task name.
   * @param subTaskWork total work units in sub-task, or {@link #UNKNOWN}.
   * @return sub-task handle.
   */
  public Task beginSubTask(final String subTask, final int subTaskWork) {
    Task task = new Task(subTaskWork);
    tasks.put(subTask, task);
    return task;
  }

  /** End the overall task. */
  public void end() {
    timerFuture.cancel(false);
    spinnerState = ' ';
    StringBuilder s = format();
    s.append(", done    \n");
    send(s);
  }

  private void restartTimer() {
    if (timerFuture != null) {
      timerFuture.cancel(false);
    }
    timerFuture = alarmQueue.schedule(new UpdateCallback(), maxIntervalTime,
        maxIntervalUnit);
  }

  private void sendUpdate() {
    send(format());
  }

  private StringBuilder format() {
    StringBuilder s = new StringBuilder().append("\r").append(taskName)
        .append(": ");
    if (spinnerState != NO_SPINNER) {
      // Don't output a spinner until the alarm fires for the first time.
      s.append('(').append(spinnerState).append(") ");
    }

    if (!tasks.isEmpty()) {
      boolean first = true;
      for (Map.Entry<String, Task> e : tasks.entrySet()) {
        Task t = e.getValue();
        if (t.count == 0) {
          continue;
        }

        if (!first) {
          s.append(", ");
        } else {
          first = false;
        }

        s.append(e.getKey()).append(": ");
        if (t.total == UNKNOWN) {
          s.append(t.count);
        } else {
          s.append(t.count * 100 / t.total).append("% (").append(t.count)
              .append('/').append(t.total).append(')');
        }
      }
    }
    return s;
  }

  private void send(StringBuilder s) {
    if (write) {
      try {
        out.write(Constants.encode(s.toString()));
        out.flush();
      } catch (IOException e) {
        write = false;
      }
    }
  }
}
