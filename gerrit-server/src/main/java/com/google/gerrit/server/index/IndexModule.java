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

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.query.change.ChangeQueryRewriter;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;

import org.eclipse.jgit.lib.Config;

/**
 * Module for non-indexer-specific secondary index setup.
 * <p>
 * This module should not be used directly except by specific secondary indexer
 * implementations (e.g. Lucene).
 */
public class IndexModule extends LifecycleModule {
  public enum IndexType {
    SQL, LUCENE, SOLR;
  }

  /** Type of secondary index. */
  public static IndexType getIndexType(Injector injector) {
    Config cfg = injector.getInstance(
        Key.get(Config.class, GerritServerConfig.class));
    return cfg.getEnum("index", null, "type", IndexType.SQL);
  }

  private final int threads;
  private final ListeningExecutorService indexExecutor;

  public IndexModule(int threads) {
    this.threads = threads;
    this.indexExecutor = null;
  }

  public IndexModule(ListeningExecutorService indexExecutor) {
    this.threads = -1;
    this.indexExecutor = indexExecutor;
  }

  @Override
  protected void configure() {
    bind(ChangeQueryRewriter.class).to(IndexRewriteImpl.class);
    bind(IndexRewriteImpl.BasicRewritesImpl.class);
    bind(IndexCollection.class);
    listener().to(IndexCollection.class);
    install(new FactoryModuleBuilder()
        .implement(ChangeIndexer.class, ChangeIndexerImpl.class)
        .build(ChangeIndexer.Factory.class));

    if (indexExecutor != null) {
      bind(ListeningExecutorService.class)
          .annotatedWith(IndexExecutor.class)
          .toInstance(indexExecutor);
    } else {
      install(new IndexExecutorModule(threads));
    }
  }

  @Provides
  ChangeIndexer getChangeIndexer(
      ChangeIndexer.Factory factory,
      IndexCollection indexes) {
    return factory.create(indexes);
  }

  private static class IndexExecutorModule extends AbstractModule {
    private final int threads;

    private IndexExecutorModule(int threads) {
      this.threads = threads;
    }

    @Override
    public void configure() {
    }

    @Provides
    @Singleton
    @IndexExecutor
    ListeningExecutorService getIndexExecutor(
        @GerritServerConfig Config config,
        WorkQueue workQueue) {
      int threads = this.threads;
      if (threads <= 0) {
        threads = config.getInt("index", null, "threads", 0);
      }
      if (threads <= 0) {
        return MoreExecutors.sameThreadExecutor();
      }
      return MoreExecutors.listeningDecorator(
          workQueue.createQueue(threads, "index"));
    }
  }
}
