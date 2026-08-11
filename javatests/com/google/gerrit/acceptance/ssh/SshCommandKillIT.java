// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.acceptance.ssh;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.restapi.config.ListTasks;
import com.google.gerrit.server.restapi.config.ListTasks.TaskInfo;
import com.google.inject.Inject;
import com.google.inject.Module;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

@NoHttpd
@UseSsh
@Sandboxed
public class SshCommandKillIT extends AbstractDaemonTest {
  private static final long TIMEOUT_MS = 10_000;

  @Inject private WorkQueue workQueue;
  @Inject private ListTasks listTasks;

  @Override
  public Module createSshModule() {
    return new TestSshCommandModule();
  }

  @Test
  public void killedTaskBlockedOnInputLeavesTheQueue() throws Exception {
    try (ExecutorService executor = Executors.newFixedThreadPool(1)) {
      @SuppressWarnings("unused")
      var unused = executor.submit(() -> userSshSession.execAndReturnStatus("stuck-on-input"));
      StuckOnInputCommand.syncPoint.await();

      TaskInfo task = waitForTask();
      workQueue.getTask(Integer.parseUnsignedInt(task.id, 16)).cancel(true);

      waitUntil(() -> findTask().isEmpty());
    }
  }

  private TaskInfo waitForTask() throws Exception {
    waitUntil(() -> findTask().isPresent());
    return findTask().get();
  }

  private Optional<TaskInfo> findTask() {
    List<TaskInfo> tasks;
    try {
      tasks = listTasks.apply(new ConfigResource()).value();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    return tasks.stream().filter(t -> t.command.startsWith("stuck-on-input")).findFirst();
  }

  private void waitUntil(BooleanSupplierWithException condition) throws Exception {
    long deadline = System.currentTimeMillis() + TIMEOUT_MS;
    while (!condition.get()) {
      assertThat(System.currentTimeMillis()).isLessThan(deadline);
      TimeUnit.MILLISECONDS.sleep(10);
    }
  }

  private interface BooleanSupplierWithException {
    boolean get() throws Exception;
  }
}
