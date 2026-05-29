// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.Task.State;
import com.google.gerrit.server.restapi.config.ListTasks;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class WorkQueueIT extends AbstractDaemonTest {
  public static class TestListener implements WorkQueue.TaskListener {

    @Override
    public void onStart(WorkQueue.Task<?> task) {}

    @Override
    public void onStop(WorkQueue.Task<?> task) {
      try {
        Thread.sleep(FIXED_RATE_SCHEDULE_INTERVAL_MILLI_SEC);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static final Integer FIXED_RATE_SCHEDULE_INITIAL_DELAY = 0;
  private static final Integer FIXED_RATE_SCHEDULE_INTERVAL_MILLI_SEC = 200;
  private static final Integer POOL_CORE_SIZE = 8;
  private static final String QUEUE_NAME = "test-Queue";
  private static final Integer EXCEPT_RUN_TIMES = 2;
  private static final Integer TIMEOUT_MILLIS = 500;
  private final CountDownLatch downLatch = new CountDownLatch(EXCEPT_RUN_TIMES);
  @Inject private WorkQueue workQueue;
  @Inject private ListTasks listTasks;
  private TestListener testListener;

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
        downLatch.await(
            EXCEPT_RUN_TIMES * FIXED_RATE_SCHEDULE_INTERVAL_MILLI_SEC, TimeUnit.MILLISECONDS);
    assertThat(ifRunMoreThanOnce).isTrue();
    testExecutor.shutdownNow();
  }

  @Test
  public void testCanceledTaskStaysUntilFinished() throws Exception {
    ScheduledExecutorService testExecutor = workQueue.createQueue(POOL_CORE_SIZE, QUEUE_NAME);
    CountDownLatch latch = new CountDownLatch(1);
    Future<?> taskFuture =
        testExecutor.submit(
            () -> {
              try {
                latch.await();
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
            });
    assertTasksInStateEventually(QUEUE_NAME, State.RUNNING, 1);

    taskFuture.cancel(false);
    assertTasksInStateEventually(QUEUE_NAME, State.CANCELLED, 1);

    latch.countDown();
    // task is now removed after completion
    assertEventually(
        () ->
            listTasks.apply(new ConfigResource()).value().stream()
                .noneMatch(t -> t.queueName.equals(QUEUE_NAME)));
    testExecutor.shutdownNow();
  }

  public void assertTasksInStateEventually(String queue, State expectedState, int expectedCount)
      throws Exception {
    assertEventually(
        () ->
            expectedCount
                == listTasks.apply(new ConfigResource()).value().stream()
                    .filter(t -> t.queueName.equals(queue))
                    .filter(t -> t.state.equals(expectedState))
                    .count());
  }

  public void assertEventually(Callable<Boolean> r) throws Exception {
    long ms = 0;
    while (r.call() != true) {
      assertThat(ms++).isLessThan(TIMEOUT_MILLIS);
      TimeUnit.MILLISECONDS.sleep(1);
    }
  }
}
