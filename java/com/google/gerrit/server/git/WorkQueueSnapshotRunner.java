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

package com.google.gerrit.server.git;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runnable that captures a point-in-time snapshot of the {@link WorkQueue}, writing one row per
 * task to {@link WorkQueueSnapshotLogFile}.
 */
@Singleton
public class WorkQueueSnapshotRunner implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static class Lifecycle implements LifecycleListener {
    private final WorkQueue queue;
    private final WorkQueueSnapshotRunner runner;
    private final WorkQueueSnapshotConfig config;

    @Inject
    Lifecycle(WorkQueue queue, WorkQueueSnapshotRunner runner, WorkQueueSnapshotConfig config) {
      this.queue = queue;
      this.runner = runner;
      this.config = config;
    }

    @Override
    public void start() {
      config
          .getSchedule()
          .ifPresent(
              s -> {
                queue.scheduleAtFixedRate(runner, s);
                logger.atInfo().log("Work queue snapshots scheduled every %d ms", s.interval());
              });
    }

    @Override
    public void stop() {
      // handled by WorkQueue.stop() already
    }
  }

  private final WorkQueue workQueue;
  private final WorkQueueSnapshotLogFile logFile;
  private final AtomicLong snapshotSeq = new AtomicLong();

  @Inject
  WorkQueueSnapshotRunner(WorkQueue workQueue, WorkQueueSnapshotLogFile logFile) {
    this.workQueue = workQueue;
    this.logFile = logFile;
  }

  @Override
  public void run() {
    try {
      long seq = snapshotSeq.incrementAndGet();
      long snapshotAtMs = TimeUtil.nowMs();
      List<TaskInfo> tasks = workQueue.getTaskInfos(TaskInfo::new);
      logFile.writeSummary(seq, snapshotAtMs, tasks.size());
      for (TaskInfo task : tasks) {
        logFile.write(seq, snapshotAtMs, task);
      }
    } catch (RuntimeException e) {
      logger.atSevere().withCause(e).log("Work queue snapshot failed");
    }
  }

  @Override
  public String toString() {
    return "Work queue snapshot";
  }
}
