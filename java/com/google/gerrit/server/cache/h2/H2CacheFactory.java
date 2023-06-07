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

package com.google.gerrit.server.cache.h2;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.cache.CacheBackend;
import com.google.gerrit.server.cache.MemoryCacheFactory;
import com.google.gerrit.server.cache.PersistentCacheBaseFactory;
import com.google.gerrit.server.cache.PersistentCacheDef;
import com.google.gerrit.server.cache.h2.H2CacheImpl.SqlStore;
import com.google.gerrit.server.cache.h2.H2CacheImpl.ValueHolder;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.options.BuildBloomFilter;
import com.google.gerrit.server.index.options.IsFirstInsertForEntry;
import com.google.gerrit.server.logging.LoggingContextAwareExecutorService;
import com.google.gerrit.server.logging.LoggingContextAwareScheduledExecutorService;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

/**
 * Creates persistent caches depending on gerrit.config parameters. If the cache.directory property
 * is unset, it will fall back to in-memory caches.
 */
@Singleton
class H2CacheFactory extends PersistentCacheBaseFactory implements LifecycleListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final List<H2CacheImpl<?, ?>> caches;
  private final DynamicMap<Cache<?, ?>> cacheMap;
  private final ExecutorService executor;
  private final ScheduledExecutorService cleanup;
  private final long h2CacheSize;
  private final boolean h2AutoServer;
  private final boolean isOfflineReindex;
  private final boolean buildBloomFilter;

  @Inject
  H2CacheFactory(
      MemoryCacheFactory memCacheFactory,
      @GerritServerConfig Config cfg,
      SitePaths site,
      DynamicMap<Cache<?, ?>> cacheMap,
      @Nullable IsFirstInsertForEntry isFirstInsertForEntry,
      @Nullable BuildBloomFilter buildBloomFilter) {
    super(memCacheFactory, cfg, site);
    h2CacheSize = cfg.getLong("cache", null, "h2CacheSize", -1);
    h2AutoServer = cfg.getBoolean("cache", null, "h2AutoServer", false);
    caches = new LinkedList<>();
    this.cacheMap = cacheMap;
    this.isOfflineReindex =
        isFirstInsertForEntry != null && isFirstInsertForEntry.equals(IsFirstInsertForEntry.YES);
    this.buildBloomFilter =
        !(buildBloomFilter != null && buildBloomFilter.equals(BuildBloomFilter.FALSE));

    if (diskEnabled) {
      executor =
          new LoggingContextAwareExecutorService(
              Executors.newFixedThreadPool(
                  1, new ThreadFactoryBuilder().setNameFormat("DiskCache-Store-%d").build()));

      cleanup =
          isOfflineReindex
              ? null
              : new LoggingContextAwareScheduledExecutorService(
                  Executors.newScheduledThreadPool(
                      1,
                      new ThreadFactoryBuilder()
                          .setNameFormat("DiskCache-Prune-%d")
                          .setDaemon(true)
                          .build()));
    } else {
      executor = null;
      cleanup = null;
    }
  }

  @Override
  public void start() {
    if (executor != null) {
      for (H2CacheImpl<?, ?> cache : caches) {
        executor.execute(cache::start);
        if (cleanup != null) {
          @SuppressWarnings("unused")
          Future<?> possiblyIgnoredError =
              cleanup.schedule(() -> cache.prune(cleanup), 30, TimeUnit.SECONDS);
        }
      }
    }
  }

  @Override
  public void stop() {
    if (executor != null) {
      try {
        if (cleanup != null) {
          cleanup.shutdownNow();
        }

        List<Runnable> pending = executor.shutdownNow();
        if (executor.awaitTermination(15, TimeUnit.MINUTES)) {
          if (pending != null && !pending.isEmpty()) {
            logger.atInfo().log("Finishing %d disk cache updates", pending.size());
            for (Runnable update : pending) {
              update.run();
            }
          }
        } else {
          logger.atInfo().log("Timeout waiting for disk cache to close");
        }
      } catch (InterruptedException e) {
        logger.atWarning().log("Interrupted waiting for disk cache to shutdown");
      }
    }
    synchronized (caches) {
      for (H2CacheImpl<?, ?> cache : caches) {
        cache.stop();
      }
    }
  }

  @SuppressWarnings({"unchecked"})
  @Override
  public <K, V> Cache<K, V> buildImpl(
      PersistentCacheDef<K, V> in, long limit, CacheBackend backend) {
    H2CacheDefProxy<K, V> def = new H2CacheDefProxy<>(in);
    SqlStore<K, V> store = newSqlStore(def, limit);
    H2CacheImpl<K, V> cache =
        new H2CacheImpl<>(
            executor,
            store,
            def.keyType(),
            (Cache<K, ValueHolder<V>>) memCacheFactory.build(def, backend));
    synchronized (caches) {
      caches.add(cache);
    }
    return cache;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <K, V> LoadingCache<K, V> buildImpl(
      PersistentCacheDef<K, V> in, CacheLoader<K, V> loader, long limit, CacheBackend backend) {
    H2CacheDefProxy<K, V> def = new H2CacheDefProxy<>(in);
    SqlStore<K, V> store = newSqlStore(def, limit);
    Cache<K, ValueHolder<V>> mem =
        (Cache<K, ValueHolder<V>>)
            memCacheFactory.build(
                def,
                (CacheLoader<K, V>) new H2CacheImpl.Loader<>(executor, store, loader),
                backend);
    H2CacheImpl<K, V> cache = new H2CacheImpl<>(executor, store, def.keyType(), mem);
    synchronized (caches) {
      caches.add(cache);
    }
    return cache;
  }

  @Override
  public void onStop(String plugin) {
    synchronized (caches) {
      for (Map.Entry<String, Provider<Cache<?, ?>>> entry : cacheMap.byPlugin(plugin).entrySet()) {
        Cache<?, ?> cache = entry.getValue().get();
        if (caches.remove(cache)) {
          ((H2CacheImpl<?, ?>) cache).stop();
        }
      }
    }
  }

  private <V, K> SqlStore<K, V> newSqlStore(PersistentCacheDef<K, V> def, long maxSize) {
    StringBuilder url = new StringBuilder();
    url.append("jdbc:h2:").append(cacheDir.resolve(def.name()).toUri());
    if (h2CacheSize >= 0) {
      url.append(";CACHE_SIZE=");
      // H2 CACHE_SIZE is always given in KB
      url.append(h2CacheSize / 1024);
    }
    if (h2AutoServer) {
      url.append(";AUTO_SERVER=TRUE");
    }
    return new SqlStore<>(
        url.toString(),
        def.keyType(),
        def.keySerializer(),
        def.valueSerializer(),
        def.version(),
        maxSize,
        def.expireAfterWrite(),
        def.expireFromMemoryAfterAccess(),
        buildBloomFilter);
  }
}
