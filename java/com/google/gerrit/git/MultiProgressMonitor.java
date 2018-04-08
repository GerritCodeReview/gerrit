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

package com.google.gerrit.git;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.common.base.Strings;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Progress reporting interface that multiplexes multiple sub-tasks.
 *
 * <p>Output is of the format:
 *
 * <pre>
 *   Task: subA: 1, subB: 75% (3/4) (-)\r
 *   Task: subA: 2, subB: 75% (3/4), subC: 1 (\)\r
 *   Task: subA: 2, subB: 100% (4/4), subC: 1 (|)\r
 *   Task: subA: 4, subB: 100% (4/4), subC: 4, done    \n
 * </pre>
 *
 * <p>Callers should try to keep task and sub-task descriptions short, since the output should fit
 * on one terminal line. (Note that git clients do not accept terminal control characters, so true
 * multi-line progress messages would be impossible.)
 */
public class MultiProgressMonitor {
  private static final Logger log = LoggerFactory.getLogger(MultiProgressMonitor.class);

  /** Constant indicating the total work units cannot be predicted. */
  public static final int UNKNOWN = 0;

  private static final char[] SPINNER_STATES = new char[] {'-', '\\', '|', '/'};
  private static final char NO_SPINNER = ' ';

  /** Handle for a sub-task. */
  public class Task implements ProgressMonitor {
    private final String name;
    private final int total;
    private int count;
    private int lastPercent;

    Task(String subTaskName, int totalWork) {
      this.name = subTaskName;
      this.total = totalWork;
    }

    /**
     * Indicate that work has been completed on this sub-task.
     *
     * <p>Must be called from a worker thread.
     *
     * @param completed number of work units completed.
     */
    @Override
    public void update(int completed) {
      boolean w = false;
      synchronized (MultiProgressMonitor.this) {
        count += completed;
        if (total != UNKNOWN) {
          int percent = count * 100 / total;
          if (percent > lastPercent) {
            lastPercent = percent;
            w = true;
          }
        }
      }
      if (w) {
        wakeUp();
      }
    }

    /**
     * Indicate that this sub-task is finished.
     *
     * <p>Must be called from a worker thread.
     */
    public void end() {
      if (total == UNKNOWN && getCount() > 0) {
        wakeUp();
      }
    }

    @Override
    public void start(int totalTasks) {}

    @Override
    public void beginTask(String title, int totalWork) {}

    @Override
    public void endTask() {}

    @Override
    public boolean isCancelled() {
      return false;
    }

    public int getCount() {
      synchronized (MultiProgressMonitor.this) {
        return count;
      }
    }
  }

  private final OutputStream out;
  private final String taskName;
  private final List<Task> tasks = new CopyOnWriteArrayList<>();
  private int spinnerIndex;
  private char spinnerState = NO_SPINNER;
  private boolean done;
  private boolean write = true;

  private final long maxIntervalNanos;

  /**
   * Create a new progress monitor for multiple sub-tasks.
   *
   * @param out stream for writing progress messages.
   * @param taskName name of the overall task.
   */
  public MultiProgressMonitor(OutputStream out, String taskName) {
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
  public MultiProgressMonitor(
      OutputStream out, String taskName, long maxIntervalTime, TimeUnit maxIntervalUnit) {
    this.out = out;
    this.taskName = taskName;
    maxIntervalNanos = NANOSECONDS.convert(maxIntervalTime, maxIntervalUnit);
  }

  /**
   * Wait for a task managed by a {@link Future}, with no timeout.
   *
   * @see #waitFor(Future, long, TimeUnit)
   */
  public void waitFor(Future<?> workerFuture) throws ExecutionException {
    waitFor(workerFuture, 0, null);
  }

  /**
   * Wait for a task managed by a {@link Future}.
   *
   * <p>Must be called from the main thread, <em>not</em> a worker thread. Once a worker thread
   * calls {@link #end()}, the future has an additional {@code maxInterval} to finish before it is
   * forcefully cancelled and {@link ExecutionException} is thrown.
   *
   * @param workerFuture a future that returns when worker threads are finished.
   * @param timeoutTime overall timeout for the task; the future is forcefully cancelled if the task
   *     exceeds the timeout. Non-positive values indicate no timeout.
   * @param timeoutUnit unit for overall task timeout.
   * @throws ExecutionException if this thread or a worker thread was interrupted, the worker was
   *     cancelled, or timed out waiting for a worker to call {@link #end()}.
   */
  public void waitFor(Future<?> workerFuture, long timeoutTime, TimeUnit timeoutUnit)
      throws ExecutionException {
    long overallStart = System.nanoTime();
    long deadline;
    String detailMessage = "";
    if (timeoutTime > 0) {
      deadline = overallStart + NANOSECONDS.convert(timeoutTime, timeoutUnit);
    } else {
      deadline = 0;
    }

    synchronized (this) {
      long left = maxIntervalNanos;
      while (!done) {
        long start = System.nanoTime();
        try {
          NANOSECONDS.timedWait(this, left);
        } catch (InterruptedException e) {
          throw new ExecutionException(e);
        }

        // Send an update on every wakeup (manual or spurious), but only move
        // the spinner every maxInterval.
        long now = System.nanoTime();

        if (deadline > 0 && now > deadline) {
          workerFuture.cancel(true);
          if (workerFuture.isCancelled()) {
            detailMessage =
                String.format(
                    "(timeout %sms, cancelled)",
                    TimeUnit.MILLISECONDS.convert(now - deadline, NANOSECONDS));
            log.warn(
                String.format(
                    "MultiProgressMonitor worker killed after %sms" + detailMessage, //
                    TimeUnit.MILLISECONDS.convert(now - overallStart, NANOSECONDS)));
          }
          break;
        }

        left -= now - start;
        if (left <= 0) {
          moveSpinner();
          left = maxIntervalNanos;
        }
        sendUpdate();
        if (!done && workerFuture.isDone()) {
          // The worker may not have called end() explicitly, which is likely a
          // programming error.
          log.warn("MultiProgressMonitor worker did not call end() before returning");
          end();
        }
      }
      sendDone();
    }

    // The loop exits as soon as the worker calls end(), but we give it another
    // maxInterval to finish up and return.
    try {
      workerFuture.get(maxIntervalNanos, NANOSECONDS);
    } catch (InterruptedException e) {
      throw new ExecutionException(e);
    } catch (CancellationException e) {
      throw new ExecutionException(detailMessage, e);
    } catch (TimeoutException e) {
      workerFuture.cancel(true);
      throw new ExecutionException(e);
    }
  }

  private synchronized void wakeUp() {
    notifyAll();
  }

  /**
   * Begin a sub-task.
   *
   * @param subTask sub-task name.
   * @param subTaskWork total work units in sub-task, or {@link #UNKNOWN}.
   * @return sub-task handle.
   */
  public Task beginSubTask(String subTask, int subTaskWork) {
    Task task = new Task(subTask, subTaskWork);
    tasks.add(task);
    return task;
  }

  /**
   * End the overall task.
   *
   * <p>Must be called from a worker thread.
   */
  public synchronized void end() {
    done = true;
    wakeUp();
  }

  private void sendDone() {
    spinnerState = NO_SPINNER;
    StringBuilder s = format();
    boolean any = false;
    for (Task t : tasks) {
      if (t.count != 0) {
        any = true;
        break;
      }
    }
    if (any) {
      s.append(",");
    }
    s.append(" done    \n");
    send(s);
  }

  private void moveSpinner() {
    spinnerIndex = (spinnerIndex + 1) % SPINNER_STATES.length;
    spinnerState = SPINNER_STATES[spinnerIndex];
  }

  private void sendUpdate() {
    send(format());
  }

  private StringBuilder format() {
    StringBuilder s = new StringBuilder().append("\r").append(taskName).append(':');

    if (!tasks.isEmpty()) {
      boolean first = true;
      for (Task t : tasks) {
        int count = t.getCount();
        if (count == 0) {
          continue;
        }

        if (!first) {
          s.append(',');
        } else {
          first = false;
        }

        s.append(' ');
        if (!Strings.isNullOrEmpty(t.name)) {
          s.append(t.name).append(": ");
        }
        if (t.total == UNKNOWN) {
          s.append(count);
        } else {
          s.append(String.format("%d%% (%d/%d)", count * 100 / t.total, count, t.total));
        }
      }
    }

    if (spinnerState != NO_SPINNER) {
      // Don't output a spinner until the alarm fires for the first time.
      s.append(" (").append(spinnerState).append(')');
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
