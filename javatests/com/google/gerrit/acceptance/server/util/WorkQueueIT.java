package com.google.gerrit.acceptance.server.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class WorkQueueIT extends AbstractDaemonTest {
  @Inject private WorkQueue workQueue;
  private TestListener testListener;
  private static final Integer FIXED_RATE_SCHEDULE_INITIAL_DELAY = 0;
  private static final Integer FIXED_RATE_SCHEDULE_INTERVAL_MILLI_SEC = 200;
  private static final Integer POOL_CORE_SIZE = 8;
  private static final String QUEUE_NAME = "test-Queue";
  private final CountDownLatch downLatch = new CountDownLatch(2);

  public static class TestListener implements WorkQueue.TaskListener {

    @Override
    public void onStart(WorkQueue.Task<?> task) {}

    @Override
    public void onStop(WorkQueue.Task<?> task) {
      try {
        Thread.sleep(FIXED_RATE_SCHEDULE_INTERVAL_MILLI_SEC + 1);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public Module createModule() {
    return new AbstractModule() {
      @Override
      public void configure() {
        testListener = new TestListener();
        bind(WorkQueue.TaskListener.class)
            .annotatedWith(Exports.named("listener"))
            .toInstance(testListener);
      }
    };
  }

  @Test
  public void testScheduleAtFixedRate() throws InterruptedException {
    ScheduledExecutorService testExecutor = workQueue.createQueue(POOL_CORE_SIZE, QUEUE_NAME);
    ScheduledFuture<?> unusedFuture =
        testExecutor.scheduleAtFixedRate(
            downLatch::countDown,
            FIXED_RATE_SCHEDULE_INITIAL_DELAY,
            FIXED_RATE_SCHEDULE_INTERVAL_MILLI_SEC,
            TimeUnit.MILLISECONDS);

    boolean ifRunMoreThanOnce =
        downLatch.await(5 * FIXED_RATE_SCHEDULE_INTERVAL_MILLI_SEC, TimeUnit.MILLISECONDS);
    assertThat(ifRunMoreThanOnce).isTrue();
    testExecutor.shutdownNow();
  }
}
