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

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.cache.CacheBinding;
import com.google.gerrit.server.cache.PersistentCacheFactory;
import com.google.gerrit.server.cache.h2.H2CacheImpl.SqlStore;
import com.google.gerrit.server.cache.h2.H2CacheImpl.ValueHolder;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Singleton
class H2CacheFactory implements PersistentCacheFactory, LifecycleListener {
  static final Logger log = LoggerFactory.getLogger(H2CacheFactory.class);

  private final DefaultCacheFactory defaultFactory;
  private final Config config;
   private final File cacheDir;
  private final List<H2CacheImpl<?, ?>> caches;
  private final ExecutorService executor;
  private final ScheduledExecutorService cleanup;
  private volatile boolean started;

  @Inject
  H2CacheFactory(
      DefaultCacheFactory defaultCacheFactory,
      @GerritServerConfig Config cfg,
      SitePaths site) {
    defaultFactory = defaultCacheFactory;
    config = cfg;

    File loc = site.resolve(cfg.getString("cache", null, "directory"));
    if (loc == null) {
      cacheDir = null;
    } else if (loc.exists() || loc.mkdirs()) {
      if (loc.canWrite()) {
        log.info("Enabling disk cache " + loc.getAbsolutePath());
        cacheDir = loc;
      } else {
        log.warn("Can't write to disk cache: " + loc.getAbsolutePath());
        cacheDir = null;
      }
    } else {
      log.warn("Can't create disk cache: " + loc.getAbsolutePath());
      cacheDir = null;
    }

    caches = Lists.newLinkedList();

    if (cacheDir != null) {
      executor = Executors.newFixedThreadPool(
          1,
          new ThreadFactoryBuilder()
            .setNameFormat("DiskCache-Store-%d")
            .build());
      cleanup = Executors.newScheduledThreadPool(
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

  @Override
  public void start() {
    started = true;
    if (executor != null) {
      for (final H2CacheImpl<?, ?> cache : caches) {
        executor.execute(new Runnable() {
          @Override
          public void run() {
            cache.start();
          }
        });

        cleanup.schedule(new Runnable() {
          @Override
          public void run() {
            cache.prune(cleanup);
          }
        }, 30, TimeUnit.SECONDS);
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
    for (H2CacheImpl<?, ?> cache : caches) {
      cache.stop();
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes", "cast"})
  @Override
  public <K, V> Cache<K, V> build(CacheBinding<K, V> def) {
    Preconditions.checkState(!started, "cache must be built before start");
    long limit = config.getLong("cache", def.name(), "diskLimit", 128 << 20);

    if (cacheDir == null || limit <= 0) {
      return defaultFactory.build(def);
    }

    SqlStore<K, V> store = newSqlStore(def.name(), def.keyType(), limit);
    H2CacheImpl<K, V> cache = new H2CacheImpl<K, V>(
        executor, store, def.keyType(),
        (Cache<K, ValueHolder<V>>) defaultFactory.create(def, true).build());
    caches.add(cache);
    return cache;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <K, V> LoadingCache<K, V> build(
      CacheBinding<K, V> def,
      CacheLoader<K, V> loader) {
    Preconditions.checkState(!started, "cache must be built before start");
    long limit = config.getLong("cache", def.name(), "diskLimit", 128 << 20);

    if (cacheDir == null || limit <= 0) {
      return defaultFactory.build(def, loader);
    }

    SqlStore<K, V> store = newSqlStore(def.name(), def.keyType(), limit);
    Cache<K, ValueHolder<V>> mem = (Cache<K, ValueHolder<V>>)
        defaultFactory.create(def, true)
        .build((CacheLoader<K, V>) new H2CacheImpl.Loader<K, V>(
              executor, store, loader));
    H2CacheImpl<K, V> cache = new H2CacheImpl<K, V>(
        executor, store, def.keyType(), mem);
    caches.add(cache);
    return cache;
  }

  private <V, K> SqlStore<K, V> newSqlStore(
      String name,
      TypeLiteral<K> keyType,
      long maxSize) {
    File db = new File(cacheDir, name).getAbsoluteFile();
    String url = "jdbc:h2:" + db.toURI().toString();
    return new SqlStore<K, V>(url, keyType, maxSize);
  }
}
