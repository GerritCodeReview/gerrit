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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.gerrit.server.git.WorkQueue.TaskListener;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;

public class TaskListenerIT extends AbstractDaemonTest {
  /**
   * Use a LatchedMethod in a method to allow another thread to await the method's call. Once
   * called, the call() method will block until another thread calls the complete() method or until
   * a preset timeout is reached.
   */
  public static class LatchedMethod<T> {
    private volatile T value;

    private final CountDownLatch called = new CountDownLatch(1);
    private final CountDownLatch complete = new CountDownLatch(1);

    /** Assert that the call() method has not yet been called */
    public void assertUncalled() {
      assertThat(called.getCount()).isEqualTo(1);
    }

    /**
     * Assert that a timeout does not occur while awaiting the call() to be called. Fails if the
     * waiting time elapses before the call() method is called, otherwise passes.
     */
    public void assertCalledEventually() {
      assertThat(await(called)).isEqualTo(true);
    }

    public T call() {
      called.countDown();
      await(complete);
      return getValue();
    }

    public T call(T val) {
      set(val);
      return call();
    }

    public T callAndAwaitComplete() throws InterruptedException {
      called.countDown();
      complete.await();
      return getValue();
    }

    public void complete() {
      complete.countDown();
    }

    public void set(T val) {
      value = val;
    }

    public void complete(T val) {
      set(val);
      complete();
    }

    public void assertCalledEventuallyThenComplete(T val) {
      assertCalledEventually();
      complete(val);
    }

    protected T getValue() {
      return value;
    }
  }

  public static class LatchedRunnable implements Runnable {
    public LatchedMethod<?> run = new LatchedMethod<>();
    public String name = "latched-runnable";

    public LatchedRunnable(String name) {
      this.name = name;
    }

    public LatchedRunnable() {}

    @Override
    public void run() {
      run.call();
    }

    @Override
    public String toString() {
      return name;
    }
  }

  public static class ForwardingListener<T extends TaskListener> implements TaskListener {
    public volatile T delegate;
    public volatile Task<?> task;

    public void resetDelegate(T listener) {
      delegate = listener;
      task = null;
    }

    @Override
    public void onStart(Task<?> task) {
      if (isDelegatable(task)) {
        delegate.onStart(task);
      }
    }

    @Override
    public void onStop(Task<?> task) {
      if (isDelegatable(task)) {
        delegate.onStop(task);
      }
    }

    protected boolean isDelegatable(Task<?> task) {
      if (delegate != null) {
        if (this.task == task) {
          return true;
        }
        if (this.task == null) {
          this.task = task;
          return true;
        }
      }
      return false;
    }
  }

  public static class LatchedListener implements TaskListener {
    public LatchedMethod<?> onStart = new LatchedMethod<>();
    public LatchedMethod<?> onStop = new LatchedMethod<>();

    @Override
    public void onStart(Task<?> task) {
      onStart.call();
    }

    @Override
    public void onStop(Task<?> task) {
      onStop.call();
    }
  }

  private static final int AWAIT_TIMEOUT = 20;
  private static final TimeUnit AWAIT_TIMEUNIT = TimeUnit.MILLISECONDS;
  private static final long MS_EMPTY_QUEUE =
      TimeUnit.MILLISECONDS.convert(50, TimeUnit.MILLISECONDS);

  private static ForwardingListener<TaskListener> forwarder;

  @Inject private WorkQueue workQueue;
  private ScheduledExecutorService executor;

  private final LatchedListener listener = new LatchedListener();
  private final LatchedRunnable runnable = new LatchedRunnable();

  @Override
  public Module createModule() {
    return new AbstractModule() {
      @Override
      public void configure() {
        // Forwarder.delegate is empty on start to protect test listener from non-test tasks (such
        // as the "Log File Manager") interference
        forwarder = new ForwardingListener<>(); // Only gets bound once for all tests
        bind(TaskListener.class).annotatedWith(Exports.named("listener")).toInstance(forwarder);
      }
    };
  }

  @Before
  public void setupExecutorAndForwarder() throws InterruptedException {
    executor = workQueue.createQueue(1, "TaskListeners");

    // "Log File Manager"s are likely running and will interfere with tests
    while (0 != workQueue.getTasks().size()) {
      for (Task<?> t : workQueue.getTasks()) {
        @SuppressWarnings("unused")
        boolean unused = t.cancel(true);
      }
      TimeUnit.MILLISECONDS.sleep(1);
    }

    forwarder.resetDelegate(listener);

    assertQueueSize(0);
    assertThat(forwarder.task).isEqualTo(null);
    listener.onStart.assertUncalled();
    runnable.run.assertUncalled();
    listener.onStop.assertUncalled();
  }

  @Test
  public void onStartThenRunThenOnStopAreCalled() throws Exception {
    int size = assertQueueBlockedOnExecution(runnable);

    // onStartThenRunThenOnStopAreCalled -> onStart...Called
    listener.onStart.assertCalledEventually();
    assertQueueSize(size);
    runnable.run.assertUncalled();
    listener.onStop.assertUncalled();

    listener.onStart.complete();
    // onStartThenRunThenOnStopAreCalled -> ...ThenRun...Called
    runnable.run.assertCalledEventually();
    listener.onStop.assertUncalled();

    runnable.run.complete();
    // onStartThenRunThenOnStopAreCalled -> ...ThenOnStop...Called
    listener.onStop.assertCalledEventually();
    assertQueueSize(size);

    listener.onStop.complete();
    assertTaskCountIsEventually(--size);
  }

  @Test
  public void firstBlocksSecond() throws Exception {
    int size = assertQueueBlockedOnExecution(runnable);

    // firstBlocksSecond -> first...
    listener.onStart.assertCalledEventually();
    assertQueueSize(size);

    LatchedRunnable runnable2 = new LatchedRunnable();
    size = assertQueueBlockedOnExecution(runnable2);

    // firstBlocksSecond -> ...BlocksSecond
    runnable2.run.assertUncalled();
    assertQueueSize(size); // waiting on first

    listener.onStart.complete();
    runnable.run.assertCalledEventually();
    assertQueueSize(size); // waiting on first
    runnable2.run.assertUncalled();

    runnable.run.complete();
    listener.onStop.assertCalledEventually();
    assertQueueSize(size); // waiting on first
    runnable2.run.assertUncalled();

    listener.onStop.complete();
    runnable2.run.assertCalledEventually();
    assertQueueSize(--size);

    runnable2.run.complete();
    assertTaskCountIsEventually(--size);
  }

  @Test
  public void states() throws Exception {
    executor.execute(runnable);
    listener.onStart.assertCalledEventually();
    assertStateIs(Task.State.STARTING);

    listener.onStart.complete();
    runnable.run.assertCalledEventually();
    assertStateIs(Task.State.RUNNING);

    runnable.run.complete();
    listener.onStop.assertCalledEventually();
    assertStateIs(Task.State.STOPPING);

    listener.onStop.complete();
    assertAwaitQueueIsEmpty();
    assertStateIs(Task.State.DONE);
  }

  /** Fails if the waiting time elapses before the count is reached, otherwise passes */
  public static void assertTaskCountIsEventually(WorkQueue workQueue, int count)
      throws InterruptedException {
    long ms = 0;
    while (count != workQueue.getTasks().size()) {
      assertThat(ms++).isLessThan(MS_EMPTY_QUEUE);
      TimeUnit.MILLISECONDS.sleep(1);
    }
  }

  public static void assertQueueSize(WorkQueue workQueue, int size) {
    assertThat(workQueue.getTasks().size()).isEqualTo(size);
  }

  @CanIgnoreReturnValue
  public static boolean await(CountDownLatch latch) {
    try {
      return latch.await(AWAIT_TIMEOUT, AWAIT_TIMEUNIT);
    } catch (InterruptedException e) {
      return false;
    }
  }

  public void assertTaskCountIsEventually(int count) throws InterruptedException {
    TaskListenerIT.assertTaskCountIsEventually(workQueue, count);
  }

  public static void assertStateIs(Task<?> task, Task.State state) {
    assertThat(task).isNotNull();
    assertThat(task.getState()).isEqualTo(state);
  }

  private void assertStateIs(Task.State state) {
    assertStateIs(forwarder.task, state);
  }

  private int assertQueueBlockedOnExecution(Runnable runnable) {
    int expectedSize = workQueue.getTasks().size() + 1;
    executor.execute(runnable);
    assertQueueSize(expectedSize);
    return expectedSize;
  }

  private void assertQueueSize(int size) {
    assertQueueSize(workQueue, size);
  }

  private void assertAwaitQueueIsEmpty() throws InterruptedException {
    assertTaskCountIsEventually(0);
  }
}
