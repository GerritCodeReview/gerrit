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

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.index.account.AccountIndexCollection;
import com.google.gerrit.server.index.account.AccountIndexDefinition;
import com.google.gerrit.server.index.account.AccountIndexRewriter;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.gerrit.server.index.account.AccountIndexerImpl;
import com.google.gerrit.server.index.account.AccountSchemaDefinitions;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.change.ChangeIndexDefinition;
import com.google.gerrit.server.index.change.ChangeIndexRewriter;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.Set;
import org.eclipse.jgit.lib.Config;

/**
 * Module for non-indexer-specific secondary index setup.
 *
 * <p>This module should not be used directly except by specific secondary indexer implementations
 * (e.g. Lucene).
 */
public class IndexModule extends LifecycleModule {
  public enum IndexType {
    LUCENE,
    ELASTICSEARCH
  }

  public static final ImmutableCollection<SchemaDefinitions<?>> ALL_SCHEMA_DEFS =
      ImmutableList.<SchemaDefinitions<?>>of(
          AccountSchemaDefinitions.INSTANCE, ChangeSchemaDefinitions.INSTANCE);

  /** Type of secondary index. */
  public static IndexType getIndexType(Injector injector) {
    Config cfg = injector.getInstance(Key.get(Config.class, GerritServerConfig.class));
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

  public IndexModule(
      ListeningExecutorService interactiveExecutor, ListeningExecutorService batchExecutor) {
    this.threads = -1;
    this.interactiveExecutor = interactiveExecutor;
    this.batchExecutor = batchExecutor;
  }

  @Override
  protected void configure() {
    bind(AccountIndexRewriter.class);
    bind(AccountIndexCollection.class);
    listener().to(AccountIndexCollection.class);
    factory(AccountIndexerImpl.Factory.class);

    bind(ChangeIndexRewriter.class);
    bind(ChangeIndexCollection.class);
    listener().to(ChangeIndexCollection.class);
    factory(ChangeIndexer.Factory.class);
  }

  @Provides
  Collection<IndexDefinition<?, ?, ?>> getIndexDefinitions(
      AccountIndexDefinition accounts, ChangeIndexDefinition changes) {
    Collection<IndexDefinition<?, ?, ?>> result =
        ImmutableList.<IndexDefinition<?, ?, ?>>of(accounts, changes);
    Set<String> expected =
        FluentIterable.from(ALL_SCHEMA_DEFS).transform(SchemaDefinitions::getName).toSet();
    Set<String> actual = FluentIterable.from(result).transform(IndexDefinition::getName).toSet();
    if (!expected.equals(actual)) {
      throw new ProvisionException(
          "need index definitions for all schemas: " + expected + " != " + actual);
    }
    return result;
  }

  @Provides
  @Singleton
  AccountIndexer getAccountIndexer(
      AccountIndexerImpl.Factory factory, AccountIndexCollection indexes) {
    return factory.create(indexes);
  }

  @Provides
  @Singleton
  ChangeIndexer getChangeIndexer(
      @IndexExecutor(INTERACTIVE) ListeningExecutorService executor,
      ChangeIndexer.Factory factory,
      ChangeIndexCollection indexes) {
    // Bind default indexer to interactive executor; callers who need a
    // different executor can use the factory directly.
    return factory.create(executor, indexes);
  }

  @Provides
  @Singleton
  @IndexExecutor(INTERACTIVE)
  ListeningExecutorService getInteractiveIndexExecutor(
      @GerritServerConfig Config config, WorkQueue workQueue) {
    if (interactiveExecutor != null) {
      return interactiveExecutor;
    }
    int threads = this.threads;
    if (threads <= 0) {
      threads = config.getInt("index", null, "threads", 0);
    }
    if (threads <= 0) {
      threads = Runtime.getRuntime().availableProcessors() / 2 + 1;
    }
    return MoreExecutors.listeningDecorator(workQueue.createQueue(threads, "Index-Interactive"));
  }

  @Provides
  @Singleton
  @IndexExecutor(BATCH)
  ListeningExecutorService getBatchIndexExecutor(
      @GerritServerConfig Config config, WorkQueue workQueue) {
    if (batchExecutor != null) {
      return batchExecutor;
    }
    int threads = config.getInt("index", null, "batchThreads", 0);
    if (threads <= 0) {
      threads = Runtime.getRuntime().availableProcessors();
    }
    return MoreExecutors.listeningDecorator(workQueue.createQueue(threads, "Index-Batch"));
  }
}
