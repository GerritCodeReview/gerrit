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

package com.google.gerrit.server.git;

import com.google.gerrit.server.LifecycleListener;
import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/** Delayed execution of tasks using a background thread pool. */
@Singleton
public class WorkQueue implements LifecycleListener{
  private Executor defaultQueue;
  private final CopyOnWriteArrayList<Executor> queues =
      new CopyOnWriteArrayList<Executor>();

  /** Get the default work queue, for miscellaneous tasks. */
  public synchronized Executor getDefaultQueue() {
    if (defaultQueue == null) {
      defaultQueue = createQueue(1, "WorkQueue");
    }
    return defaultQueue;
  }

  /** Create a new executor queue with one thread. */
  public Executor createQueue(final int poolsize, final String prefix) {
    final Executor r = new Executor(poolsize, prefix);
    r.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    r.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    queues.add(r);
    return r;
  }

  /** Get all of the tasks currently scheduled in any work queue. */
  public List<Task<?>> getTasks() {
    final List<Task<?>> r = new ArrayList<Task<?>>();
    for (final Executor e : queues) {
      e.addAllTo(r);
    }
    return r;
  }

  public void start() {
  }

  /** Shutdown all queues, aborting any pending tasks that haven't started. */
  public void stop() {
    for (final Executor p : queues) {
      p.shutdown();
      boolean isTerminated;
      do {
        try {
          isTerminated = p.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
          isTerminated = false;
        }
      } while (!isTerminated);
    }
    queues.clear();
  }

  /** An isolated queue. */
  public static class Executor extends ScheduledThreadPoolExecutor {
    private final Set<Task<?>> active = new HashSet<Task<?>>();

    Executor(final int corePoolSize, final String prefix) {
      super(corePoolSize, new ThreadFactory() {
        private final ThreadFactory parent = Executors.defaultThreadFactory();
        private final AtomicInteger tid = new AtomicInteger(1);

        @Override
        public Thread newThread(final Runnable task) {
          final Thread t = parent.newThread(task);
          t.setName(prefix + "-thread-" + tid.getAndIncrement());
          return t;
        }
      });
    }

    @Override
    protected <V> RunnableScheduledFuture<V> decorateTask(
        final Runnable runnable, final RunnableScheduledFuture<V> task) {
      return new Task<V>(runnable, super.decorateTask(runnable, task));
    }

    @Override
    protected <V> RunnableScheduledFuture<V> decorateTask(
        final Callable<V> callable, final RunnableScheduledFuture<V> task) {
      throw new UnsupportedOperationException("Callable not implemented");
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
      super.beforeExecute(t, r);
      synchronized (active) {
        active.add((Task<?>) r);
      }
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
      super.afterExecute(r, t);
      synchronized (active) {
        active.remove(r);
      }
    }

    void addAllTo(final List<Task<?>> list) {
      synchronized (active) {
        list.addAll(active);
      }
      for (final Runnable task : getQueue()) { // iterator is thread safe
        list.add((Task<?>) task);
      }
    }
  }

  /** A wrapper around a scheduled Runnable, as maintained in the queue. */
  public static class Task<V> implements RunnableScheduledFuture<V> {
    /**
     * Summarized status of a single task.
     * <p>
     * Tasks have the following state flow:
     * <ol>
     * <li>{@link #SLEEPING}: if scheduled with a non-zero delay.</li>
     * <li>{@link #READY}: waiting for an available worker thread.</li>
     * <li>{@link #RUNNING}: actively executing on a worker thread.</li>
     * <li>{@link #DONE}: finished executing, if not periodic.</li>
     * </ol>
     */
    public static enum State {
      // Ordered like this so ordinal matches the order we would
      // prefer to see tasks sorted in: done before running,
      // running before ready, ready before sleeping.
      //
      DONE, CANCELLED, RUNNING, READY, SLEEPING, OTHER;
    }

    private final Runnable runnable;
    private final RunnableScheduledFuture<V> task;
    private volatile boolean running;

    Task(Runnable runnable, RunnableScheduledFuture<V> task) {
      this.runnable = runnable;
      this.task = task;
    }

    /** Get the Runnable this task executes. */
    public Runnable getRunnable() {
      return runnable;
    }

    public State getState() {
      if (isDone() && !isPeriodic()) {
        return State.DONE;
      } else if (isRunning()) {
        return State.RUNNING;
      } else if (isCancelled()) {
        return State.CANCELLED;
      }

      final long delay = getDelay(TimeUnit.MILLISECONDS);
      if (delay <= 0) {
        return State.READY;
      } else if (0 < delay) {
        return State.SLEEPING;
      }

      return State.OTHER;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
      return task.cancel(mayInterruptIfRunning);
    }

    public int compareTo(Delayed o) {
      return task.compareTo(o);
    }

    public V get() throws InterruptedException, ExecutionException {
      return task.get();
    }

    public V get(long timeout, TimeUnit unit) throws InterruptedException,
        ExecutionException, TimeoutException {
      return task.get(timeout, unit);
    }

    public long getDelay(TimeUnit unit) {
      return task.getDelay(unit);
    }

    public boolean isCancelled() {
      return task.isCancelled();
    }

    public boolean isRunning() {
      return running;
    }

    public boolean isDone() {
      return task.isDone();
    }

    public boolean isPeriodic() {
      return task.isPeriodic();
    }

    public void run() {
      try {
        running = true;
        task.run();
      } finally {
        running = false;
      }
    }

    @Override
    public String toString() {
      return runnable.toString();
    }
  }
}
