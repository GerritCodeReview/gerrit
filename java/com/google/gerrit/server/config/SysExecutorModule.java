// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.config;

import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.server.CacheRefreshExecutor;
import com.google.gerrit.server.FanOutExecutor;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.util.concurrent.ExecutorService;
import org.eclipse.jgit.lib.Config;

/**
 * Module providing different executors.
 *
 * <p>This module is intended to be installed at the top level when creating a {@code sysInjector}
 * in {@code Daemon} or similar, not nested in another module. This ensures the module can be
 * swapped out for the googlesource.com implementation.
 */
public class SysExecutorModule extends AbstractModule {
  @Override
  protected void configure() {}

  @Provides
  @Singleton
  @ReceiveCommitsExecutor
  public ExecutorService provideReceiveCommitsExecutor(
      @GerritServerConfig Config config, WorkQueue queues) {
    int poolSize =
        config.getInt(
            "receive", null, "threadPoolSize", Runtime.getRuntime().availableProcessors());
    return queues.createQueue(poolSize, "ReceiveCommits", true);
  }

  @Provides
  @Singleton
  @AsyncPostUpdateExecutor
  public ExecutorService provideAsyncPostUpdateExecutor(
      @GerritServerConfig Config config, WorkQueue queues) {
    // sendemail.threadPoolSize is deprecated and overridden by asyncPostUpdate.threadPoolSize.
    int poolSize =
        config.getInt(
            "asyncPostUpdate",
            null,
            "threadPoolSize",
            config.getInt("sendemail", null, "threadPoolSize", 1));
    if (poolSize == 0) {
      return newDirectExecutorService();
    }
    return queues.createQueue(poolSize, "AsyncPostUpdate", true);
  }

  @Provides
  @Singleton
  @FanOutExecutor
  public ExecutorService provideFanOutExecutor(
      @GerritServerConfig Config config, WorkQueue queues) {
    int poolSize = config.getInt("execution", null, "fanOutThreadPoolSize", 25);
    if (poolSize == 0) {
      return newDirectExecutorService();
    }
    return queues.createQueue(poolSize, "FanOut");
  }

  @Provides
  @Singleton
  @CacheRefreshExecutor
  public ListeningExecutorService provideCacheRefreshExecutor(
      @GerritServerConfig Config config, WorkQueue queues) {
    int poolSize = config.getInt("cache", null, "refreshThreadPoolSize", 2);
    if (poolSize == 0) {
      return newDirectExecutorService();
    }
    return MoreExecutors.listeningDecorator(queues.createQueue(poolSize, "CacheRefresh"));
  }
}
