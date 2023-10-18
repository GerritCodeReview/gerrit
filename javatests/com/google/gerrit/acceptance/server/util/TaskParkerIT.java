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

import com.google.gerrit.acceptance.server.util.TaskListenerIT.LatchedMethod;
import com.google.gerrit.acceptance.server.util.TaskListenerIT.LatchedRunnable;
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
import com.google.gerrit.server.git.WorkQueue.TaskParker;

public class TaskParkerIT extends AbstractDaemonTest {
  private static class ForwardingParker extends TaskListenerIT.ForwardingListener<TaskParker> implements TaskParker {
    @Override
    public boolean isReadyToStart(Task<?> task) {
      if (delegate != null) {
        if (this.task == null || this.task == task) {
          this.task = task;
          return delegate.isReadyToStart(task);
        }
      }
      return true;
    }

    @Override
    public void onNotReadyToStart(Task<?> task) {
      if (delegate != null) {
        if (this.task == task) {
          delegate.onNotReadyToStart(task);
        }
      }
    }
  }

  public static class LatchedParker extends TaskListenerIT.LatchedListener implements TaskParker {
    public volatile LatchedMethod isReadyToStart = new LatchedMethod();
    public volatile LatchedMethod onNotReadyToStart = new LatchedMethod();
    public volatile boolean isStart = true;

    @Override
    public boolean isReadyToStart(Task<?> task) {
      isReadyToStart.call();
      return isStart;
    }

    @Override
    public void onNotReadyToStart(Task<?> task) {
      onNotReadyToStart.call();
    }
  }

  private static ForwardingParker forwarder;

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
    parker.isReadyToStart.complete();
    runnable2.run.assertAwait();
    assertThat(workQueue.getTasks().size()).isEqualTo(2);
    parker.onNotReadyToStart.assertUncalled();
    parker.onStart.assertUncalled();
    runnable.run.assertUncalled();
    parker.onStop.assertUncalled();

    parker.isReadyToStart = new LatchedMethod();
    parker.isStart = true;
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
