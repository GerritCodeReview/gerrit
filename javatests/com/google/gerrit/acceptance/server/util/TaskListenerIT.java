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
  private static class ForwardingListener implements TaskListener {
    public volatile TaskListener delegate;

    @Override
    public void onStart(Task<?> task) {
      if (delegate != null) {
        delegate.onStart(task);
      }
    }

    @Override
    public void onStop(Task<?> task) {
      if (delegate != null) {
        delegate.onStop(task);
      }
    }
  }

  public class LatchedListener implements TaskListener {
    @Override
    public void onStart(Task<?> task) {
      onStart.countDown();
      await(start);
    }

    @Override
    public void onStop(Task<?> task) {
      onStop.countDown();
      await(stop);
    }
  }

  public static final int AWAIT_TIMEOUT = 10;
  public static final TimeUnit AWAIT_TIMEUNIT = TimeUnit.SECONDS;
  public static final long MS_EMPTY_QUEUE =
      TimeUnit.MILLISECONDS.convert(AWAIT_TIMEOUT, AWAIT_TIMEUNIT);

  private static ForwardingListener forwarder;

  public LatchedListener listener = new LatchedListener();

  @Inject private WorkQueue workQueue;
  private ScheduledExecutorService executor;

  private CountDownLatch onStart = new CountDownLatch(1);
  private CountDownLatch start = new CountDownLatch(1);
  private CountDownLatch onRun = new CountDownLatch(1);
  private CountDownLatch run = new CountDownLatch(1);
  private CountDownLatch onStop = new CountDownLatch(1);
  private CountDownLatch stop = new CountDownLatch(1);

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
    forwarder.delegate = listener;
  }

  @Override
  public Module createModule() {
    return new AbstractModule() {
      @Override
      public void configure() {
        // Forwarder.delegate is empty on start to protect test listener from non test tasks
        // (such as the "Log File Compressor") interference
        forwarder = new ForwardingListener();
        bind(TaskListener.class).annotatedWith(Exports.named("listener")).toInstance(forwarder);
      }
    };
  }

  @Test
  public void onStartThenRunThenOnStopAreCalled() throws Exception {
    assertThat(onStart.getCount()).isEqualTo(1);
    assertThat(onRun.getCount()).isEqualTo(1);
    assertThat(onStop.getCount()).isEqualTo(1);

    executor.execute(
        () -> {
          onRun.countDown();
          await(run);
        });
    assertThat(workQueue.getTasks().size()).isEqualTo(1);

    assertThat(await(onStart)).isEqualTo(true);
    assertThat(workQueue.getTasks().size()).isEqualTo(1);
    assertThat(onRun.getCount()).isEqualTo(1);
    assertThat(onStop.getCount()).isEqualTo(1);
    assertThat(workQueue.getTasks().size()).isEqualTo(1);

    start.countDown();
    assertThat(await(onRun)).isEqualTo(true);
    assertThat(workQueue.getTasks().size()).isEqualTo(1);
    assertThat(onStop.getCount()).isEqualTo(1);

    run.countDown();
    assertThat(await(onStop)).isEqualTo(true);
    assertThat(workQueue.getTasks().size()).isEqualTo(1);

    stop.countDown();
    long ms = 0;
    while (0 != workQueue.getTasks().size()) {
      assertThat(ms++).isLessThan(MS_EMPTY_QUEUE);
      TimeUnit.MILLISECONDS.sleep(1);
    }
  }

  private boolean await(CountDownLatch latch) {
    try {
      return latch.await(AWAIT_TIMEOUT, AWAIT_TIMEUNIT);
    } catch (InterruptedException e) {
    }
    return false;
  }
}
