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

import static java.util.stream.Collectors.toList;

import com.google.common.base.CaseFormat;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.ScheduleConfig.Schedule;
import com.google.gerrit.server.logging.LoggingContext;
import com.google.gerrit.server.logging.LoggingContextAwareRunnable;
import com.google.gerrit.server.plugincontext.PluginMapContext;
import com.google.gerrit.server.util.IdGenerator;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.lib.Config;

/** Delayed execution of tasks using a background thread pool. */
@Singleton
public class WorkQueue {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * To register a TaskListener, which will get called right before and after a Task runs, bind it
   * like this:
   *
   * <p><code>
   *   bind(TaskListener.class)
   *       .annotatedWith(Exports.named("MyListener"))
   *       .to(MyListener.class);
   * </code>
   */
  public interface TaskListener {
    public static class NoOp implements TaskListener {
      @Override
      public void onStart(Task<?> task) {}

      @Override
      public void onStop(Task<?> task) {}
    }

    void onStart(Task<?> task);

    void onStop(Task<?> task);
  }

  public static class Lifecycle implements LifecycleListener {
    private final WorkQueue workQueue;

    @Inject
    Lifecycle(WorkQueue workQeueue) {
      this.workQueue = workQeueue;
    }

    @Override
    public void start() {}

    @Override
    public void stop() {
      workQueue.stop();
    }
  }

  public static class WorkQueueModule extends LifecycleModule {
    @Override
    protected void configure() {
      bind(WorkQueue.class);
      listener().to(Lifecycle.class);
    }
  }

  private final ScheduledExecutorService defaultQueue;
  private final IdGenerator idGenerator;
  private final MetricMaker metrics;
  private final CopyOnWriteArrayList<Executor> queues;
  private final PluginMapContext<TaskListener> listeners;

  @Inject
  WorkQueue(
      IdGenerator idGenerator,
      @GerritServerConfig Config cfg,
      MetricMaker metrics,
      PluginMapContext<TaskListener> listeners) {
    this(
        idGenerator,
        Math.max(cfg.getInt("execution", "defaultThreadPoolSize", 2), 2),
        metrics,
        listeners);
  }

  /** Constructor to allow binding the WorkQueue more explicitly in a vhost setup. */
  public WorkQueue(
      IdGenerator idGenerator,
      int defaultThreadPoolSize,
      MetricMaker metrics,
      PluginMapContext<TaskListener> listeners) {
    this.idGenerator = idGenerator;
    this.metrics = metrics;
    this.queues = new CopyOnWriteArrayList<>();
    this.defaultQueue = createQueue(defaultThreadPoolSize, "WorkQueue", true);
    this.listeners = listeners;
  }

  /** Get the default work queue, for miscellaneous tasks. */
  public ScheduledExecutorService getDefaultQueue() {
    return defaultQueue;
  }

  /**
   * Create a new executor queue.
   *
   * <p>Creates a new executor queue without associated metrics. This method is suitable for use by
   * plugins.
   *
   * <p>If metrics are needed, use {@link #createQueue(int, String, int, boolean)} instead.
   *
   * @param poolsize the size of the pool.
   * @param queueName the name of the queue.
   */
  public ScheduledExecutorService createQueue(int poolsize, String queueName) {
    return createQueue(poolsize, queueName, Thread.NORM_PRIORITY, false);
  }

  /**
   * Create a new executor queue, with default priority, optionally with metrics.
   *
   * <p>Creates a new executor queue, optionally with associated metrics. Metrics should not be
   * requested for queues created by plugins.
   *
   * @param poolsize the size of the pool.
   * @param queueName the name of the queue.
   * @param withMetrics whether to create metrics.
   */
  public ScheduledThreadPoolExecutor createQueue(
      int poolsize, String queueName, boolean withMetrics) {
    return createQueue(poolsize, queueName, Thread.NORM_PRIORITY, withMetrics);
  }

  /**
   * Create a new executor queue, optionally with metrics.
   *
   * <p>Creates a new executor queue, optionally with associated metrics. Metrics should not be
   * requested for queues created by plugins.
   *
   * @param poolsize the size of the pool.
   * @param queueName the name of the queue.
   * @param threadPriority thread priority.
   * @param withMetrics whether to create metrics.
   */
  @SuppressWarnings("ThreadPriorityCheck")
  public ScheduledThreadPoolExecutor createQueue(
      int poolsize, String queueName, int threadPriority, boolean withMetrics) {
    Executor executor = new Executor(poolsize, queueName);
    if (withMetrics) {
      logger.atInfo().log("Adding metrics for '%s' queue", queueName);
      executor.buildMetrics(queueName);
    }
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(true);
    queues.add(executor);
    if (threadPriority != Thread.NORM_PRIORITY) {
      ThreadFactory parent = executor.getThreadFactory();
      executor.setThreadFactory(
          task -> {
            Thread t = parent.newThread(task);
            t.setPriority(threadPriority);
            return t;
          });
    }

    return executor;
  }

  /** Executes a periodic command at a fixed schedule on the default queue. */
  public void scheduleAtFixedRate(Runnable command, Schedule schedule) {
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError =
        getDefaultQueue()
            .scheduleAtFixedRate(
                command, schedule.initialDelay(), schedule.interval(), TimeUnit.MILLISECONDS);
  }

  /** Get all of the tasks currently scheduled in any work queue. */
  public List<Task<?>> getTasks() {
    final List<Task<?>> r = new ArrayList<>();
    for (Executor e : queues) {
      e.addAllTo(r);
    }
    return r;
  }

  public <T> List<T> getTaskInfos(TaskInfoFactory<T> factory) {
    List<T> taskInfos = new ArrayList<>();
    for (Executor exe : queues) {
      for (Task<?> task : exe.getTasks()) {
        taskInfos.add(factory.getTaskInfo(task));
      }
    }
    return taskInfos;
  }

  /** Locate a task by its unique id, null if no task matches. */
  public Task<?> getTask(int id) {
    Task<?> result = null;
    for (Executor e : queues) {
      final Task<?> t = e.getTask(id);
      if (t != null) {
        if (result != null) {
          // Don't return the task if we have a duplicate. Lie instead.
          return null;
        }
        result = t;
      }
    }
    return result;
  }

  public ScheduledThreadPoolExecutor getExecutor(String queueName) {
    for (Executor e : queues) {
      if (e.queueName.equals(queueName)) {
        return e;
      }
    }
    return null;
  }

  private void stop() {
    for (Executor p : queues) {
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
  private class Executor extends ScheduledThreadPoolExecutor {
    private final ConcurrentHashMap<Integer, Task<?>> all;
    private final String queueName;

    Executor(int corePoolSize, final String queueName) {
      super(
          corePoolSize,
          new ThreadFactory() {
            private final ThreadFactory parent = Executors.defaultThreadFactory();
            private final AtomicInteger tid = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable task) {
              final Thread t = parent.newThread(task);
              t.setName(queueName + "-" + tid.getAndIncrement());
              t.setUncaughtExceptionHandler(WorkQueue::logUncaughtException);
              return t;
            }
          });

      all =
          new ConcurrentHashMap<>( //
              corePoolSize << 1, // table size
              0.75f, // load factor
              corePoolSize + 4 // concurrency level
              );
      this.queueName = queueName;
    }

    @Override
    public void execute(Runnable command) {
      super.execute(LoggingContext.copy(command));
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
      return super.submit(LoggingContext.copy(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
      return super.submit(LoggingContext.copy(task), result);
    }

    @Override
    public Future<?> submit(Runnable task) {
      return super.submit(LoggingContext.copy(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
        throws InterruptedException {
      return super.invokeAll(tasks.stream().map(LoggingContext::copy).collect(toList()));
    }

    @Override
    public <T> List<Future<T>> invokeAll(
        Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
        throws InterruptedException {
      return super.invokeAll(
          tasks.stream().map(LoggingContext::copy).collect(toList()), timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException {
      return super.invokeAny(tasks.stream().map(LoggingContext::copy).collect(toList()));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      return super.invokeAny(
          tasks.stream().map(LoggingContext::copy).collect(toList()), timeout, unit);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
      return super.schedule(LoggingContext.copy(command), delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
      return super.schedule(LoggingContext.copy(callable), delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
        Runnable command, long initialDelay, long period, TimeUnit unit) {
      return super.scheduleAtFixedRate(LoggingContext.copy(command), initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable command, long initialDelay, long delay, TimeUnit unit) {
      return super.scheduleWithFixedDelay(LoggingContext.copy(command), initialDelay, delay, unit);
    }

    @Override
    protected void terminated() {
      super.terminated();
      queues.remove(this);
    }

    private void buildMetrics(String queueName) {
      metrics.newCallbackMetric(
          getMetricName(queueName, "max_pool_size"),
          Long.class,
          new Description("Maximum allowed number of threads in the pool")
              .setGauge()
              .setUnit("threads"),
          () -> (long) getMaximumPoolSize());
      metrics.newCallbackMetric(
          getMetricName(queueName, "pool_size"),
          Long.class,
          new Description("Current number of threads in the pool").setGauge().setUnit("threads"),
          () -> (long) getPoolSize());
      metrics.newCallbackMetric(
          getMetricName(queueName, "active_threads"),
          Long.class,
          new Description("Number number of threads that are actively executing tasks")
              .setGauge()
              .setUnit("threads"),
          () -> (long) getActiveCount());
      metrics.newCallbackMetric(
          getMetricName(queueName, "scheduled_tasks"),
          Integer.class,
          new Description("Number of scheduled tasks in the queue").setGauge().setUnit("tasks"),
          () -> getQueue().size());
      metrics.newCallbackMetric(
          getMetricName(queueName, "total_scheduled_tasks_count"),
          Long.class,
          new Description("Total number of tasks that have been scheduled for execution")
              .setCumulative()
              .setUnit("tasks"),
          this::getTaskCount);
      metrics.newCallbackMetric(
          getMetricName(queueName, "total_completed_tasks_count"),
          Long.class,
          new Description("Total number of tasks that have completed execution")
              .setCumulative()
              .setUnit("tasks"),
          this::getCompletedTaskCount);
    }

    private String getMetricName(String queueName, String metricName) {
      String name =
          CaseFormat.UPPER_CAMEL.to(
              CaseFormat.LOWER_UNDERSCORE, queueName.replaceFirst("SSH", "Ssh").replace("-", ""));
      return metrics.sanitizeMetricName(String.format("queue/%s/%s", name, metricName));
    }

    @Override
    protected <V> RunnableScheduledFuture<V> decorateTask(
        Runnable runnable, RunnableScheduledFuture<V> r) {
      r = super.decorateTask(runnable, r);
      for (; ; ) {
        final int id = idGenerator.next();

        Task<V> task;

        if (runnable instanceof LoggingContextAwareRunnable) {
          runnable = ((LoggingContextAwareRunnable) runnable).unwrap();
        }

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
        Callable<V> callable, RunnableScheduledFuture<V> task) {
      throw new UnsupportedOperationException("Callable not implemented");
    }

    void remove(Task<?> task) {
      all.remove(task.getTaskId(), task);
    }

    Task<?> getTask(int id) {
      return all.get(id);
    }

    void addAllTo(List<Task<?>> list) {
      list.addAll(all.values()); // iterator is thread safe
    }

    Collection<Task<?>> getTasks() {
      return all.values();
    }

    public void onStart(Task<?> task) {
      listeners.runEach(extension -> extension.getProvider().get().onStart(task));
    }

    public void onStop(Task<?> task) {
      listeners.runEach(extension -> extension.getProvider().get().onStop(task));
    }
  }

  private static void logUncaughtException(Thread t, Throwable e) {
    logger.atSevere().withCause(e).log("WorkQueue thread %s threw exception", t.getName());
  }

  /**
   * Runnable needing to know it was canceled. Note that cancel is called only in case the task is
   * not in progress already.
   */
  public interface CancelableRunnable extends Runnable {
    /** Notifies the runnable it was canceled. */
    void cancel();
  }

  /**
   * Base interface handles the case when task was canceled before actual execution and in case it
   * was started cancel method is not called yet the task itself will be destroyed anyway (it will
   * result in resource opening errors). This interface gives a chance to implementing classes for
   * handling such scenario and act accordingly.
   */
  public interface CanceledWhileRunning extends CancelableRunnable {
    /** Notifies the runnable it was canceled during execution. * */
    void setCanceledWhileRunning();
  }

  /** A wrapper around a scheduled Runnable, as maintained in the queue. */
  public static class Task<V> implements RunnableScheduledFuture<V> {
    /**
     * Summarized status of a single task.
     *
     * <p>Tasks have the following state flow:
     *
     * <ol>
     *   <li>{@link #SLEEPING}: if scheduled with a non-zero delay.
     *   <li>{@link #READY}: waiting for an available worker thread.
     *   <li>{@link #RUNNING}: actively executing on a worker thread.
     *   <li>{@link #DONE}: finished executing, if not periodic.
     * </ol>
     */
    public enum State {
      // Ordered like this so ordinal matches the order we would
      // prefer to see tasks sorted in: done before running,
      // running before ready, ready before sleeping.
      //
      DONE,
      CANCELLED,
      RUNNING,
      READY,
      SLEEPING,
      OTHER
    }

    private final Runnable runnable;
    private final RunnableScheduledFuture<V> task;
    private final Executor executor;
    private final int taskId;
    private final AtomicBoolean running;
    private final Instant startTime;

    Task(Runnable runnable, RunnableScheduledFuture<V> task, Executor executor, int taskId) {
      this.runnable = runnable;
      this.task = task;
      this.executor = executor;
      this.taskId = taskId;
      this.running = new AtomicBoolean();
      this.startTime = Instant.now();
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
      }
      return State.SLEEPING;
    }

    public Instant getStartTime() {
      return startTime;
    }

    public String getQueueName() {
      return executor.queueName;
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
        if (runnable instanceof CancelableRunnable) {
          if (running.compareAndSet(false, true)) {
            ((CancelableRunnable) runnable).cancel();
          } else if (runnable instanceof CanceledWhileRunning) {
            ((CanceledWhileRunning) runnable).setCanceledWhileRunning();
          }
        }
        if (runnable instanceof Future<?>) {
          // Creating new futures eventually passes through
          // AbstractExecutorService#schedule, which will convert the Guava
          // Future to a Runnable, thereby making it impossible for the
          // cancellation to propagate from ScheduledThreadPool's task back to
          // the Guava future, so kludge it here.
          ((Future<?>) runnable).cancel(mayInterruptIfRunning);
        }

        executor.remove(this);
        executor.purge();
        return true;
      }
      return false;
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
    public V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
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
        String oldThreadName = Thread.currentThread().getName();
        try {
          executor.onStart(this);
          Thread.currentThread().setName(oldThreadName + "[" + task.toString() + "]");
          task.run();
        } finally {
          Thread.currentThread().setName(oldThreadName);
          executor.onStop(this);
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
      // This is a workaround to be able to print a proper name when the task
      // is wrapped into a TrustedListenableFutureTask.
      try {
        if (runnable
            .getClass()
            .isAssignableFrom(
                Class.forName("com.google.common.util.concurrent.TrustedListenableFutureTask"))) {
          Class<?> trustedFutureInterruptibleTask =
              Class.forName(
                  "com.google.common.util.concurrent.TrustedListenableFutureTask$TrustedFutureInterruptibleTask");
          for (Field field : runnable.getClass().getDeclaredFields()) {
            if (field.getType().isAssignableFrom(trustedFutureInterruptibleTask)) {
              field.setAccessible(true);
              Object innerObj = field.get(runnable);
              if (innerObj != null) {
                for (Field innerField : innerObj.getClass().getDeclaredFields()) {
                  if (innerField.getType().isAssignableFrom(Callable.class)) {
                    innerField.setAccessible(true);
                    return innerField.get(innerObj).toString();
                  }
                }
              }
            }
          }
        }
      } catch (ClassNotFoundException | IllegalArgumentException | IllegalAccessException e) {
        logger.atFine().log(
            "Cannot get a proper name for TrustedListenableFutureTask: %s", e.getMessage());
      }
      return runnable.toString();
    }
  }

  /**
   * Same as Task class, but with a reference to ProjectRunnable, used to retrieve the project name
   * from the operation queued
   */
  public static class ProjectTask<V> extends Task<V> implements ProjectRunnable {

    private final ProjectRunnable runnable;

    ProjectTask(
        ProjectRunnable runnable, RunnableScheduledFuture<V> task, Executor executor, int taskId) {
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

    @Override
    public String toString() {
      return runnable.toString();
    }
  }
}
