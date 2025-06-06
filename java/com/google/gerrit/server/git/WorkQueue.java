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

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.stream.Collectors.toList;

import com.google.common.base.CaseFormat;
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicMap;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.eclipse.jgit.lib.Config;

/** Delayed execution of tasks using a background thread pool. */
@Singleton
public class WorkQueue {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * To register a TaskListener, which will be called directly before Tasks run, and directly after
   * they complete, bind the TaskListener like this:
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

  /**
   * Register a TaskParker from a plugin like this:
   *
   * <p>bind(TaskListener.class).annotatedWith(Exports.named("MyParker")).to(MyParker.class);
   */
  public interface TaskParker extends TaskListener {
    class NoOp extends TaskListener.NoOp implements TaskParker {
      @Override
      public boolean isReadyToStart(Task<?> task) {
        return true;
      }

      @Override
      public void onNotReadyToStart(Task<?> task) {}
    }

    /**
     * Determine whether a {@link Task} is ready to run or whether it should get parked.
     *
     * <p>Tasks that are not ready to run will get parked and will not run until all {@link
     * TaskParker}s return {@code true} from this method for the {@link Task}. This method may be
     * called more than once, but will always be followed by a call to {@link
     * #onNotReadyToStart(Task)} before being called again.
     *
     * <p>Resources should be acquired in this method via non-blocking means to avoid delaying the
     * executor from calling {@link #onNotReadyToStart(Task)} on other {@link TaskParker}s holding
     * resources.
     *
     * @param task the {@link Task} being considered for starting/parking
     * @return a boolean indicating if the given {@link Task} is ready to run ({@code true}) or
     *     should be parked ({@code false})
     */
    boolean isReadyToStart(Task<?> task);

    /**
     * This method will be called after this {@link TaskParker} returns {@code true} from {@link
     * #isReadyToStart(Task)} and another {@link TaskParker} returns {@code false}, thus preventing
     * the start.
     *
     * <p>Implementors should use this method to free any resources acquired in {@link
     * #isReadyToStart(Task)} based on the expectation that the task would start. Those resources
     * can be re-acquired when {@link #isReadyToStart(Task)} is called again later.
     *
     * @param task the {@link Task} that was prevented from starting by another {@link TaskParker}
     */
    void onNotReadyToStart(Task<?> task);
  }

  public static class Lifecycle implements LifecycleListener {
    private final WorkQueue workQueue;

    @Inject
    Lifecycle(WorkQueue workQueue) {
      this.workQueue = workQueue;
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
      DynamicMap.mapOf(binder(), WorkQueue.TaskListener.class);
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
  @Nullable
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

  @Nullable
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
    private class ParkedTask implements Comparable<ParkedTask> {
      public final CancellableCountDownLatch latch = new CancellableCountDownLatch(1);
      public final Task<?> task;
      private final Long priority = priorityGenerator.getAndIncrement();

      public ParkedTask(Task<?> task) {
        this.task = task;
      }

      @Override
      public int compareTo(ParkedTask o) {
        return priority.compareTo(o.priority);
      }

      /**
       * Cancel a parked {@link Task}.
       *
       * <p>Tasks awaiting in {@link #onStart(Task)} to be un-parked can be interrupted using this
       * method.
       */
      public void cancel() {
        latch.cancel();
      }

      public boolean isEqualTo(Task<?> task) {
        return this.task.taskId == task.taskId;
      }
    }

    private class CancellableCountDownLatch extends CountDownLatch {
      protected volatile boolean cancelled = false;

      public CancellableCountDownLatch(int count) {
        super(count);
      }

      /**
       * Unblocks threads which are waiting until the latch has counted down to zero.
       *
       * <p>If the current count is zero, then this method returns immediately.
       *
       * <p>If the current count is greater than zero, then it decrements until the count reaches
       * zero and causes all threads waiting on the latch using {@link CountDownLatch#await()} to
       * throw an {@link InterruptedException}.
       */
      public void cancel() {
        if (getCount() == 0) {
          return;
        }
        this.cancelled = true;
        while (getCount() > 0) {
          countDown();
        }
      }

      @Override
      public void await() throws InterruptedException {
        super.await();
        if (cancelled) {
          throw new InterruptedException();
        }
      }
    }

    private final ConcurrentHashMap<Integer, Task<?>> all;
    private final ConcurrentHashMap<Runnable, Long> nanosPeriodByRunnable;
    private final String queueName;
    private final AtomicLong priorityGenerator = new AtomicLong();
    private final PriorityBlockingQueue<ParkedTask> parked = new PriorityBlockingQueue<>();

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
              t.setUncaughtExceptionHandler(WorkQueue::handleUncaughtException);
              return t;
            }
          });

      all =
          new ConcurrentHashMap<>( //
              corePoolSize << 1, // table size
              0.75f, // load factor
              corePoolSize + 4 // concurrency level
              );
      nanosPeriodByRunnable = new ConcurrentHashMap<>(1, 0.75f, 1);
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
      nanosPeriodByRunnable.put(command, unit.toNanos(period));
      return super.scheduleAtFixedRate(LoggingContext.copy(command), initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable command, long initialDelay, long delay, TimeUnit unit) {
      nanosPeriodByRunnable.put(command, unit.toNanos(delay));
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

      // Periodic Tasks may get rescheduled if the previous run has yet to fully complete (and thus
      // passed to decorateTask() more than once), and there is no need to redecorate them if they
      // are already decorated.
      if (runnable instanceof LoggingContextAwareRunnable) {
        Runnable unwrappedTask = ((LoggingContextAwareRunnable) runnable).unwrap();
        if (unwrappedTask instanceof Task<?>) {
          return r;
        }
      }

      long nanosPeriod = firstNonNull(nanosPeriodByRunnable.remove(runnable), 0L);
      for (; ; ) {
        final int id = idGenerator.next();

        Task<V> task;

        if (runnable instanceof LoggingContextAwareRunnable) {
          runnable = ((LoggingContextAwareRunnable) runnable).unwrap();
        }

        if (runnable instanceof ProjectRunnable) {
          task = new ProjectTask<>((ProjectRunnable) runnable, r, nanosPeriod, this, id);
        } else {
          task = new Task<>(runnable, r, nanosPeriod, this, id);
        }

        if (all.putIfAbsent(task.getTaskId(), task) == null) {
          return task;
        }
      }
    }

    @Override
    protected <V> RunnableScheduledFuture<V> decorateTask(
        Callable<V> callable, RunnableScheduledFuture<V> r) {
      FutureTask<V> ft = new FutureTask<>(callable);
      return decorateTask(ft, r);
    }

    void remove(Task<?> task) {
      boolean isRemoved = all.remove(task.getTaskId(), task);
      if (isRemoved && !listeners.isEmpty()) {
        cancelIfParked(task);
      }
    }

    void cancelIfParked(Task<?> task) {
      parked.stream().filter(p -> p.isEqualTo(task)).findFirst().ifPresent(ParkedTask::cancel);
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

    public void waitUntilReadyToStart(Task<?> task) {
      if (!listeners.isEmpty() && !isReadyToStart(task)) {
        ParkedTask parkedTask = new ParkedTask(task);
        parked.offer(parkedTask);
        task.runningState.set(Task.State.PARKED);
        incrementCorePoolSizeBy(1);
        try {
          parkedTask.latch.await();
        } catch (InterruptedException e) {
          logger.atSevere().withCause(e).log("Parked Task(%s) Interrupted", task);
          parked.remove(parkedTask);
        } finally {
          incrementCorePoolSizeBy(-1);
        }
      }
    }

    public void onStart(Task<?> task) {
      listeners.runEach(extension -> extension.get().onStart(task));
    }

    public void onStop(Task<?> task) {
      listeners.runEach(extension -> extension.get().onStop(task));
      updateParked();
    }

    protected boolean isReadyToStart(Task<?> task) {
      MutableBoolean isReady = new MutableBoolean(true);
      Set<TaskParker> readyParkers = new HashSet<>();
      listeners.runEach(
          extension -> {
            if (isReady.isTrue()) {
              TaskListener listener = extension.get();
              if (listener instanceof TaskParker) {
                TaskParker parker = (TaskParker) listener;
                if (parker.isReadyToStart(task)) {
                  readyParkers.add(parker);
                } else {
                  isReady.setFalse();
                }
              }
            }
          });

      if (isReady.isFalse()) {
        listeners.runEach(
            extension -> {
              TaskListener listener = extension.get();
              if (readyParkers.contains(listener)) {
                ((TaskParker) listener).onNotReadyToStart(task);
              }
            });
      }
      return isReady.getValue();
    }

    public void updateParked() {
      List<ParkedTask> notReady = new ArrayList<>();
      ParkedTask ready;

      while ((ready = parked.poll()) != null) {
        if (Task.State.CANCELLED.equals(ready.task.getState())) {
          ready.cancel(); // In case a cancelled task is polled before cleanup
        } else if (isReadyToStart(ready.task)) {
          break;
        } else if (Task.State.CANCELLED.equals(ready.task.getState())) {
          ready.cancel(); // In case the task is cancelled while evaluating isReadyToStart
        } else {
          notReady.add(ready);
        }
      }
      parked.addAll(notReady);

      if (ready != null) {
        ready.latch.countDown();
      }
    }

    public synchronized void incrementCorePoolSizeBy(int i) {
      super.setCorePoolSize(getCorePoolSize() + i);
    }

    @Override
    public synchronized void setCorePoolSize(int s) {
      super.setCorePoolSize(s);
    }
  }

  private static void handleUncaughtException(Thread t, Throwable e) {
    logger.atSevere().withCause(e).log("WorkQueue thread %s threw exception", t.getName());

    // Clear the logging context to prevent that it leaks into other threads.
    if (!LoggingContext.getInstance().isEmpty()) {
      logger.atInfo().log("Clearing LoggingContext after uncaught exception");
      LoggingContext.getInstance().clear();
    }
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
     *   <li>{@link #STARTING}: onStart() actively executing on a worker thread.
     *   <li>{@link #RUNNING}: actively executing on a worker thread.
     *   <li>{@link #STOPPING}: onStop() actively executing on a worker thread.
     *   <li>{@link #DONE}: finished executing, if not periodic.
     * </ol>
     */
    public enum State {
      // Ordered like this so ordinal matches the order we would
      // prefer to see tasks sorted in: done before running,
      // stopping before running, running before starting,
      // starting before parked, parked before ready, ready before sleeping.
      //
      DONE,
      CANCELLED,
      STOPPING,
      RUNNING,
      STARTING,
      PARKED,
      READY,
      SLEEPING,
      OTHER
    }

    private final Runnable runnable;
    private final RunnableScheduledFuture<V> task;
    private final Executor executor;
    private final int taskId;
    private final Instant startTime;
    private final long nanosPeriod;

    // runningState is non-null when listener or task code is running in an executor thread
    private final AtomicReference<State> runningState = new AtomicReference<>();

    Task(
        Runnable runnable,
        RunnableScheduledFuture<V> task,
        long nanosPeriod,
        Executor executor,
        int taskId) {
      this.runnable = runnable;
      this.task = task;
      this.nanosPeriod = nanosPeriod;
      this.executor = executor;
      this.taskId = taskId;
      this.startTime = Instant.now();
    }

    public int getTaskId() {
      return taskId;
    }

    public State getState() {
      if (isCancelled()) {
        return State.CANCELLED;
      }

      State r = runningState.get();
      if (r != null) {
        return r;
      } else if (isDone() && !isPeriodic()) {
        return State.DONE;
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
    @CanIgnoreReturnValue
    public boolean cancel(boolean mayInterruptIfRunning) {
      if (task.cancel(mayInterruptIfRunning)) {
        // Tiny abuse of runningState: if the task needs to know it
        // was canceled (to clean up resources) and it hasn't started
        // yet the task's run method won't execute. So we tag it
        // as running and allow it to clean up. This ensures we do
        // not invoke cancel twice.
        //
        if (runnable instanceof CancelableRunnable) {
          if (runningState.compareAndSet(null, State.RUNNING)) {
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
      if (runningState.compareAndSet(null, State.READY)) {
        String oldThreadName = Thread.currentThread().getName();
        try {
          Thread.currentThread().setName(oldThreadName + "[" + this + "]");
          executor.waitUntilReadyToStart(this); // Transitions to PARKED while not ready to start
          runningState.set(State.STARTING);
          executor.onStart(this);
          runningState.set(State.RUNNING);
          task.run();
        } finally {
          Thread.currentThread().setName(oldThreadName);
          runningState.set(State.STOPPING);
          executor.onStop(this);
          if (isPeriodic()) {
            runningState.set(null);
          } else {
            runningState.set(State.DONE);
            executor.remove(this);
          }
        }
      } else {
        Future<?> unusedFuture = executor.schedule(this, nanosPeriod / 3, TimeUnit.NANOSECONDS);
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
        ProjectRunnable runnable,
        RunnableScheduledFuture<V> task,
        long nanosPeriod,
        Executor executor,
        int taskId) {
      super(runnable, task, nanosPeriod, executor, taskId);
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
