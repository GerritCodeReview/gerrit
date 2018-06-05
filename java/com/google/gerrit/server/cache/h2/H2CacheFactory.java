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
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.cache.MemoryCacheFactory;
import com.google.gerrit.server.cache.PersistentCacheDef;
import com.google.gerrit.server.cache.PersistentCacheFactory;
import com.google.gerrit.server.cache.h2.H2CacheImpl.SqlStore;
import com.google.gerrit.server.cache.h2.H2CacheImpl.ValueHolder;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

@Singleton
class H2CacheFactory implements PersistentCacheFactory, LifecycleListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final MemoryCacheFactory memCacheFactory;
  private final Config config;
  private final Path cacheDir;
  private final List<H2CacheImpl<?, ?>> caches;
  private final DynamicMap<Cache<?, ?>> cacheMap;
  private final ExecutorService executor;
  private final ScheduledExecutorService cleanup;
  private final long h2CacheSize;
  private final boolean h2AutoServer;

  @Inject
  H2CacheFactory(
      MemoryCacheFactory memCacheFactory,
      @GerritServerConfig Config cfg,
      SitePaths site,
      DynamicMap<Cache<?, ?>> cacheMap) {
    this.memCacheFactory = memCacheFactory;
    config = cfg;
    cacheDir = getCacheDir(site, cfg.getString("cache", null, "directory"));
    h2CacheSize = cfg.getLong("cache", null, "h2CacheSize", -1);
    h2AutoServer = cfg.getBoolean("cache", null, "h2AutoServer", false);
    caches = new LinkedList<>();
    this.cacheMap = cacheMap;

    if (cacheDir != null) {
      executor =
          Executors.newFixedThreadPool(
              1, new ThreadFactoryBuilder().setNameFormat("DiskCache-Store-%d").build());
      cleanup =
          Executors.newScheduledThreadPool(
              1,
              new ThreadFactoryBuilder()
                  .setNameFormat("DiskCache-Prune-%d")
                  .setDaemon(true)
                  .build());
    } else {
      executor = null;
      cleanup = null;
    }
  }

  private static Path getCacheDir(SitePaths site, String name) {
    if (name == null) {
      return null;
    }
    Path loc = site.resolve(name);
    if (!Files.exists(loc)) {
      try {
        Files.createDirectories(loc);
      } catch (IOException e) {
        logger.atWarning().log("Can't create disk cache: %s", loc.toAbsolutePath());
        return null;
      }
    }
    if (!Files.isWritable(loc)) {
      logger.atWarning().log("Can't write to disk cache: %s", loc.toAbsolutePath());
      return null;
    }
    logger.atInfo().log("Enabling disk cache %s", loc.toAbsolutePath());
    return loc;
  }

  @Override
  public void start() {
    if (executor != null) {
      for (H2CacheImpl<?, ?> cache : caches) {
        executor.execute(cache::start);
        @SuppressWarnings("unused")
        Future<?> possiblyIgnoredError =
            cleanup.schedule(() -> cache.prune(cleanup), 30, TimeUnit.SECONDS);
      }
    }
  }

  @Override
  public void stop() {
    if (executor != null) {
      try {
        cleanup.shutdownNow();

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
  public <K, V> Cache<K, V> build(PersistentCacheDef<K, V> in) {
    long limit = config.getLong("cache", in.configKey(), "diskLimit", in.diskLimit());

    if (cacheDir == null || limit <= 0) {
      return memCacheFactory.build(in);
    }

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

  @SuppressWarnings("unchecked")
  @Override
  public <K, V> LoadingCache<K, V> build(PersistentCacheDef<K, V> in, CacheLoader<K, V> loader) {
    long limit = config.getLong("cache", in.configKey(), "diskLimit", in.diskLimit());

    if (cacheDir == null || limit <= 0) {
      return memCacheFactory.build(in, loader);
    }

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
        def.expireAfterWrite());
  }
}
