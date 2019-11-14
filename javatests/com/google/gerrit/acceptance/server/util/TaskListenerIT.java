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
  private static class LatchedRunnable implements Runnable {
    public CountDownLatch onRun = new CountDownLatch(1);
    public CountDownLatch run = new CountDownLatch(1);
    public volatile boolean runFailure;

    @Override
    public void run() {
      onRun.countDown();
      runFailure = !await(run);
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
    public CountDownLatch onStart = new CountDownLatch(1);
    public CountDownLatch start = new CountDownLatch(1);
    public CountDownLatch onStop = new CountDownLatch(1);
    public CountDownLatch stop = new CountDownLatch(1);
    public volatile boolean startFailure;
    public volatile boolean stopFailure;

    @Override
    public void onStart(Task<?> task) {
      onStart.countDown();
      startFailure = !await(start);
    }

    @Override
    public void onStop(Task<?> task) {
      onStop.countDown();
      stopFailure = !await(stop);
    }
  }

  private static final int AWAIT_TIMEOUT = 100;
  private static final TimeUnit AWAIT_TIMEUNIT = TimeUnit.MILLISECONDS;
  private static final long MS_EMPTY_QUEUE =
      TimeUnit.MILLISECONDS.convert(AWAIT_TIMEOUT, AWAIT_TIMEUNIT);

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
  }

  @Test
  public void onStartThenRunThenOnStopAreCalled() throws Exception {
    assertThat(workQueue.getTasks().size()).isEqualTo(0);
    assertThat(listener.onStart.getCount()).isEqualTo(1);
    assertThat(runnable.onRun.getCount()).isEqualTo(1);
    assertThat(listener.onStop.getCount()).isEqualTo(1);

    executor.execute(runnable);
    assertThat(workQueue.getTasks().size()).isEqualTo(1);

    assertThat(await(listener.onStart)).isEqualTo(true);
    assertThat(workQueue.getTasks().size()).isEqualTo(1);
    assertThat(runnable.onRun.getCount()).isEqualTo(1);
    assertThat(listener.onStop.getCount()).isEqualTo(1);
    assertThat(workQueue.getTasks().size()).isEqualTo(1);

    listener.start.countDown();
    assertThat(await(runnable.onRun)).isEqualTo(true);
    assertThat(listener.startFailure).isEqualTo(false);
    assertThat(workQueue.getTasks().size()).isEqualTo(1);
    assertThat(listener.onStop.getCount()).isEqualTo(1);

    runnable.run.countDown();
    assertThat(await(listener.onStop)).isEqualTo(true);
    assertThat(runnable.runFailure).isEqualTo(false);
    assertThat(workQueue.getTasks().size()).isEqualTo(1);

    listener.stop.countDown();
    assertTaskCountIsEventually(0);
    assertThat(listener.stopFailure).isEqualTo(false);
  }

  @Test
  public void firstBlocksSecond() throws Exception {
    executor.execute(runnable);
    await(listener.onStart);
    assertThat(workQueue.getTasks().size()).isEqualTo(1);

    LatchedRunnable runnable2 = new LatchedRunnable();
    executor.execute(runnable2);
    assertThat(workQueue.getTasks().size()).isEqualTo(2);

    runnable2.run.countDown();
    assertThat(workQueue.getTasks().size()).isEqualTo(2); // waiting on first
    assertThat(runnable2.runFailure).isEqualTo(false);

    listener.start.countDown();
    await(runnable.onRun);
    assertThat(listener.startFailure).isEqualTo(false);
    assertThat(workQueue.getTasks().size()).isEqualTo(2); // waiting on first

    runnable.run.countDown();
    await(listener.onStop);
    assertThat(runnable.runFailure).isEqualTo(false);
    assertThat(workQueue.getTasks().size()).isEqualTo(2); // waiting on first

    listener.stop.countDown();
    assertTaskCountIsEventually(0);
    assertThat(listener.stopFailure).isEqualTo(false);
  }

  private void assertTaskCountIsEventually(int count) throws InterruptedException {
    long ms = 0;
    while (count != workQueue.getTasks().size()) {
      assertThat(ms++).isLessThan(MS_EMPTY_QUEUE);
      TimeUnit.MILLISECONDS.sleep(1);
    }
  }

  private static boolean await(CountDownLatch latch) {
    try {
      return latch.await(AWAIT_TIMEOUT, AWAIT_TIMEUNIT);
    } catch (InterruptedException e) {
      return false;
    }
  }
}
