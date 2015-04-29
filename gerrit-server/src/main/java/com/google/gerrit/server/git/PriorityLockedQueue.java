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

import com.google.gerrit.common.MultiLock;

import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * This class
 * @author sbeller
 *
 * @param <R>
 * @param <T>
 */
public abstract class PriorityLockedQueue<R, T extends PriorityLockedQueue.Task<R>> {
  public interface Task<R> {
    /** @return the resources required to be locked to perform this task. */
    Set<R> resources();
  }

  // Contains locks on all resources we are currently holding
  private MultiLock<R> locks;

  // all future tasks
  private PriorityQueue<TimedResourceTask> queue;

  private ScheduledExecutorService exec;

  // keeps track of the next wake up time
  private ScheduledFuture<?> nextWakeUp;

  PriorityLockedQueue(ScheduledExecutorService sched) {
    locks = new MultiLock<>();
    queue = new PriorityQueue<>(1);
    exec = sched;
  }

  /**
   * Checks if a task can be processed right now and if it can, it will do so.
   * If it cannot be processed right now, no further action will be taken and
   * false is returned.
   *
   * @return if the given item will be processed right now.
   */
  public synchronized boolean processTask(T task) {
    boolean ret = tryRunTask(task);
    // During processing some other scheduled task may have become ready
    // but blocked so check if we can run them now
    checkQueue();

    return ret;
  }

  /**
   * This will schedule a task to be processed in the future with a minimum
   * delay of {@code minimumDelay} milliseconds in the future.
   *
   * @param rtask
   */
  public synchronized void schedule(T rtask, final long minDelay) {
    queue.add(new TimedResourceTask(rtask, minDelay));
    scheduleQueueCheck(minDelay);
  }


  private synchronized boolean tryRunTask(T task) {
    if (locks.lock(task.resources())) {
      try {
        process(task);
      } finally {
        locks.unlock(task.resources());
      }
      return true;
    }
    return false;
  }

  private synchronized void decreaseTime(long decreaseBy) {
    for (TimedResourceTask t : queue) {
      t.decreaseDelay(decreaseBy);
    }
  }

  // schedules a check for a queue after delay,
  // if there is already a check scheduled the earlier check is kept, while the
  // later check is droped.
  private synchronized void scheduleQueueCheck(final long delay) {
    Runnable wakeUpAction = new Runnable() {
      @Override
      public void run() {
        nextWakeUp = null;
        decreaseTime(delay);
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
    TimedResourceTask task = queue.peek();
    while (task != null && task.delay() <= 0) {
      if (tryRunTask(task.task)) {
        queue.remove();
      } else {
        schedule(task.task, 1);
      }
      task = queue.peek();
    }

    TimedResourceTask nextTask = queue.peek();
    if (nextTask != null) {
      scheduleQueueCheck(nextTask.delay());
    }
  }

  /**
   * The actual work will be done inside this function.
   *
   * @param task
   */
  abstract void process(T task);

  private class TimedResourceTask implements PriorityLockedQueue.Task<R>, Comparable<TimedResourceTask> {
    private T task;
    private long delay;

    public TimedResourceTask(T task, long at) {
      this.task = task;
      this.delay = at;
    }

    @Override
    public Set<R> resources() {
      return task.resources();
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
