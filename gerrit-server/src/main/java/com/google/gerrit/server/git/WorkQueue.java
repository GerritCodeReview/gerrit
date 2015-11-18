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

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.util.IdGenerator;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Delayed execution of tasks using a background thread pool. */
@Singleton
public class WorkQueue {
  public static class Lifecycle implements LifecycleListener {
    private final WorkQueue workQueue;

    @Inject
    Lifecycle(final WorkQueue workQeueue) {
      this.workQueue = workQeueue;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
      workQueue.stop();
    }
  }

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      bind(WorkQueue.class);
      listener().to(Lifecycle.class);
    }
  }

  private static final Logger log = LoggerFactory.getLogger(WorkQueue.class);
  private static final UncaughtExceptionHandler LOG_UNCAUGHT_EXCEPTION =
      new UncaughtExceptionHandler() {
        @Override
        public void uncaughtException(Thread t, Throwable e) {
          log.error("WorkQueue thread " + t.getName() + " threw exception", e);
        }
      };

  private Executor defaultQueue;
  private int defaultQueueSize;
  private final IdGenerator idGenerator;
  private final CopyOnWriteArrayList<Executor> queues;

  @Inject
  WorkQueue(IdGenerator idGenerator, @GerritServerConfig Config cfg) {
    this(idGenerator, cfg.getInt("execution", "defaultThreadPoolSize", 1));
  }

  public WorkQueue(IdGenerator idGenerator, int defaultThreadPoolSize) {
    this.idGenerator = idGenerator;
    this.queues = new CopyOnWriteArrayList<>();
    this.defaultQueueSize = defaultThreadPoolSize;
  }

  /** Get the default work queue, for miscellaneous tasks. */
  public synchronized Executor getDefaultQueue() {
    if (defaultQueue == null) {
      defaultQueue = createQueue(defaultQueueSize, "WorkQueue");
    }
    return defaultQueue;
  }

  /** Create a new executor queue. */
  public Executor createQueue(int poolsize, String prefix) {
    final Executor r = new Executor(poolsize, prefix);
    r.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    r.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    queues.add(r);
    return r;
  }

  /** Get all of the tasks currently scheduled in any work queue. */
  public List<Task<?>> getTasks() {
    final List<Task<?>> r = new ArrayList<>();
    for (final Executor e : queues) {
      e.addAllTo(r);
    }
    return r;
  }

  public <T> List<T> getTaskInfos(TaskInfoFactory<T> factory) {
    List<T> taskInfos = Lists.newArrayList();
    for (Executor exe : queues) {
      for (Task<?> task : exe.getTasks()) {
        taskInfos.add(factory.getTaskInfo(task));
      }
    }
    return taskInfos;
  }

  /** Locate a task by its unique id, null if no task matches. */
  public Task<?> getTask(final int id) {
    Task<?> result = null;
    for (final Executor e : queues) {
      final Task<?> t = e.getTask(id);
      if (t != null) {
        if (result != null) {
          // Don't return the task if we have a duplicate. Lie instead.
          return null;
        } else {
          result = t;
        }
      }
    }
    return result;
  }

  private void stop() {
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
  public class Executor extends ScheduledThreadPoolExecutor {
    private final ConcurrentHashMap<Integer, Task<?>> all;

    Executor(final int corePoolSize, final String prefix) {
      super(corePoolSize, new ThreadFactory() {
        private final ThreadFactory parent = Executors.defaultThreadFactory();
        private final AtomicInteger tid = new AtomicInteger(1);

        @Override
        public Thread newThread(final Runnable task) {
          final Thread t = parent.newThread(task);
          t.setName(prefix + "-" + tid.getAndIncrement());
          t.setUncaughtExceptionHandler(LOG_UNCAUGHT_EXCEPTION);
          return t;
        }
      });

      all = new ConcurrentHashMap<>( //
          corePoolSize << 1, // table size
          0.75f, // load factor
          corePoolSize + 4 // concurrency level
          );
    }

    public void unregisterWorkQueue() {
      queues.remove(this);
    }

    @Override
    protected <V> RunnableScheduledFuture<V> decorateTask(
        final Runnable runnable, RunnableScheduledFuture<V> r) {
      r = super.decorateTask(runnable, r);
      for (;;) {
        final int id = idGenerator.next();

        Task<V> task;

        if (runnable instanceof ProjectRunnable) {
          task = new ProjectTask<>((ProjectRunnable) runnable, r, this, id);
        } else {
          task = new Task<>(runnable, r, this, id);
        }

        if (all.putIfAbsent(task.getTaskId(), task) == null) {
          return task;
        }
      }
    }

    @Override
    protected <V> RunnableScheduledFuture<V> decorateTask(
        final Callable<V> callable, final RunnableScheduledFuture<V> task) {
      throw new UnsupportedOperationException("Callable not implemented");
    }

    void remove(final Task<?> task) {
      all.remove(task.getTaskId(), task);
    }

    Task<?> getTask(final int id) {
      return all.get(id);
    }

    void addAllTo(final List<Task<?>> list) {
      list.addAll(all.values()); // iterator is thread safe
    }

    Collection<Task<?>> getTasks() {
      return all.values();
    }
  }

  /** Runnable needing to know it was canceled. */
  public interface CancelableRunnable extends Runnable {
    /** Notifies the runnable it was canceled. */
    void cancel();
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
      DONE, CANCELLED, RUNNING, READY, SLEEPING, OTHER
    }

    private final Runnable runnable;
    private final RunnableScheduledFuture<V> task;
    private final Executor executor;
    private final int taskId;
    private final AtomicBoolean running;
    private final Date startTime;

    Task(Runnable runnable, RunnableScheduledFuture<V> task, Executor executor,
        int taskId) {
      this.runnable = runnable;
      this.task = task;
      this.executor = executor;
      this.taskId = taskId;
      this.running = new AtomicBoolean();
      this.startTime = new Date();
    }

    public int getTaskId() {
      return taskId;
    }

    public State getState() {
      if (isCancelled()) {
        return State.CANCELLED;
      } else if (isDone() && !isPeriodic()) {
        return State.DONE;
      } else if (running.get()) {
        return State.RUNNING;
      }

      final long delay = getDelay(TimeUnit.MILLISECONDS);
      if (delay <= 0) {
        return State.READY;
      } else {
        return State.SLEEPING;
      }
    }

    public Date getStartTime() {
      return startTime;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      if (task.cancel(mayInterruptIfRunning)) {
        // Tiny abuse of running: if the task needs to know it was
        // canceled (to clean up resources) and it hasn't started
        // yet the task's run method won't execute. So we tag it
        // as running and allow it to clean up. This ensures we do
        // not invoke cancel twice.
        //
        if (runnable instanceof CancelableRunnable
            && running.compareAndSet(false, true)) {
          ((CancelableRunnable) runnable).cancel();
        }
        executor.remove(this);
        executor.purge();
        return true;

      } else {
        return false;
      }
    }

    @Override
    public int compareTo(Delayed o) {
      return task.compareTo(o);
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
      return task.get();
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException,
        ExecutionException, TimeoutException {
      return task.get(timeout, unit);
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return task.getDelay(unit);
    }

    @Override
    public boolean isCancelled() {
      return task.isCancelled();
    }

    @Override
    public boolean isDone() {
      return task.isDone();
    }

    @Override
    public boolean isPeriodic() {
      return task.isPeriodic();
    }

    @Override
    public void run() {
      if (running.compareAndSet(false, true)) {
        try {
          task.run();
        } finally {
          if (isPeriodic()) {
            running.set(false);
          } else {
            executor.remove(this);
          }
        }
      }
    }

    @Override
    public String toString() {
      //This is a workaround to be able to print a proper name when the task
      //is wrapped into a ListenableFutureTask.
      if (runnable instanceof ListenableFutureTask<?>) {
        String errorMessage;
        try {
          for (Field field : ListenableFutureTask.class.getSuperclass()
              .getDeclaredFields()) {
            if (field.getType().isAssignableFrom(Callable.class)) {
              field.setAccessible(true);
              return ((Callable<?>) field.get(runnable)).toString();
            }
          }
          errorMessage = "Cannot find wrapped Callable field";
        } catch (SecurityException | IllegalArgumentException
            | IllegalAccessException e) {
          errorMessage = "Cannot call toString on Callable field";
        }
        log.debug("Cannot get a proper name for ListenableFutureTask: {}",
            errorMessage);
      }
      return runnable.toString();
    }
  }

  /**
   * Same as Task class, but with a reference to ProjectRunnable, used to
   * retrieve the project name from the operation queued
   **/
  public static class ProjectTask<V> extends Task<V> implements ProjectRunnable {

    private final ProjectRunnable runnable;

    ProjectTask(ProjectRunnable runnable, RunnableScheduledFuture<V> task,
        Executor executor, int taskId) {
      super(runnable, task, executor, taskId);
      this.runnable = runnable;
    }

    @Override
    public Project.NameKey getProjectNameKey() {
      return runnable.getProjectNameKey();
    }

    @Override
    public String getRemoteName() {
      return runnable.getRemoteName();
    }

    @Override
    public boolean hasCustomizedPrint() {
      return runnable.hasCustomizedPrint();
    }
  }
}
