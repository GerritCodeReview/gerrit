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
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.cache.CacheBinding;
import com.google.gerrit.server.cache.PersistentCacheFactory;
import com.google.gerrit.server.cache.h2.H2CacheImpl.SqlStore;
import com.google.gerrit.server.cache.h2.H2CacheImpl.ValueHolder;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class H2CacheFactory implements PersistentCacheFactory, LifecycleListener {
  private static final Logger log = LoggerFactory.getLogger(H2CacheFactory.class);

  private final DefaultCacheFactory defaultFactory;
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
      DefaultCacheFactory defaultCacheFactory,
      @GerritServerConfig Config cfg,
      SitePaths site,
      DynamicMap<Cache<?, ?>> cacheMap) {
    defaultFactory = defaultCacheFactory;
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
        log.warn("Can't create disk cache: " + loc.toAbsolutePath());
        return null;
      }
    }
    if (!Files.isWritable(loc)) {
      log.warn("Can't write to disk cache: " + loc.toAbsolutePath());
      return null;
    }
    log.info("Enabling disk cache " + loc.toAbsolutePath());
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
            log.info(String.format("Finishing %d disk cache updates", pending.size()));
            for (Runnable update : pending) {
              update.run();
            }
          }
        } else {
          log.info("Timeout waiting for disk cache to close");
        }
      } catch (InterruptedException e) {
        log.warn("Interrupted waiting for disk cache to shutdown");
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
  public <K, V> Cache<K, V> build(CacheBinding<K, V> def) {
    long limit = config.getLong("cache", def.name(), "diskLimit", 128 << 20);

    if (cacheDir == null || limit <= 0) {
      return defaultFactory.build(def);
    }

    SqlStore<K, V> store =
        newSqlStore(def.name(), def.keyType(), limit, def.expireAfterWrite(TimeUnit.SECONDS));
    H2CacheImpl<K, V> cache =
        new H2CacheImpl<>(
            executor,
            store,
            def.keyType(),
            (Cache<K, ValueHolder<V>>) defaultFactory.create(def, true).build());
    synchronized (caches) {
      caches.add(cache);
    }
    return cache;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <K, V> LoadingCache<K, V> build(CacheBinding<K, V> def, CacheLoader<K, V> loader) {
    long limit = config.getLong("cache", def.name(), "diskLimit", def.diskLimit());

    if (cacheDir == null || limit <= 0) {
      return defaultFactory.build(def, loader);
    }

    SqlStore<K, V> store =
        newSqlStore(def.name(), def.keyType(), limit, def.expireAfterWrite(TimeUnit.SECONDS));
    Cache<K, ValueHolder<V>> mem =
        (Cache<K, ValueHolder<V>>)
            defaultFactory
                .create(def, true)
                .build((CacheLoader<K, V>) new H2CacheImpl.Loader<>(executor, store, loader));
    H2CacheImpl<K, V> cache = new H2CacheImpl<>(executor, store, def.keyType(), mem);
    caches.add(cache);
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

  private <V, K> SqlStore<K, V> newSqlStore(
      String name, TypeLiteral<K> keyType, long maxSize, Long expireAfterWrite) {
    StringBuilder url = new StringBuilder();
    url.append("jdbc:h2:").append(cacheDir.resolve(name).toUri());
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
        keyType,
        maxSize,
        expireAfterWrite == null ? 0 : expireAfterWrite.longValue());
  }
}
