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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;

public class TaskListenerIT extends AbstractDaemonTest {
  public TaskListener listener =
      new TaskListener() {
        @Override
        public void onStart(Task<?> task) {
          started = true;
        }

        @Override
        public void onStop(Task<?> task) {
          stopped = true;
        }
      };

  @Inject private WorkQueue workQueue;
  private ScheduledExecutorService executor;

  private volatile boolean started;
  private volatile boolean stopped;

  @Before
  public void setupExecutor() {
    executor = workQueue.getDefaultQueue();
  }

  @Override
  public Module createModule() {
    return new AbstractModule() {
      @Override
      public void configure() {
        bind(TaskListener.class).annotatedWith(Exports.named("listener")).toInstance(listener);
      }
    };
  }

  @Before
  public void resetListener() {
    started = false;
    stopped = false;
  }

  @Test
  public void onStartAndStopAreCalled() throws Exception {
    executeOne();
    assertThat(started).isEqualTo(true);
    assertThat(stopped).isEqualTo(true);
  }

  private void executeOne() throws InterruptedException {
    int count = workQueue.getTasks().size();
    executor.execute(
        new Runnable() {
          @Override
          public void run() {};
        });
    while (count != workQueue.getTasks().size()) {
      TimeUnit.MILLISECONDS.sleep(1);
    }
  }
}
