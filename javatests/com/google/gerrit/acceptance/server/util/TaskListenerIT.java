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
   * Associate a LatchedMethod with a method whose calling is to be awaited by another thread. Once
   * called, the call() method will then be blocked until another thread calls its complete()
   * method.
   */
  private static class LatchedMethod {
    /** API class meant be used by the class whose method is being latched */
    private class Latch {
      /** Ensure that the latched method calls this on entry */
      public void call() {
        called.countDown();
        await(complete);
      }
    }

    public Latch latch = new Latch();

    private CountDownLatch called = new CountDownLatch(1);
    private CountDownLatch complete = new CountDownLatch(1);

    /** Assert that the Latch's call() method has not yet been called */
    public void assertUncalled() {
      assertThat(called.getCount()).isEqualTo(1);
    }

    /**
     * Assert that a timeout does not occur while awaiting for Latch's call() method to be called.
     * Fails if the waiting time elapses before Proxy's call() method is called, otherwise passes.
     */
    public void assertAwait() {
      assertThat(await(called)).isEqualTo(true);
    }

    /** Unblock the Latch's call() method so that it can complete */
    public void complete() {
      complete.countDown();
    }
  }

  private static class LatchedRunnable implements Runnable {
    public LatchedMethod run = new LatchedMethod();

    @Override
    public void run() {
      run.latch.call();
    }
  }

  private static class ForwardingListener implements TaskListener {
    public volatile TaskListener delegate;
    public volatile Task task;

    public void resetDelegate(TaskListener listener) {
      delegate = listener;
      task = null;
    }

    @Override
    public void onStart(Task<?> task) {
      if (delegate != null) {
        if (this.task == null || this.task == task) {
          this.task = task;
          delegate.onStart(task);
        }
      }
    }

    @Override
    public void onStop(Task<?> task) {
      if (delegate != null) {
        if (this.task == task) {
          delegate.onStop(task);
        }
      }
    }
  }

  private static class LatchedListener implements TaskListener {
    public LatchedMethod onStart = new LatchedMethod();
    public LatchedMethod onStop = new LatchedMethod();

    @Override
    public void onStart(Task<?> task) {
      onStart.latch.call();
    }

    @Override
    public void onStop(Task<?> task) {
      onStop.latch.call();
    }
  }

  private static final int AWAIT_TIMEOUT = 20;
  private static final TimeUnit AWAIT_TIMEUNIT = TimeUnit.MILLISECONDS;

  private static ForwardingListener forwarder;

  @Inject private WorkQueue workQueue;
  private ScheduledExecutorService executor;

  private LatchedListener listener = new LatchedListener();
  private LatchedRunnable runnable = new LatchedRunnable();

  @Override
  public Module createModule() {
    return new AbstractModule() {
      @Override
      public void configure() {
        // Forwarder.delegate is empty on start to protect test listener from non test tasks
        // (such as the "Log File Compressor") interference
        forwarder = new ForwardingListener(); // Only gets bound once for all tests
        bind(TaskListener.class).annotatedWith(Exports.named("listener")).toInstance(forwarder);
      }
    };
  }

  @Before
  public void setupExecutorAndForwarder() throws InterruptedException {
    executor = workQueue.createQueue(1, "TaskListeners");

    // "Log File Compressor"s are likely running and will interfere with tests
    while (0 != workQueue.getTasks().size()) {
      for (Task<?> t : workQueue.getTasks()) {
        t.cancel(true);
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
    int size = assertQueueBlocked(runnable);

    // onStartThenRunThenOnStopAreCalled -> onStart...Called
    listener.onStart.assertAwait();
    assertQueueSize(size);
    runnable.run.assertUncalled();
    listener.onStop.assertUncalled();

    listener.onStart.complete();
    // onStartThenRunThenOnStopAreCalled -> ...ThenRun...Called
    runnable.run.assertAwait();
    listener.onStop.assertUncalled();

    runnable.run.complete();
    // onStartThenRunThenOnStopAreCalled -> ...ThenOnStop...Called
    listener.onStop.assertAwait();
    assertQueueSize(size);

    listener.onStop.complete();
    assertAwaitQueueSize(--size);
  }

  @Test
  public void firstBlocksSecond() throws Exception {
    int size = assertQueueBlocked(runnable);

    // firstBlocksSecond -> first...
    listener.onStart.assertAwait();
    assertQueueSize(size);

    LatchedRunnable runnable2 = new LatchedRunnable();
    size = assertQueueBlocked(runnable2);

    // firstBlocksSecond -> ...BlocksSecond
    runnable2.run.assertUncalled();
    assertQueueSize(size); // waiting on first

    listener.onStart.complete();
    runnable.run.assertAwait();
    assertQueueSize(size); // waiting on first
    runnable2.run.assertUncalled();

    runnable.run.complete();
    listener.onStop.assertAwait();
    assertQueueSize(size); // waiting on first
    runnable2.run.assertUncalled();

    listener.onStop.complete();
    runnable2.run.assertAwait();
    assertQueueSize(--size);

    runnable2.run.complete();
    assertAwaitQueueSize(--size);
  }

  @Test
  public void states() throws Exception {
    executor.execute(runnable);
    listener.onStart.assertAwait();
    assertStateIs(Task.State.STARTING);

    listener.onStart.complete();
    runnable.run.assertAwait();
    assertStateIs(Task.State.RUNNING);

    runnable.run.complete();
    listener.onStop.assertAwait();
    assertStateIs(Task.State.STOPPING);

    listener.onStop.complete();
    assertAwaitQueueIsEmpty();
    assertStateIs(Task.State.DONE);
  }

  private void assertStateIs(Task.State state) {
    assertThat(forwarder.task.getState()).isEqualTo(state);
  }

  private int assertQueueBlocked(Runnable runnable) {
    int size = workQueue.getTasks().size() + 1;
    executor.execute(runnable);
    assertQueueSize(size);
    return size;
  }

  private void assertQueueSize(int size) {
    assertThat(workQueue.getTasks().size()).isEqualTo(size);
  }

  private void assertAwaitQueueIsEmpty() throws InterruptedException {
    assertAwaitQueueSize(0);
  }

  /** Fails if the waiting time elapses before the count is reached, otherwise passes */
  private void assertAwaitQueueSize(int size) throws InterruptedException {
    long i = 0;
    do {
      TimeUnit.NANOSECONDS.sleep(10);
      assertThat(i++).isLessThan(100);
    } while (size != workQueue.getTasks().size());
  }

  private static boolean await(CountDownLatch latch) {
    try {
      return latch.await(AWAIT_TIMEOUT, AWAIT_TIMEUNIT);
    } catch (InterruptedException e) {
      return false;
    }
  }
}
