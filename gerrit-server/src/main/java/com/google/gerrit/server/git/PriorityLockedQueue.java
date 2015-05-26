// Copyright (C) 2015 The Android Open Source Project
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

import com.google.common.collect.Sets;
import com.google.gerrit.common.MultiLock;
import com.google.gerrit.server.git.PriorityLockedQueue.Task;

import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * This class schedules tasks requiring resources such that no tasks requiring
 * the same resource are scheduled at the same time.
 * <p>
 * There are two ways of scheduling a task. The first way {@code process} is
 * synchronous and either proceeds or fails to process the task. The second way
 * {@code schedule} is asynchronous and will process the task eventually when
 * resource constraints are met.
 *
 * @param <R> The resource type to be required for the scheduled tasks.
 * @param <T> The task type to be scheduled.
 */
public abstract class PriorityLockedQueue<R, T extends Task<R>> {
  public interface Task<R> {
    /** @return the resources required to be locked to perform this task. */
    Set<R> resources();
  }

  protected final Set<T> currentTasks;

  // Contains locks on all resources we are currently holding
  protected final MultiLock<R> locks;

  // all future tasks
  private final PriorityQueue<TimedResourceTask> queue;

  private final ScheduledExecutorService exec;

  // keeps track of the next wake up time
  private ScheduledFuture<?> nextWakeUp;

  private final long timeout;

  PriorityLockedQueue(ScheduledExecutorService sched, long timeout) {
    this.locks = new MultiLock<>();
    this.queue = new PriorityQueue<>();
    this.exec = sched;
    this.timeout = timeout;
    this.currentTasks = Sets.newHashSet();
  }

  /**
   * Process a task immediately, if it is possible to do so.
   * <p>
   * If it cannot be processed right now, no further action will be taken and
   * false is returned.
   *
   * @param task the task be be run
   * @return if the given item will be processed right now.
   */
  public boolean process(T task) {
    boolean ret = tryRunTask(task, false, 0);
    // During processing some other scheduled task may have become ready
    // but blocked so check if we can run them now
    checkQueue();

    return ret;
  }

  /**
   * Schedule a task to be processed in the future.
   * <p>
   * The {@code minimumDelay} which is measured in milliseconds determines the
   * minimum time it waits until scheduling is attempted. If the resource
   * constraints cannot be met at the time of the scheduling attempt, the attempt
   * will be further delaying using an exponential back off scheme, doubling the
   * {@code minimumDelay} with each failed attempt.
   *
   * @param rtask the task be be run
   * @param minimumDelay the minimum delay in milliseconds when this task
   *    should be run.
   */
  public synchronized void schedule(T rtask, long minimumDelay) {
    queue.add(new TimedResourceTask(rtask, minimumDelay));
    scheduleQueueCheck(minimumDelay);
  }

  /**
   * Try to process a task.
   *
   * @param timeout the timeout in milliseconds for the resources to be reserved
   *    if run asynchronously.
   * @returns {@code true} if the task was processed when running synchronously
   *    or if it was started when running asynchronously {@code false} otherwise.
   */
  protected boolean tryRunTask(final T task, boolean async, long timeout) {
    if (!locks.lock(task.resources())) {
      return false;
    }
    synchronized(currentTasks) {
      currentTasks.add(task);
    }
    if (!async) {
      try {
        processTask(task);
      } finally {
        synchronized(currentTasks) {
          currentTasks.remove(task);
        }
        currentTasks.remove(task);
        locks.unlock(task.resources());
      }
    } else {
      try {
        processAsyncTask(task);
      } finally {
        exec.schedule(new Runnable() {
          @Override
          public void run() {
            processingAsyncTaskDone(task);
          }
        }, timeout, TimeUnit.MILLISECONDS);
      }
    }

    return true;
  }

  /**
   * Schedule a check for a queue after {@code delay} milliseconds.
   * <p>
   * If there is already a check scheduled the earlier check is kept, while the
   * later check is dropped.
   *
   * @param delay The minimum amount of time in milliseconds after which the
   *    next wake up call will check the queue.
   */
  private synchronized void scheduleQueueCheck(final long delay) {
    Runnable wakeUpAction = new Runnable() {
      @Override
      public void run() {
        synchronized(queue) {
          nextWakeUp = null;
          for (TimedResourceTask t : queue) {
            t.decreaseDelay(delay);
          }
        }
        checkQueue();
      }
    };
    if (nextWakeUp == null) {
      nextWakeUp = exec.schedule(wakeUpAction, delay, TimeUnit.MILLISECONDS);
    } else if (delay < nextWakeUp.getDelay(TimeUnit.MILLISECONDS)) {
      nextWakeUp.cancel(false);
      nextWakeUp = exec.schedule(wakeUpAction, delay, TimeUnit.MILLISECONDS);
    }
  }

  private synchronized void checkQueue() {
    TimedResourceTask rtask = queue.peek();
    while (rtask != null && rtask.delay() <= 0) {
      queue.remove();
      if (!tryRunTask(rtask.task, true, timeout)) {
        rtask.calculateNewDelay();
        scheduleQueueCheck(rtask.delay());
      }
      rtask = queue.peek();
    }

    TimedResourceTask nextTask = queue.peek();
    if (nextTask != null) {
      scheduleQueueCheck(nextTask.delay());
    }
  }

  /**
   * The actual work will be done inside this function.
   *
   * @param task the task to be performed
   */
  abstract void processTask(T task);

  /**
   * The actual work will be done inside this function. It is assumed the work
   * will be split out to another thread, and this will return early fast.
   *
   * If the task is done and its resources don't require a lock anymore,
   * a call to {@code processingAsyncTaskDone} should be made. If this call
   * is not made, it will be called internally after the timeout as specified
   * in the constructor. To avoid resource conflicts the task must not take
   * longer than the time out.
   *
   * @param task the task to be performed
   */
  abstract void processAsyncTask(T task);

  public void processingAsyncTaskDone(T task) {
    synchronized(currentTasks) {
      if (currentTasks.contains(task)) {
        currentTasks.remove(task);
        locks.unlock(task.resources());
        // TODO(sbeller): As this method is called twice (once as a timeout and
        // once supposedly by the finished worker. Maybe there is an efficient
        // way to cancel the later call from the first call.
      }
    }
  }

  private class TimedResourceTask implements Comparable<TimedResourceTask> {
    private final T task;
    private long delay;
    private long backoff;

    public TimedResourceTask(T task, long delay) {
      this.task = task;
      this.delay = delay;
      this.backoff = delay;
    }

    synchronized void calculateNewDelay() {
      delay = 2 * backoff;
      backoff = delay;
    }

    synchronized void decreaseDelay(long decreaseBy) {
      delay -= decreaseBy;
    }

    synchronized long delay() {
      return delay;
    }

    @Override
    public int compareTo(TimedResourceTask o) {
      return Long.compare(delay(), o.delay());
    }
  }
}
