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

import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * This class schedules tasks requiring resources such that no tasks requiring
 * the same resource are scheduled at the same time.
 *
 * @param <R> The resource type to be required for the scheduled tasks.
 * @param <T> The task type to be scheduled.
 */
public abstract class PriorityLockedQueue<R, T extends PriorityLockedQueue.Task<R>> {
  public interface Task<R> {
    /** @return the resources required to be locked to perform this task. */
    Set<R> resources();
  }

  Set<T> currentTasks;

  // Contains locks on all resources we are currently holding
  protected MultiLock<R> locks;

  // all future tasks
  private PriorityQueue<TimedResourceTask> queue;

  private ScheduledExecutorService exec;

  // keeps track of the next wake up time
  private ScheduledFuture<?> nextWakeUp;

  private long timeout;

  PriorityLockedQueue(ScheduledExecutorService sched, long timeout) {
    this.locks = new MultiLock<>();
    this.queue = new PriorityQueue<>(1);
    this.exec = sched;
    this.timeout = timeout;
    this.currentTasks = Sets.newHashSet();
  }

  /**
   * Checks if a task can be processed right now and if it can, it will do so.
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
   * This will schedule a task to be processed in the future with a minimum
   * delay of {@code minimumDelay} milliseconds in the future.
   *
   * @param rtask the task be be run
   * @param minDelay the minimum delay in milliseconds when this task should be
   *        run.
   */
  public synchronized void schedule(T rtask, final long minDelay) {
    queue.add(new TimedResourceTask(rtask, minDelay));
    scheduleQueueCheck(minDelay);
  }


  /**
   *
   * @param timeout the timeout in milliseconds for the resources to be reserved
   *        if run asynchronously.
   */
  private boolean tryRunTask(final T task, boolean async, long timeout) {
    if (locks.lock(task.resources())) {
      currentTasks.add(task);
      try {
        if (!async) {
          processTask(task);
        } else {
          processAsyncTask(task);
        }
      } finally {
        if (!async) {
          currentTasks.remove(task);
          locks.unlock(task.resources());
        } else {
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
    return false;
  }

  /**
   * Schedules a check for a queue after {@code delay} milliseconds.
   * <p>
   * if there is already a check scheduled the earlier check is kept, while the
   * later check is dropped.
   *
   * @param delay The minimum amount of time in milliseconds after which the
   *              next wake up call will check the queue.
   */
  private synchronized void scheduleQueueCheck(final long delay) {
    Runnable wakeUpAction = new Runnable() {
      @Override
      public void run() {
        nextWakeUp = null;
        for (TimedResourceTask t : queue) {
          t.decreaseDelay(delay);
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
   * will be split out to another thread, and this may return early fast.
   *
   * If the task is done and its resources don't require a lock anymore,
   * a call to {@code processingAsyncTaskDone} should be made. If this call
   * is not made, it will be called internally after the timeout as specified in
   *
   *
   * @param task the task to be performed
   */
  abstract void processAsyncTask(T task);

  public synchronized void processingAsyncTaskDone(T task) {
    if (currentTasks.contains(task)) {
      currentTasks.remove(task);
      locks.unlock(task.resources());
      // TODO(sbeller): As this method is called twice (once as a timeout and
      // once supposedly by the finished worker. Maybe there is an efficient
      // way to cancel the later call from the first call.
    }
  }

  private class TimedResourceTask implements PriorityLockedQueue.Task<R>, Comparable<TimedResourceTask> {
    private T task;
    private long delay;
    private long backoff;

    public TimedResourceTask(T task, long delay) {
      this.task = task;
      this.delay = delay;
      this.backoff = delay;
    }

    @Override
    public Set<R> resources() {
      return task.resources();
    }

    void calculateNewDelay() {
      delay = 2 * backoff;
      backoff = delay;
    }

    void decreaseDelay(long decreaseBy) {
      delay -= decreaseBy;
    }

    long delay() {
      return delay;
    }

    @Override
    public int compareTo(TimedResourceTask o) {
      return Long.compare(delay(), o.delay());
    }
  }
}
