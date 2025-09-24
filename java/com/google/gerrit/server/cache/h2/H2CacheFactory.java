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

import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.cache.MemoryCacheFactory;
import com.google.gerrit.server.cache.PersistentCacheBaseFactory;
import com.google.gerrit.server.cache.PersistentCacheDef;
import com.google.gerrit.server.cache.h2.H2CacheImpl.SqlStore;
import com.google.gerrit.server.cache.h2.H2CacheImpl.ValueHolder;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.config.ScheduleConfig.Schedule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.lib.Config;

/**
 * Creates persistent caches depending on gerrit.config parameters. If the cache.directory property
 * is unset, it will fall back to in-memory caches.
 */
@Singleton
class H2CacheFactory extends PersistentCacheBaseFactory implements LifecycleListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final int COMPATIBILITY_VERSION = 2;

  static class PeriodicCachePruner implements Runnable {
    private final H2CacheImpl<?, ?> cache;

    PeriodicCachePruner(H2CacheImpl<?, ?> cache) {
      this.cache = cache;
    }

    @Override
    public String toString() {
      return "Disk Cache Pruner (" + cache.getCacheName() + ")";
    }

    @Override
    public void run() {
      cache.prune();
    }
  }

  private final List<H2CacheImpl<?, ?>> caches;
  private final DynamicMap<Cache<?, ?>> cacheMap;
  private final ExecutorService executor;
  private final ScheduledExecutorService cleanup;
  private final long h2CacheSize;
  private final boolean h2AutoServer;
  private final Set<CacheOptions> options;
  private final boolean pruneOnStartup;
  private final Schedule schedule;
  private final AtomicBoolean isDiskCacheReadOnly;

  @Inject
  H2CacheFactory(
      MemoryCacheFactory memCacheFactory,
      @GerritServerConfig Config cfg,
      DynamicMap<Cache<?, ?>> cacheMap,
      @Nullable @CacheCleanupExecutor ScheduledExecutorService cleanupExecutor,
      @Nullable @CacheStoreExecutor ExecutorService storeExecutor,
      @Nullable @CacheDir Path cacheDir,
      Set<CacheOptions> options,
      @Named("DiskCacheReadOnly") AtomicBoolean isDiskCacheReadOnly) {
    super(memCacheFactory, cfg, cacheDir);
    h2CacheSize = cfg.getLong("cache", null, "h2CacheSize", -1);
    h2AutoServer = cfg.getBoolean("cache", null, "h2AutoServer", false);
    pruneOnStartup = cfg.getBoolean("cachePruning", null, "pruneOnStartup", true);
    caches = new ArrayList<>();
    schedule =
        ScheduleConfig.createSchedule(cfg, "cachePruning")
            .orElseGet(() -> Schedule.createOrFail(Duration.ofDays(1).toMillis(), "01:00"));
    logger.atInfo().log("Scheduling cache pruning with schedule %s", schedule);
    this.cacheMap = cacheMap;
    this.executor = storeExecutor;
    this.cleanup = cleanupExecutor;
    this.options = options;
    this.isDiskCacheReadOnly = isDiskCacheReadOnly;
  }

  @Override
  public void start() {
    if (executor != null) {
      for (H2CacheImpl<?, ?> cache : caches) {
        executor.execute(cache::start);
        if (cleanup != null) {
          if (pruneOnStartup) {
            @SuppressWarnings("unused")
            Future<?> possiblyIgnoredError =
                cleanup.schedule(new PeriodicCachePruner(cache), 30, TimeUnit.SECONDS);
          }

          @SuppressWarnings("unused")
          Future<?> possiblyIgnoredError =
              cleanup.scheduleAtFixedRate(
                  new PeriodicCachePruner(cache),
                  schedule.initialDelay(),
                  schedule.interval(),
                  TimeUnit.MILLISECONDS);
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
  public <K, V> Cache<K, V> buildImpl(PersistentCacheDef<K, V> in, long limit) {
    H2CacheDefProxy<K, V> def = new H2CacheDefProxy<>(in);
    SqlStore<K, V> store = newSqlStore(def, limit);
    H2CacheImpl<K, V> cache =
        new H2CacheImpl<>(
            executor, store, def.keyType(), (Cache<K, ValueHolder<V>>) memCacheFactory.build(def));
    synchronized (caches) {
      caches.add(cache);
    }
    return cache;
  }

  @SuppressWarnings({"unchecked"})
  @Override
  public <K, V> LoadingCache<K, V> buildImpl(
      PersistentCacheDef<K, V> in, CacheLoader<K, V> loader, long limit) {
    H2CacheDefProxy<K, V> def = new H2CacheDefProxy<>(in);
    SqlStore<K, V> store = newSqlStore(def, limit);
    Cache<K, ValueHolder<V>> mem =
        (Cache<K, ValueHolder<V>>)
            memCacheFactory.build(
                def, (CacheLoader<K, V>) new H2CacheImpl.Loader<>(executor, store, loader));
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
    url.append("jdbc:h2:")
        .append(cacheDir.resolve(def.name() + "-v" + COMPATIBILITY_VERSION).toUri());
    if (h2CacheSize >= 0) {
      url.append(";CACHE_SIZE=");
      // H2 CACHE_SIZE is always given in KB
      url.append(h2CacheSize / 1024);
    }
    if (h2AutoServer) {
      url.append(";AUTO_SERVER=TRUE");
    }
    url.append(";DB_CLOSE_DELAY=-1");
    Duration refreshAfterWrite = def.refreshAfterWrite();
    if (has(def.configKey(), "refreshAfterWrite")) {
      long refreshAfterWriteInSec =
          ConfigUtil.getTimeUnit(config, "cache", def.configKey(), "refreshAfterWrite", 0, SECONDS);
      if (refreshAfterWriteInSec != 0) {
        refreshAfterWrite = Duration.ofSeconds(refreshAfterWriteInSec);
      }
    }
    Duration expireAfterWrite = def.expireAfterWrite();
    if (has(def.configKey(), "maxAge")) {
      long expireAfterWriteInsec =
          ConfigUtil.getTimeUnit(config, "cache", def.configKey(), "maxAge", 0, SECONDS);
      if (expireAfterWriteInsec != 0) {
        expireAfterWrite = Duration.ofSeconds(expireAfterWriteInsec);
      }
    }
    return new SqlStore<>(
        url.toString(),
        def.keyType(),
        def.keySerializer(),
        def.valueSerializer(),
        def.version(),
        maxSize,
        config.getInt("cache", "h2MaxInvalidated", 25),
        expireAfterWrite,
        refreshAfterWrite,
        options.contains(CacheOptions.BUILD_BLOOM_FILTER),
        options.contains(CacheOptions.TRACK_LAST_ACCESS),
        isDiskCacheReadOnly);
  }

  private boolean has(String name, String var) {
    return !Strings.isNullOrEmpty(config.getString("cache", name, var));
  }
}
