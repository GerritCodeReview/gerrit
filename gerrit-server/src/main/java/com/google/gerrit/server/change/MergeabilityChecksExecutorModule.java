// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.gerrit.server.change.MergeabilityChecksExecutor.Priority;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

/** Module providing the {@link MergeabilityChecksExecutor}. */
public class MergeabilityChecksExecutorModule extends AbstractModule {
  @Override
  protected void configure() {
  }

  @Provides
  @Singleton
  @MergeabilityChecksExecutor(Priority.BACKGROUND)
  public WorkQueue.Executor createMergeabilityChecksExecutor(
      @GerritServerConfig Config config,
      WorkQueue queues) {
    int poolSize = config.getInt("changeMerge", null, "threadPoolSize", 1);
    return queues.createQueue(poolSize, "MergeabilityChecks");
  }

  @Provides
  @Singleton
  @MergeabilityChecksExecutor(Priority.INTERACTIVE)
  public WorkQueue.Executor createMergeabilityChecksExecutor(
      @GerritServerConfig Config config,
      WorkQueue queues,
      @MergeabilityChecksExecutor(Priority.BACKGROUND)
        WorkQueue.Executor backgroundExecutor) {
    String poolSizeStr =
        config.getString("changeMerge", null, "interactiveThreadPoolSize");
    if (poolSizeStr == null) {
      return backgroundExecutor;
    }
    return queues.createQueue(Integer.parseInt(poolSizeStr),
        "InteractiveMergeabilityChecks");
  }
}
