package com.google.gerrit.server.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.server.logging.LoggingContext;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class WorkQueueIT {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private ScheduledThreadPoolExecutor testScheduledThreadPool;
  private int num = 0;

  @Test
  public void testScheduleAtFixedRate() {
    testScheduledThreadPool = new Executor(8);
    ScheduledFuture<?> unusedFuture =
        testScheduledThreadPool.scheduleAtFixedRate(
            new Runnable() {
              @Override
              public void run() {
                num++;
              }

              @Override
              public String toString() {
                return String.format("scheduleAtFixedRate task running times: %d", num);
              }
            },
            0,
            1,
            TimeUnit.SECONDS);
    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    testScheduledThreadPool.shutdownNow();
    assertThat(num).isGreaterThan(1);
  }

  public static class Task<V> implements RunnableScheduledFuture<V> {

    private final Runnable runnable;
    private final RunnableScheduledFuture<V> task;
    private final Executor executor;
    private final Instant startTime;
    // runningState is non-null when listener or task code is running in an executor thread
    private final AtomicReference<Task.State> runningState = new AtomicReference<>();

    Task(Runnable runnable, RunnableScheduledFuture<V> task, Executor executor) {
      this.runnable = runnable;
      this.task = task;
      this.executor = executor;
      this.startTime = Instant.now();
    }

    public Instant getStartTime() {
      return startTime;
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
        if (runnable instanceof WorkQueue.CancelableRunnable) {
          if (runningState.compareAndSet(null, Task.State.RUNNING)) {
            ((WorkQueue.CancelableRunnable) runnable).cancel();
          } else if (runnable instanceof WorkQueue.CanceledWhileRunning) {
            ((WorkQueue.CanceledWhileRunning) runnable).setCanceledWhileRunning();
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
      if (runningState.compareAndSet(null, Task.State.STARTING)) {
        String oldThreadName = Thread.currentThread().getName();
        try {
          executor.onStart(this);
          runningState.set(Task.State.RUNNING);
          Thread.currentThread().setName(oldThreadName + "[" + task.toString() + "]");
          task.run();
        } finally {
          Thread.currentThread().setName(oldThreadName);
          runningState.set(Task.State.STOPPING);
          executor.onStop(this);
          if (isPeriodic()) {
            runningState.set(null);
          } else {
            runningState.set(Task.State.DONE);
            executor.remove(this);
          }
        }
      }
    }

    @Override
    public String toString() {
      return runnable.toString();
    }

    public enum State {
      // Ordered like this so ordinal matches the order we would
      // prefer to see tasks sorted in: done before running,
      // stopping before running, running before starting,
      // starting before ready, ready before sleeping.
      //
      DONE,
      CANCELLED,
      STOPPING,
      RUNNING,
      STARTING,
      READY,
      SLEEPING,
      OTHER
    }
  }

  private class Executor extends ScheduledThreadPoolExecutor {

    Executor(int corePoolSize) {
      super(corePoolSize);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
        Runnable command, long initialDelay, long period, TimeUnit unit) {
      return super.scheduleAtFixedRate(LoggingContext.copy(command), initialDelay, period, unit);
    }

    @Override
    protected <V> RunnableScheduledFuture<V> decorateTask(
        Runnable runnable, RunnableScheduledFuture<V> r) {
      r = super.decorateTask(runnable, r);
      for (; ; ) {
        Task<V> task;
        task = new Task<>(runnable, r, this);
        return task;
      }
    }

    @Override
    protected <V> RunnableScheduledFuture<V> decorateTask(
        Callable<V> callable, RunnableScheduledFuture<V> r) {
      FutureTask<V> ft = new FutureTask<>(callable);
      return decorateTask(ft, r);
    }

    public void onStart(Task<?> task) {}

    public void onStop(Task<?> task) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        logger.atWarning().log("%s", e.getMessage());
      }
    }
  }
}
