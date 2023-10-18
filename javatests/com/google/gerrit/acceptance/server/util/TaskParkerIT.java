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
import static com.google.gerrit.acceptance.server.util.TaskListenerIT.await;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.server.util.TaskListenerIT.LatchedMethod;
import com.google.gerrit.acceptance.server.util.TaskListenerIT.LatchedRunnable;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.gerrit.server.git.WorkQueue.TaskListener;
import com.google.gerrit.server.git.WorkQueue.TaskParker;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;

public class TaskParkerIT extends AbstractDaemonTest {
  @SuppressWarnings("unused")
  public static class LatchedSupplier<T> {
    private CountDownLatch called = new CountDownLatch(1);
    private CountDownLatch complete = new CountDownLatch(1);
    private volatile T value;

    @SuppressWarnings("unused")
    public void assertUncalled() {
      assertThat(called.getCount()).isEqualTo(1);
    }

    public void assertAwait() {
      assertThat(await(called)).isEqualTo(true);
    }

    @SuppressWarnings("unused")
    public T assertAwaitAndSupply(T val) {
      assertAwait();
      supply(val);
      return getValue();
    }

    public T call() {
      called.countDown();
      await(complete);
      return getValue();
    }

    public void supply(T val) {
      value = val;
      complete.countDown();
    }

    protected T getValue() {
      return value;
    }
  }

  private static class ForwardingParker extends TaskListenerIT.ForwardingListener<LatchedParker>
      implements TaskParker {
    private static LatchedMethod<LatchedParker> latch;

    @SuppressWarnings("unused")
    public static void setLatched(boolean isLatched) {
      latch = isLatched ? new LatchedMethod() : null;
    }

    @SuppressWarnings("unused")
    public static LatchedParker assertAwaitAndComplete() {
      return latch.assertAwaitAndComplete();
    }

    @Override
    public boolean isReadyToStart(Task<?> task) {
      if (isDelegatable(task)) {
        return delegate.isReadyToStart(task);
      }
      return true;
    }

    @Override
    public void onNotReadyToStart(Task<?> task) {
      if (isDelegatable(task)) {
        delegate.onNotReadyToStart(task);
      }
    }
    /*
    @Override
    protected synchronized boolean isDelegatable(Task<?> task) {
      if (!super.isDelegatable(task)) {
        return false;
      }
      if (latch != null) {
        latch.call(delegate);
        setLatched(true);
      }
      return true;
    }*/
  }

  public static class LatchedParker extends TaskListenerIT.LatchedListener implements TaskParker {
    public volatile LatchedMethod<Boolean> isReadyToStart = new LatchedMethod<>();
    public volatile LatchedMethod<?> onNotReadyToStart = new LatchedMethod<>();
    public volatile boolean isStart = true;

    @Override
    public boolean isReadyToStart(Task<?> task) {
      boolean rtn = isReadyToStart.call();
      isReadyToStart = new LatchedMethod<>();
      if (rtn) return isStart;
      else return isStart;
    }

    @Override
    public void onNotReadyToStart(Task<?> task) {
      onNotReadyToStart.call();
    }
  }

  private static ForwardingParker forwarder;
  private static ForwardingParker forwarder2;
  private static ForwardingParker forwarder3;

  private LatchedParker parker = new LatchedParker();
  public LatchedRunnable runnable = new LatchedRunnable();

  @Inject private WorkQueue workQueue;
  private ScheduledExecutorService executor;

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
    forwarder.delegate = parker;
    forwarder.task = null;
    forwarder2.delegate = null; // load only if test needs it
    forwarder2.task = null;
    forwarder3.delegate = null; // load only if test needs it
    forwarder3.task = null;
    //   private LatchedParker parker2 = new LatchedParker();
    //   private LatchedParker parker3 = new LatchedParker();
    //     forwarder2.delegate = parker2;
    //     forwarder2.task = null;
    //     forwarder3.delegate = parker3;
    //     forwarder3.task = null;
  }

  @Override
  public Module createModule() {
    return new AbstractModule() {
      @Override
      public void configure() {
        // Forwarder.delegate is empty on start to protect test parker from non test tasks
        // (such as the "Log File Compressor") interference
        forwarder = new ForwardingParker(); // Only gets bound once for all tests
        bind(TaskListener.class).annotatedWith(Exports.named("parker")).toInstance(forwarder);
        forwarder2 = new ForwardingParker();
        bind(TaskListener.class).annotatedWith(Exports.named("parker2")).toInstance(forwarder2);
        forwarder3 = new ForwardingParker();
        bind(TaskListener.class).annotatedWith(Exports.named("parker3")).toInstance(forwarder3);
      }
    };
  }

  @Test
  public void noParkFlow() throws Exception {
    assertThat(workQueue.getTasks().size()).isEqualTo(0);
    assertThat(forwarder.task).isEqualTo(null);
    parker.isReadyToStart.assertUncalled();
    parker.onNotReadyToStart.assertUncalled();
    parker.onStart.assertUncalled();
    runnable.run.assertUncalled();
    parker.onStop.assertUncalled();

    executor.execute(runnable);
    assertThat(workQueue.getTasks().size()).isEqualTo(1);

    parker.isReadyToStart.assertAwait();
    assertThat(workQueue.getTasks().size()).isEqualTo(1);
    parker.onNotReadyToStart.assertUncalled();
    parker.onStart.assertUncalled();
    runnable.run.assertUncalled();
    parker.onStop.assertUncalled();

    parker.isReadyToStart.complete();
    parker.onStart.assertAwait();
    assertThat(workQueue.getTasks().size()).isEqualTo(1);
    parker.onNotReadyToStart.assertUncalled();
    runnable.run.assertUncalled();
    parker.onStop.assertUncalled();

    parker.onStart.complete();
    runnable.run.assertAwait();
    assertThat(workQueue.getTasks().size()).isEqualTo(1);
    parker.onNotReadyToStart.assertUncalled();
    parker.onStop.assertUncalled();

    runnable.run.complete();
    parker.onStop.assertAwait();
    assertThat(workQueue.getTasks().size()).isEqualTo(1);
    parker.onNotReadyToStart.assertUncalled();

    parker.onStop.complete();
    assertTaskCountIsEventually(0);
    parker.onNotReadyToStart.assertUncalled();
  }

  @Test
  public void parkFirstSoSecondRuns() throws Exception {
    LatchedRunnable runnable2 = new LatchedRunnable();

    executor.execute(runnable);
    parker.isReadyToStart.assertAwait();

    executor.execute(runnable2);
    assertThat(workQueue.getTasks().size()).isEqualTo(2);
    parker.onNotReadyToStart.assertUncalled();
    parker.onStart.assertUncalled();
    runnable.run.assertUncalled();
    parker.onStop.assertUncalled();

    parker.isStart = false;
    parker.isReadyToStart.set(false);
    parker.isReadyToStart.complete();
    runnable2.run.assertAwait();
    assertThat(workQueue.getTasks().size()).isEqualTo(2);
    parker.onNotReadyToStart.assertUncalled();
    parker.onStart.assertUncalled();
    runnable.run.assertUncalled();
    parker.onStop.assertUncalled();

    parker.isStart = true;
    parker.isReadyToStart.set(true);
    runnable2.run.complete();
    parker.isReadyToStart.assertAwait();
    parker.onNotReadyToStart.assertUncalled();
    parker.onStart.assertUncalled();
    runnable.run.assertUncalled();
    parker.onStop.assertUncalled();

    parker.isReadyToStart.complete();
    parker.onStart.assertAwait();
    assertTaskCountIsEventually(1);
    parker.onNotReadyToStart.assertUncalled();
    runnable.run.assertUncalled();
    parker.onStop.assertUncalled();

    parker.onStart.complete();
    runnable.run.assertAwait();
    assertThat(workQueue.getTasks().size()).isEqualTo(1);
    parker.onNotReadyToStart.assertUncalled();
    parker.onStop.assertUncalled();

    runnable.run.complete();
    parker.onStop.assertAwait();
    assertThat(workQueue.getTasks().size()).isEqualTo(1);
    parker.onNotReadyToStart.assertUncalled();

    parker.onStop.complete();
    assertTaskCountIsEventually(0);
    parker.onNotReadyToStart.assertUncalled();
  }

  private void assertTaskCountIsEventually(int count) throws InterruptedException {
    TaskListenerIT.assertTaskCountIsEventually(workQueue, count);
  }
}
