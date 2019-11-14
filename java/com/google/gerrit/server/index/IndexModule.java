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

import com.google.common.base.Ticker;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.index.IndexType;
import com.google.gerrit.index.SchemaDefinitions;
import com.google.gerrit.index.project.ProjectIndexCollection;
import com.google.gerrit.index.project.ProjectIndexRewriter;
import com.google.gerrit.index.project.ProjectIndexer;
import com.google.gerrit.index.project.ProjectSchemaDefinitions;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.MultiProgressMonitor;
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
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.gerrit.server.index.group.GroupIndexDefinition;
import com.google.gerrit.server.index.group.GroupIndexRewriter;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.gerrit.server.index.group.GroupIndexerImpl;
import com.google.gerrit.server.index.group.GroupSchemaDefinitions;
import com.google.gerrit.server.index.options.IsFirstInsertForEntry;
import com.google.gerrit.server.index.project.ProjectIndexDefinition;
import com.google.gerrit.server.index.project.ProjectIndexerImpl;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

/**
 * Module for non-indexer-specific secondary index setup.
 *
 * <p>This module should not be used directly except by specific secondary indexer implementations
 * (e.g. Lucene).
 */
public class IndexModule extends LifecycleModule {
  public static final ImmutableCollection<SchemaDefinitions<?>> ALL_SCHEMA_DEFS =
      ImmutableList.of(
          AccountSchemaDefinitions.INSTANCE,
          ChangeSchemaDefinitions.INSTANCE,
          GroupSchemaDefinitions.INSTANCE,
          ProjectSchemaDefinitions.INSTANCE);

  /** Type of secondary index. */
  public static IndexType getIndexType(Injector injector) {
    Config cfg = injector.getInstance(Key.get(Config.class, GerritServerConfig.class));
    String configValue = cfg != null ? cfg.getString("index", null, "type") : null;
    return new IndexType(configValue);
  }

  private final int threads;
  private final ListeningExecutorService interactiveExecutor;
  private final ListeningExecutorService batchExecutor;
  private final boolean closeExecutorsOnShutdown;
  private final boolean slave;

  public IndexModule(int threads, boolean slave) {
    this.threads = threads;
    this.slave = slave;
    this.interactiveExecutor = null;
    this.batchExecutor = null;
    this.closeExecutorsOnShutdown = true;
  }

  public IndexModule(
      ListeningExecutorService interactiveExecutor, ListeningExecutorService batchExecutor) {
    this.threads = 0;
    this.interactiveExecutor = interactiveExecutor;
    this.batchExecutor = batchExecutor;
    this.closeExecutorsOnShutdown = false;
    slave = false;
  }

  @Override
  protected void configure() {
    factory(MultiProgressMonitor.Factory.class);
    OptionalBinder.newOptionalBinder(binder(), Ticker.class)
        .setDefault()
        .toInstance(Ticker.systemTicker());

    bind(AccountIndexRewriter.class);
    bind(AccountIndexCollection.class);
    listener().to(AccountIndexCollection.class);
    factory(AccountIndexerImpl.Factory.class);

    bind(ChangeIndexRewriter.class);
    bind(ChangeIndexCollection.class);
    listener().to(ChangeIndexCollection.class);
    factory(ChangeIndexer.Factory.class);

    bind(GroupIndexRewriter.class);
    // GroupIndexCollection is already bound very high up in SchemaModule.
    listener().to(GroupIndexCollection.class);
    factory(GroupIndexerImpl.Factory.class);

    bind(ProjectIndexRewriter.class);
    bind(ProjectIndexCollection.class);
    listener().to(ProjectIndexCollection.class);
    factory(ProjectIndexerImpl.Factory.class);

    if (closeExecutorsOnShutdown) {
      // The executors must be shutdown _before_ closing the indexes.
      // On Gerrit start the LifecycleListeners are invoked in the order in which they are
      // registered, but on shutdown of Gerrit the order is reversed. This means the
      // LifecycleListener to shutdown the executors must be registered _after_ the
      // LifecycleListeners that close the indexes. The closing of the indexes is done by
      // *IndexCollection which have been registered as LifecycleListener above. The
      // registration of the ShutdownIndexExecutors LifecycleListener must happen afterwards.
      listener().to(ShutdownIndexExecutors.class);
    }

    DynamicSet.setOf(binder(), OnlineUpgradeListener.class);
    OptionalBinder.newOptionalBinder(binder(), IsFirstInsertForEntry.class)
        .setDefault()
        .toInstance(IsFirstInsertForEntry.NO);
    DynamicMap.mapOf(binder(), WorkQueue.TaskListener.class);
  }

  @Provides
  Collection<IndexDefinition<?, ?, ?>> getIndexDefinitions(
      AccountIndexDefinition accounts,
      ChangeIndexDefinition changes,
      GroupIndexDefinition groups,
      ProjectIndexDefinition projects) {
    if (slave) {
      // In slave mode, we only have the group index.
      return ImmutableList.of(groups);
    }

    Collection<IndexDefinition<?, ?, ?>> result =
        ImmutableList.of(accounts, groups, changes, projects);
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
  GroupIndexer getGroupIndexer(GroupIndexerImpl.Factory factory, GroupIndexCollection indexes) {
    return factory.create(indexes);
  }

  @Provides
  @Singleton
  ProjectIndexer getProjectIndexer(
      ProjectIndexerImpl.Factory factory, ProjectIndexCollection indexes) {
    return factory.create(indexes);
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
    if (threads == 0) {
      threads =
          config.getInt(
              "index", null, "threads", Runtime.getRuntime().availableProcessors() / 2 + 1);
    }
    if (threads < 0) {
      return MoreExecutors.newDirectExecutorService();
    }
    return MoreExecutors.listeningDecorator(
        workQueue.createQueue(threads, "Index-Interactive", true));
  }

  @Provides
  @Singleton
  @IndexExecutor(BATCH)
  ListeningExecutorService getBatchIndexExecutor(
      @GerritServerConfig Config config, WorkQueue workQueue) {
    if (batchExecutor != null) {
      return batchExecutor;
    }
    int threads = this.threads;
    if (threads == 0) {
      threads =
          config.getInt("index", null, "batchThreads", Runtime.getRuntime().availableProcessors());
    }
    if (threads < 0) {
      return MoreExecutors.newDirectExecutorService();
    }
    return MoreExecutors.listeningDecorator(workQueue.createQueue(threads, "Index-Batch", true));
  }

  @Singleton
  private static class ShutdownIndexExecutors implements LifecycleListener {
    private final ListeningExecutorService interactiveExecutor;
    private final ListeningExecutorService batchExecutor;

    @Inject
    ShutdownIndexExecutors(
        @IndexExecutor(INTERACTIVE) ListeningExecutorService interactiveExecutor,
        @IndexExecutor(BATCH) ListeningExecutorService batchExecutor) {
      this.interactiveExecutor = interactiveExecutor;
      this.batchExecutor = batchExecutor;
    }

    @Override
    public void start() {}

    @Override
    public void stop() {
      MoreExecutors.shutdownAndAwaitTermination(
          interactiveExecutor, Long.MAX_VALUE, TimeUnit.SECONDS);
      MoreExecutors.shutdownAndAwaitTermination(batchExecutor, Long.MAX_VALUE, TimeUnit.SECONDS);
    }
  }
}
