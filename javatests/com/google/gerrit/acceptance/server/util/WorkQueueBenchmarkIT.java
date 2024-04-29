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
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Stopwatch;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;

public class WorkQueueBenchmarkIT extends AbstractDaemonTest {
  private static class NoOpRunnable implements Runnable {
    @Override
    public void run() {}
  }

  @Inject private WorkQueue workQueue;

  private ScheduledExecutorService executor;

  @Before
  public void setupExecutorAndForwarder() throws InterruptedException {
    executor = workQueue.createQueue(1, "default");
    while (0 != workQueue.getTasks().size()) {
      for (Task<?> t : workQueue.getTasks()) {
        @SuppressWarnings("unused")
        boolean unused = t.cancel(true);
      }
      TimeUnit.MILLISECONDS.sleep(1);
    }
    assertTaskCountIsEventually(0);
  }

  @Test
  public void benchmark() throws InterruptedException {
    Stopwatch sw = Stopwatch.createStarted();
    for (int i = 0; i < 500000; i++) {
      executor.execute(new NoOpRunnable());
    }
    assertTaskCountIsEventually(0);
    assertWithMessage("benchmark took " + sw.elapsed(TimeUnit.MILLISECONDS) + "ms")
        .that(true)
        .isFalse();
  }

  public void assertTaskCountIsEventually(int count) throws InterruptedException {
    long ms = 0;
    while (count != workQueue.getTasks().size()) {
      assertThat(ms++).isLessThan(60000);
      TimeUnit.MILLISECONDS.sleep(1);
    }
  }
}
