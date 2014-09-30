// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.sshd;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.QueueProvider;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;

import java.util.concurrent.ThreadFactory;

public class CommandExecutorQueueProvider implements QueueProvider {

  private int poolSize;
  private final int batchThreads;
  private final WorkQueue.Executor interactiveExecutor;
  private final WorkQueue.Executor batchExecutor;

  @Inject
  public CommandExecutorQueueProvider(@GerritServerConfig final Config config,
      final WorkQueue queues) {
    final int cores = Runtime.getRuntime().availableProcessors();
    poolSize = config.getInt("sshd", "threads", 3 * cores / 2);
    batchThreads = config.getInt("sshd", "batchThreads", 0);
    if (batchThreads > poolSize) {
      poolSize += batchThreads;
    }
    int interactiveThreads = Math.max(1, poolSize - batchThreads);
    interactiveExecutor = queues.createQueue(interactiveThreads,
        "SSH-Interactive-Worker");
    if (batchThreads !=  0) {
      batchExecutor = queues.createQueue(batchThreads, "SSH-Batch-Worker");
      setThreadFactory(batchExecutor);
    } else {
      batchExecutor = interactiveExecutor;
    }
    setThreadFactory(interactiveExecutor);

  }

  private void setThreadFactory(WorkQueue.Executor executor) {
    final ThreadFactory parent = executor.getThreadFactory();
    executor.setThreadFactory(new ThreadFactory() {
      @Override
      public Thread newThread(final Runnable task) {
        final Thread t = parent.newThread(task);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
      }
    });
  }

  @Override
  public WorkQueue.Executor getQueue(QueueType type) {
    switch (type) {
      case INTERACTIVE:
        return interactiveExecutor;
      case BATCH:
      default:
        return batchExecutor;
    }
  }
}
