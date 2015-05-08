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

import com.google.common.collect.Maps;
import com.google.gerrit.common.MultiLock;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public abstract class PriorityLockedQueue<Resource> {

  // Contains locks on all resources we are currently holding
  private MultiLock<Resource> locks;

  // Contains the tasks which are currently processing
  private Map<ResourceTask<Resource>, Semaphore> currentBlockingTasks;

  private ScheduledExecutorService exec;

  // all future scheduled tasks
  private PriorityQueue<TimedRessourceTask> queue;

  // keeps track of the next wake up time
  private ScheduledFuture<?> nextWakeUp;
  private Runnable wakeUpAction;

  PriorityLockedQueue() {

  PriorityLockedQueue(ScheduledExecutorService sched) {
    Comparator<PriorityLockedQueue<Resource>.TimedRessourceTask> comparator =
        new TimedRessourceTaskComparator();
    locks = new MultiLock<>();
    currentBlockingTasks = Maps.newHashMap();
    queue = new PriorityQueue<>(1, comparator);
    exec = sched;
    wakeUpAction = new Runnable() {
      @Override
      public void run() {
        doWork();
      }
    };
  }

  /**
   * Checks if an item can be processed right now and if it can, it will do so.
   * If it cannot be processed right now, no further action will be taken and
   * false is returned.
   *
   * @return if the given item will be processed right now.
   */
  public synchronized boolean processItem(ResourceTask<Resource> task)
      throws InterruptedException {
    if (locks.lock(task.resources())) {
      currentBlockingTasks.put(task, new Semaphore(0));
      process(task);
      currentBlockingTasks.get(task).acquire();
      currentBlockingTasks.remove(task);
      return true;
    }
    return false;
  }

  /**
   * This will schedule an element to be processed in the future with a minimum
   * delay of {@code minimumDelay} milliseconds in the future.
   *
   * @param task
   */
  public synchronized void schedule(ResourceTask<Resource> task, long minDelay) {
    final long now = TimeUtil.nowMs();
    final long at = now + MILLISECONDS.convert(minDelay, TimeUnit.MILLISECONDS);

    queue.add(new TimedRessourceTask(task, at));
    if (nextWakeUp == null) {
      nextWakeUp = exec.schedule(wakeUpAction, minDelay, TimeUnit.MILLISECONDS);
    } else {
      if (minDelay < nextWakeUp.getDelay(TimeUnit.MILLISECONDS)) {
        nextWakeUp.cancel(false);
        nextWakeUp = exec.schedule(wakeUpAction, minDelay, TimeUnit.MILLISECONDS);
      }
    }
  }

  private synchronized void doWork() {
    TimedRessourceTask task = queue.poll();
    if (task == null) {
      // This should never happen as we schedule only wake up calls if there
      // is work to be done
      //TODO(sbeller): write to log
    } else {
      process(task);

      TimedRessourceTask nextTask = queue.peek();
      if (nextTask != null) {
        nextWakeUp = exec.schedule(wakeUpAction,
            nextTask.scheduledTime() - TimeUtil.nowMs(),
            TimeUnit.MILLISECONDS);
      }
    }
  }

  /**
   * The actual work will be done inside this function. As the tasks are
   * intended to be heavy workloads, this function must call be kept short and
   * hand over the task to some other thread pool.
   * Once the work is done, a call to {@code processed(Task task)} must be made
   * with the same task as given by parameter.
   *
   * @param task
   */
  abstract void process(ResourceTask<Resource> task);

  protected void processed(ResourceTask<Resource> task) {
    locks.unlock(task.resources());
    Semaphore sem = currentBlockingTasks.get(task);
    if (sem != null) {
      sem.release();
    }
  }

  private class TimedRessourceTask implements ResourceTask<Resource> {
    private final ResourceTask<Resource> task;
    private final long at;

    public TimedRessourceTask(ResourceTask<Resource> task, long at) {
      this.task = task;
      this.at = at;
    }

    @Override
    public Set<Resource> resources() {
      return task.resources();
    }

    long scheduledTime() {
      return at;
    }
  }

  private class TimedRessourceTaskComparator implements Comparator<TimedRessourceTask> {
    @Override
    public int compare(TimedRessourceTask x, TimedRessourceTask y) {
      if (x.scheduledTime() < y.scheduledTime()) {
        return -1;
      }
      if (x.scheduledTime() > y.scheduledTime()) {
        return +1;
      }
      return 0;
    }
  }
}
