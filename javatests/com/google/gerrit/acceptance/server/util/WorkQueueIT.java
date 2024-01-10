package com.google.gerrit.acceptance.server.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class WorkQueueIT extends AbstractDaemonTest {
  @Inject private WorkQueue workQueue;

  public static class testListener implements WorkQueue.TaskListener {

    public static class testModule extends AbstractModule {
      @Override
      public void configure() {
        bind(WorkQueue.TaskListener.class).annotatedWith(Exports.named("listener")).to(testListener.class);
      }
    }

    @Override
    public void onStart(WorkQueue.Task<?> task) {

    }

    @Override
    public void onStop(WorkQueue.Task<?> task) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
  }

  @Test
  public void testScheduleAtFixedRate() throws ExecutionException, InterruptedException {
    ScheduledExecutorService testExecutor = workQueue.createQueue(8, "test-Queue");
    ScheduledFuture<?> unusedFuture =
        testExecutor.scheduleAtFixedRate(
                () -> {
                },
            0,
            1,
            TimeUnit.SECONDS);
    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    assertThat(workQueue.getTasks().get(0).getState()).isNotEqualTo(WorkQueue.Task.State.SLEEPING);
    testExecutor.shutdownNow();
  }
}
