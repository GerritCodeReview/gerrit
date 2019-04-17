package com.google.gerrit.testing;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.util.concurrent.ForwardingExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Forwards all calls to a direct executor making it so that the submitted {@link Runnable}s run
 * synchronously. Holds a count of the number of tasks that were executed.
 */
public class AssertableExecutorService extends ForwardingExecutorService {

  private final ExecutorService delegate = MoreExecutors.newDirectExecutorService();
  private final AtomicInteger numInteractions = new AtomicInteger();

  @Override
  protected ExecutorService delegate() {
    return delegate;
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    numInteractions.incrementAndGet();
    return super.submit(task);
  }

  @Override
  public Future<?> submit(Runnable task) {
    numInteractions.incrementAndGet();
    return super.submit(task);
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    numInteractions.incrementAndGet();
    return super.submit(task, result);
  }

  /** Asserts and resets the number of executions this executor observed. */
  public void assertInteractions(int expectedNumInteractions) {
    assertThat(numInteractions.get())
        .named("expectedRunnablesSubmittedOnExecutor")
        .isEqualTo(expectedNumInteractions);
    numInteractions.set(0);
  }
}
