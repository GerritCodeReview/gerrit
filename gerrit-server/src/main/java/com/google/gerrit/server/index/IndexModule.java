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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.server.index;

import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.Executor;
import com.google.gerrit.server.query.change.IndexRewrite;
import com.google.gerrit.server.query.change.IndexRewriteImpl;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

/**
 * Module for non-indexer-specific secondary index setup.
 * <p>
 * This module should not be used directly except by specific secondary indexer
 * implementations (e.g. Lucene).
 */
public class IndexModule extends AbstractModule {
  public static boolean isEnabled(Injector injector) {
    return injector.getInstance(Key.get(Config.class, GerritServerConfig.class))
        .getBoolean("index", null, "enabled", false);
  }

  private final int threads;

  public IndexModule(int threads) {
    this.threads = threads;
  }

  @Override
  protected void configure() {
    bind(ChangeIndexer.class).to(ChangeIndexerImpl.class);
    bind(IndexRewrite.class).to(IndexRewriteImpl.class);
  }

  @Provides
  @Singleton
  @IndexExecutor
  ListeningScheduledExecutorService getIndexExecutor(
      @GerritServerConfig Config config,
      WorkQueue workQueue) {
    int threads = this.threads;
    if (threads <= 0) {
      threads = config.getInt("index", null, "threads", 0);
    }
    Executor executor;
    if (threads <= 0) {
      executor = workQueue.getDefaultQueue();
    } else {
      executor = workQueue.createQueue(threads, "index");
    }
    return MoreExecutors.listeningDecorator(executor);
  }
}
