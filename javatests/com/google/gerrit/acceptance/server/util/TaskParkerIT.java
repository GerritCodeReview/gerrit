// Copyright (C) 2022 The Android Open Source Project
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
import com.google.gerrit.acceptance.server.util.TaskListenerIT.LatchedMethod;
import com.google.gerrit.acceptance.server.util.TaskListenerIT.LatchedRunnable;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.gerrit.server.git.WorkQueue.Task.State;
import com.google.gerrit.server.git.WorkQueue.TaskListener;
import com.google.gerrit.server.git.WorkQueue.TaskParker;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;

public class TaskParkerIT extends AbstractDaemonTest {
  private static class ForwardingParker extends TaskListenerIT.ForwardingListener<LatchedParker>
      implements TaskParker {
    public AtomicInteger isReadyToStartCounter = new AtomicInteger(0);
    public AtomicInteger onNotReadyToStartCounter = new AtomicInteger(0);

    @Override
    public boolean isReadyToStart(Task<?> task) {
      isReadyToStartCounter.incrementAndGet();
      if (isDelegatable(task)) {
        return delegate.isReadyToStart(task);
      }
      return true;
    }

    @Override
    public void onNotReadyToStart(Task<?> task) {
      onNotReadyToStartCounter.incrementAndGet();
      if (isDelegatable(task)) {
        delegate.onNotReadyToStart(task);
      }
    }

    public void resetCounters() {
      isReadyToStartCounter.getAndSet(0);
      onNotReadyToStartCounter.getAndSet(0);
    }
  }

  public static class LatchedParker extends TaskListenerIT.LatchedListener implements TaskParker {
    public volatile LatchedMethod<Boolean> isReadyToStart = new LatchedMethod<>();
    public volatile LatchedMethod<?> onNotReadyToStart = new LatchedMethod<>();

    @Override
    public boolean isReadyToStart(Task<?> task) {
      Boolean rtn = isReadyToStart.call();
      isReadyToStart = new LatchedMethod<>();

      if ("park-me".equals(task.toString())) {
        return false;
      }

      if (rtn != null) {
        return rtn;
      }
      return true;
    }

    @Override
    public void onNotReadyToStart(Task<?> task) {
      onNotReadyToStart.call();
    }
  }

  private static ForwardingParker forwarder;
  private static ForwardingParker forwarder2;
  public static final long TIMEOUT = TimeUnit.MILLISECONDS.convert(200, TimeUnit.MILLISECONDS);

  private final LatchedParker parker = new LatchedParker();

  @Inject private WorkQueue workQueue;
  private ScheduledExecutorService executor;

  @Before
  public void setupExecutorAndForwarder() throws InterruptedException {
    if (executor == null) {
      executor = workQueue.createQueue(1, "TaskListeners");
    }

    // "Log File Manager"s are likely running and will interfere with tests
    while (0 != workQueue.getTasks().size()) {
      for (Task<?> t : workQueue.getTasks()) {
        t.cancel(true);
      }
      TimeUnit.MILLISECONDS.sleep(1);
    }
    forwarder.delegate = parker;
    forwarder.task = null;
    forwarder.resetCounters();
    forwarder2.delegate = null; // load only if test needs it
    forwarder2.task = null;
    forwarder2.resetCounters();
  }

  @Override
  public Module createModule() {
    return new AbstractModule() {
      @Override
      public void configure() {
        // Forwarder.delegate is empty on start to protect test parker from non-test tasks (such as
        // the "Log File Manager") interference
        forwarder = new ForwardingParker(); // Only gets bound once for all tests
        bind(TaskListener.class).annotatedWith(Exports.named("parker")).toInstance(forwarder);
        forwarder2 = new ForwardingParker();
        bind(TaskListener.class).annotatedWith(Exports.named("parker2")).toInstance(forwarder2);
      }
    };
  }

  @Test
  public void noParkFlow() throws Exception {
    LatchedRunnable runnable = new LatchedRunnable();

    assertThat(workQueue.getTasks().size()).isEqualTo(0);
    assertThat(forwarder.task).isEqualTo(null);
    parker.isReadyToStart.assertUncalled();
    parker.onNotReadyToStart.assertUncalled();
    parker.onStart.assertUncalled();
    runnable.run.assertUncalled();
    parker.onStop.assertUncalled();
    assertCorePoolSize(1);

    executor.execute(runnable);
    assertThat(workQueue.getTasks().size()).isEqualTo(1);

    parker.isReadyToStart.awaitAndAssertCalled();
    assertStateIs(forwarder.task, State.PARKED);
    assertThat(workQueue.getTasks().size()).isEqualTo(1);
    parker.onNotReadyToStart.assertUncalled();
    parker.onStart.assertUncalled();
    runnable.run.assertUncalled();
    parker.onStop.assertUncalled();

    parker.isReadyToStart.complete();
    parker.onStart.awaitAndAssertCalled();
    assertStateIs(forwarder.task, State.STARTING);
    assertThat(workQueue.getTasks().size()).isEqualTo(1);
    parker.onNotReadyToStart.assertUncalled();
    runnable.run.assertUncalled();
    parker.onStop.assertUncalled();

    parker.onStart.complete();
    runnable.run.awaitAndAssertCalled();
    assertStateIs(forwarder.task, State.RUNNING);
    assertThat(workQueue.getTasks().size()).isEqualTo(1);
    parker.onNotReadyToStart.assertUncalled();
    parker.onStop.assertUncalled();

    runnable.run.complete();
    parker.onStop.awaitAndAssertCalled();
    assertStateIs(forwarder.task, State.STOPPING);
    assertThat(workQueue.getTasks().size()).isEqualTo(1);
    parker.onNotReadyToStart.assertUncalled();

    parker.onStop.complete();
    assertTaskCountIsEventually(0);
    assertStateIs(forwarder.task, State.DONE);
    parker.onNotReadyToStart.assertUncalled();
    assertCorePoolSize(1);
    assertCounterIsEventually(forwarder.isReadyToStartCounter, 1);
    assertCounter(forwarder.onNotReadyToStartCounter, 0);
  }

  @Test
  public void parkFirstSoSecondRuns() throws Exception {
    LatchedRunnable runnable1 = new LatchedRunnable();
    LatchedRunnable runnable2 = new LatchedRunnable();
    assertCorePoolSize(1);

    executor.execute(runnable1);
    parker.isReadyToStart.awaitAndAssertCalled();
    Task<?> task1 = forwarder.task; // task for runnable1
    assertCounterIsEventually(forwarder.isReadyToStartCounter, 1);
    assertCounter(forwarder.onNotReadyToStartCounter, 0);
    executor.execute(runnable2);
    assertThat(workQueue.getTasks().size()).isEqualTo(2);
    parker.onNotReadyToStart.assertUncalled();
    parker.onStart.assertUncalled();
    runnable1.run.assertUncalled();
    parker.onStop.assertUncalled();

    parker.isReadyToStart.set(false);
    assertCorePoolSizeIsEventually(2);

    runnable2.run.awaitAndAssertCalled();
    assertThat(workQueue.getTasks().size()).isEqualTo(2);
    parker.onNotReadyToStart.assertUncalled();
    parker.onStart.assertUncalled();
    runnable1.run.assertUncalled();
    parker.onStop.assertUncalled();
    assertStateIs(task1, State.PARKED);

    parker.isReadyToStart.set(true);
    assertCounterIsEventually(forwarder.isReadyToStartCounter, 2);
    assertCounter(forwarder.onNotReadyToStartCounter, 0);
    runnable2.run.complete();

    parker.isReadyToStart.awaitAndAssertCalled();
    assertStateIs(task1, State.PARKED);
    parker.onNotReadyToStart.assertUncalled();
    parker.onStart.assertUncalled();
    runnable1.run.assertUncalled();
    parker.onStop.assertUncalled();

    parker.isReadyToStart.complete();
    parker.onStart.awaitAndAssertCalled();
    assertStateIs(task1, State.STARTING);
    assertTaskCountIsEventually(1);
    parker.onNotReadyToStart.assertUncalled();
    runnable1.run.assertUncalled();
    parker.onStop.assertUncalled();

    parker.onStart.complete();
    runnable1.run.awaitAndAssertCalled();
    assertStateIs(task1, State.RUNNING);
    assertThat(workQueue.getTasks().size()).isEqualTo(1);
    parker.onNotReadyToStart.assertUncalled();
    parker.onStop.assertUncalled();

    runnable1.run.complete();
    parker.onStop.awaitAndAssertCalled();
    assertStateIs(task1, State.STOPPING);
    assertThat(workQueue.getTasks().size()).isEqualTo(1);
    parker.onNotReadyToStart.assertUncalled();

    parker.onStop.complete();
    assertTaskCountIsEventually(0);
    assertStateIs(task1, State.DONE);
    parker.onNotReadyToStart.assertUncalled();
    assertCorePoolSize(1);
    assertCounterIsEventually(forwarder.isReadyToStartCounter, 3);
    assertCounter(forwarder.onNotReadyToStartCounter, 0);
  }

  @Test
  public void unParkPriorityOrder() throws Exception {
    LatchedRunnable runnable1 = new LatchedRunnable();
    LatchedRunnable runnable2 = new LatchedRunnable();
    LatchedRunnable runnable3 = new LatchedRunnable();

    // park runnable1
    assertCorePoolSize(1);
    executor.execute(runnable1);
    parker.isReadyToStart.set(false);
    parker.isReadyToStart.awaitAndAssertCalled();
    Task<?> task1 = forwarder.task; // task for runnable1
    assertStateIs(task1, State.PARKED);
    assertCounterIsEventually(forwarder.isReadyToStartCounter, 1);
    assertCounter(forwarder.onNotReadyToStartCounter, 0);
    assertTaskCountIsEventually(1);
    assertCorePoolSizeIsEventually(2);

    // park runnable2
    forwarder.resetDelegate(parker);
    executor.execute(runnable2);
    parker.isReadyToStart.set(false);
    parker.isReadyToStart.awaitAndAssertCalled();
    Task<?> task2 = forwarder.task; // task for runnable2
    assertStateIs(task2, State.PARKED);

    assertCounterIsEventually(forwarder.isReadyToStartCounter, 2);
    assertCounter(forwarder.onNotReadyToStartCounter, 0);
    assertTaskCountIsEventually(2);
    assertCorePoolSizeIsEventually(3);

    // set parker to ready and execute runnable3
    parker.isReadyToStart.set(true);
    forwarder.resetDelegate(parker);
    executor.execute(runnable3);

    // assert runnable3 finishes executing and runnable1, runnable2 stay parked
    parker.isReadyToStart.awaitAndAssertCalled();
    Task<?> task3 = forwarder.task; // task for runnable3
    assertStateIs(task3, State.PARKED);
    assertCounterIsEventually(forwarder.isReadyToStartCounter, 3);
    assertCounter(forwarder.onNotReadyToStartCounter, 0);
    parker.isReadyToStart.complete();
    parker.onStart.awaitAndAssertCalled();
    assertStateIs(task3, State.STARTING);
    parker.onStart.complete();
    runnable3.run.awaitAndAssertCalled();
    assertStateIs(task3, State.RUNNING);
    runnable1.run.assertUncalled();
    runnable2.run.assertUncalled();
    runnable3.run.complete();
    parker.onStop.awaitAndAssertCalled();
    assertStateIs(task3, State.STOPPING);
    parker.onStop.complete();
    assertTaskCountIsEventually(2);
    assertStateIs(task3, State.DONE);

    // assert runnable1 finishes executing and runnable2 stays parked
    runnable1.run.awaitAndAssertCalled();
    assertStateIs(task1, State.RUNNING);
    assertCounterIsEventually(forwarder.isReadyToStartCounter, 4);
    assertCounter(forwarder.onNotReadyToStartCounter, 0);
    runnable2.run.assertUncalled();
    assertStateIs(task2, State.PARKED);
    runnable1.run.complete();
    assertCorePoolSizeIsEventually(2);
    assertTaskCountIsEventually(1);
    assertStateIs(task1, State.DONE);

    // assert runnable2 finishes executing
    runnable2.run.awaitAndAssertCalled();
    assertStateIs(task2, State.RUNNING);
    assertCounterIsEventually(forwarder.isReadyToStartCounter, 5);
    assertCounter(forwarder.onNotReadyToStartCounter, 0);
    runnable2.run.complete();
    assertCorePoolSizeIsEventually(1);
    assertTaskCountIsEventually(0);
    assertStateIs(task2, State.DONE);
  }

  @Test
  public void notReadyToStartIsCalledOnReadyListenerWhenAnotherListenerIsNotReady()
      throws InterruptedException {
    LatchedRunnable runnable1 = new LatchedRunnable();
    LatchedRunnable runnable2 = new LatchedRunnable();

    LatchedParker parker2 = new LatchedParker();
    forwarder2.delegate = parker2;

    // park runnable1 (parker1 is ready and parker2 is not ready)
    assertCorePoolSize(1);
    executor.execute(runnable1);
    parker2.isReadyToStart.set(false);

    assertTaskCountIsEventually(1);
    assertCorePoolSizeIsEventually(2);

    assertCounterIsEventually(forwarder.isReadyToStartCounter, 1);
    assertCounterIsEventually(forwarder.onNotReadyToStartCounter, 1);
    assertCounterIsEventually(forwarder2.isReadyToStartCounter, 1);
    assertCounter(forwarder2.onNotReadyToStartCounter, 0);
    Task<?> task1 = forwarder.task; // task for runnable1
    assertStateIs(task1, State.PARKED);

    // set parker2 to ready and execute runnable-2
    parker2.isReadyToStart.set(true);
    forwarder.resetDelegate(parker);
    forwarder2.resetDelegate(parker2);
    executor.execute(runnable2);

    assertCounterIsEventually(forwarder.isReadyToStartCounter, 2);
    assertCounterIsEventually(forwarder.onNotReadyToStartCounter, 1);
    assertCounterIsEventually(forwarder2.isReadyToStartCounter, 2);
    assertCounter(forwarder2.onNotReadyToStartCounter, 0);
    Task<?> task2 = forwarder.task; // task for runnable2

    assertCorePoolSizeIsEventually(1);
    runnable2.run.awaitAndAssertCalled();
    runnable2.run.complete();
    assertTaskCountIsEventually(1);
    assertStateIs(task2, State.DONE);

    assertCounterIsEventually(forwarder.isReadyToStartCounter, 3);
    assertCounterIsEventually(forwarder.onNotReadyToStartCounter, 1);
    assertCounterIsEventually(forwarder2.isReadyToStartCounter, 3);
    assertCounter(forwarder2.onNotReadyToStartCounter, 0);

    runnable1.run.awaitAndAssertCalled();
    runnable1.run.complete();
    assertTaskCountIsEventually(0);
    assertStateIs(task1, State.DONE);
  }

  @Test
  public void canParkUsingTaskName() throws Exception {
    LatchedRunnable runnable1 = new LatchedRunnable("park-me");
    LatchedRunnable runnable2 = new LatchedRunnable();

    executor.execute(runnable1);
    parker.isReadyToStart.awaitAndAssertCalled();
    Task<?> task1 = forwarder.task; // task for runnable1

    assertCounterIsEventually(forwarder.isReadyToStartCounter, 1);
    assertCounter(forwarder.onNotReadyToStartCounter, 0);
    assertTaskCountIsEventually(1);
    assertCorePoolSizeIsEventually(2);
    assertStateIs(task1, State.PARKED);

    executor.execute(runnable2);
    runnable2.run.awaitAndAssertCalled();
    assertTaskCountIsEventually(2);
    runnable2.run.complete();

    assertCounterIsEventually(forwarder.isReadyToStartCounter, 3);
    assertCounter(forwarder.onNotReadyToStartCounter, 0);
    assertTaskCountIsEventually(1);
    runnable1.run.assertUncalled();

    // since runnable1 will stay parked forever, shutdown the executor to
    // finish the test
    assertStateIs(task1, State.PARKED);
    executor.shutdownNow();
  }

  private void assertTaskCountIsEventually(int count) throws InterruptedException {
    TaskListenerIT.assertTaskCountIsEventually(workQueue, count);
  }

  private void assertCorePoolSize(int count) {
    assertThat(count).isEqualTo(((ScheduledThreadPoolExecutor) executor).getCorePoolSize());
  }

  private void assertCounter(AtomicInteger counter, int desiredCount) {
    assertThat(counter.get()).isEqualTo(desiredCount);
  }

  private void assertCounterIsEventually(AtomicInteger counter, int desiredCount)
      throws InterruptedException {
    long ms = 0;
    while (desiredCount != counter.get()) {
      assertThat(ms++).isLessThan(TIMEOUT);
      TimeUnit.MILLISECONDS.sleep(1);
    }
  }

  private void assertCorePoolSizeIsEventually(int count) throws InterruptedException {
    ScheduledThreadPoolExecutor scheduledThreadPoolExecutor =
        (ScheduledThreadPoolExecutor) executor;
    long ms = 0;
    while (count != scheduledThreadPoolExecutor.getCorePoolSize()) {
      assertThat(ms++).isLessThan(TIMEOUT);
      TimeUnit.MILLISECONDS.sleep(1);
    }
  }

  private void assertStateIs(Task<?> task, Task.State state) {
    assertThat(task).isNotNull();
    assertThat(task.getState()).isEqualTo(state);
  }
}
