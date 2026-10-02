// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.cache.h2;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.ModuleImpl;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.PersistentCacheFactory;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.logging.LoggingContextAwareExecutorService;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.lib.Config;

@ModuleImpl(name = CacheModule.PERSISTENT_MODULE)
public class H2CacheModule extends LifecycleModule {
  private final ImmutableSet<CacheOptions> options;

  public H2CacheModule(Set<CacheOptions> options) {
    this.options = ImmutableSet.copyOf(options);
  }

  public H2CacheModule() {
    this(EnumSet.allOf(CacheOptions.class));
  }

  @Override
  protected void configure() {
    bind(AtomicBoolean.class)
        .annotatedWith(Names.named("DiskCacheReadOnly"))
        .toInstance(new AtomicBoolean(false));
    bind(PersistentCacheFactory.class).to(H2CacheFactory.class);
    listener().to(H2CacheFactory.class);
  }

  @Provides
  @Singleton
  @Nullable
  @CacheCleanupExecutor
  ScheduledExecutorService createDiskCachePruneExecutor(WorkQueue workQueue, SitePaths site) {
    if (options.contains(CacheOptions.CACHE_CLEANUP) && site.cache_dir != null) {
      return workQueue.createQueue(1, "DiskCache-Prune", true);
    }
    return null;
  }

  @Provides
  @Singleton
  @Nullable
  @CacheStoreExecutor
  ExecutorService createDiskCacheStoreExecutor(SitePaths site) {
    if (site.cache_dir != null) {
      return new LoggingContextAwareExecutorService(
          Executors.newFixedThreadPool(
              1, new ThreadFactoryBuilder().setNameFormat("DiskCache-Store-%d").build()));
    }
    return null;
  }

  @Provides
  @Singleton
  @Nullable
  @CacheStoreStartupExecutor
  ExecutorService createDiskCacheStoreStartupExecutor(
      SitePaths site, @GerritServerConfig Config cfg) {
    if (site.cache_dir == null) {
      return null;
    }
    int startupThreads = cfg.getInt("cache", null, "startupThreads", 1);
    if (startupThreads > 1) {
      return new LoggingContextAwareExecutorService(
          Executors.newFixedThreadPool(
              1, new ThreadFactoryBuilder().setNameFormat("DiskCache-Store-Startup-%d").build()));
    }
    return null;
  }

  @Provides
  Set<CacheOptions> getOptions() {
    return options;
  }
}
