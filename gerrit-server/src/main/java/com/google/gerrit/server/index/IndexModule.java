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

package com.google.gerrit.server.index;

import static com.google.gerrit.server.git.QueueProvider.QueueType.BATCH;
import static com.google.gerrit.server.git.QueueProvider.QueueType.INTERACTIVE;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.query.change.BasicChangeRewrites;
import com.google.gerrit.server.query.change.ChangeQueryRewriter;
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
public class IndexModule extends LifecycleModule {
  public enum IndexType {
    LUCENE, SOLR, ELASTICSEARCH
  }

  /** Type of secondary index. */
  public static IndexType getIndexType(Injector injector) {
    Config cfg = injector.getInstance(
        Key.get(Config.class, GerritServerConfig.class));
    return cfg.getEnum("index", null, "type", IndexType.LUCENE);
  }

  private final int threads;
  private final ListeningExecutorService interactiveExecutor;
  private final ListeningExecutorService batchExecutor;

  public IndexModule(int threads) {
    this.threads = threads;
    this.interactiveExecutor = null;
    this.batchExecutor = null;
  }

  public IndexModule(ListeningExecutorService interactiveExecutor,
      ListeningExecutorService batchExecutor) {
    this.threads = -1;
    this.interactiveExecutor = interactiveExecutor;
    this.batchExecutor = batchExecutor;
  }

  @Override
  protected void configure() {
    bind(ChangeQueryRewriter.class).to(IndexRewriteImpl.class);
    bind(BasicChangeRewrites.class);
    bind(IndexCollection.class);
    listener().to(IndexCollection.class);
    factory(ChangeIndexer.Factory.class);
  }

  @Provides
  @Singleton
  ChangeIndexer getChangeIndexer(
      @IndexExecutor(INTERACTIVE) ListeningExecutorService executor,
      ChangeIndexer.Factory factory,
      IndexCollection indexes) {
    // Bind default indexer to interactive executor; callers who need a
    // different executor can use the factory directly.
    return factory.create(executor, indexes);
  }

  @Provides
  @Singleton
  @IndexExecutor(INTERACTIVE)
  ListeningExecutorService getInteractiveIndexExecutor(
      @GerritServerConfig Config config,
      WorkQueue workQueue) {
    if (interactiveExecutor != null) {
      return interactiveExecutor;
    }
    int threads = this.threads;
    if (threads <= 0) {
      threads = config.getInt("index", null, "threads", 0);
    }
    if (threads <= 0) {
      threads =
          config.getInt("changeMerge", null, "interactiveThreadPoolSize", 0);
    }
    if (threads <= 0) {
      return MoreExecutors.newDirectExecutorService();
    }
    return MoreExecutors.listeningDecorator(
        workQueue.createQueue(threads, "Index-Interactive"));
  }

  @Provides
  @Singleton
  @IndexExecutor(BATCH)
  ListeningExecutorService getBatchIndexExecutor(
      @IndexExecutor(INTERACTIVE) ListeningExecutorService interactive,
      @GerritServerConfig Config config,
      WorkQueue workQueue) {
    if (batchExecutor != null) {
      return batchExecutor;
    }
    int threads = config.getInt("index", null, "batchThreads", 0);
    if (threads <= 0) {
      threads = config.getInt("changeMerge", null, "threadPoolSize", 0);
    }
    if (threads <= 0) {
      return interactive;
    }
    return MoreExecutors.listeningDecorator(
        workQueue.createQueue(threads, "Index-Batch"));
  }
}
