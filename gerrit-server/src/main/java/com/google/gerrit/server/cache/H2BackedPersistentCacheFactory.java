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

package com.google.gerrit.server.cache;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.cache.H2BackedCache.SqlStore;
import com.google.gerrit.server.cache.H2BackedCache.ValueHolder;
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
public class H2BackedPersistentCacheFactory
    implements PersistentCacheFactory, LifecycleListener {
  static final Logger log = LoggerFactory.getLogger(H2BackedPersistentCacheFactory.class);

  /**
   * Maximum size in bytes for a cache database.
   * <p>
   * Disk based caches that store more than this in total bytes for keys and
   * values will prune the oldest accessed items in the cache, until the data
   * falls below this size. Prune cycles run at 1 am local server time.
   */
  private static final int MAX_SIZE = 128 << 20;

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      bind(PersistentCacheFactory.class).to(H2BackedPersistentCacheFactory.class);
      listener().to(H2BackedPersistentCacheFactory.class);
    }
  }

  private final File cacheDir;
  private final List<H2BackedCache<?, ?>> caches;
  private final ExecutorService executor;
  private final ScheduledExecutorService cleanup;
  private volatile boolean started;

  @Inject
  H2BackedPersistentCacheFactory(
      @GerritServerConfig Config cfg,
      SitePaths site) {
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
      for (final H2BackedCache<?, ?> cache : caches) {
        executor.execute(new Runnable() {
          @Override
          public void run() {
            cache.start();
          }
        });

        cleanup.schedule(new Runnable() {
          @Override
          public void run() {
            cache.prune(MAX_SIZE, cleanup);
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
        if (executor.awaitTermination(1, TimeUnit.HOURS)) {
          if (pending != null && !pending.isEmpty()) {
            log.info(String.format("Waiting for %d disk cache updates", pending.size()));
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
    for (H2BackedCache<?, ?> cache : caches) {
      cache.stop();
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes", "cast"})
  @Override
  public <K, V> Cache<K, V> build(
      String name,
      TypeLiteral<K> keyType,
      TypeLiteral<V> valType,
      CacheBuilder<K, ?> memoryBuilder) {
    Preconditions.checkState(!started, "cache must be built before start");

    Cache mem = memoryBuilder.build();
    if (cacheDir == null) {
      return (Cache<K, V>) mem;
    }

    SqlStore<K, V> store = newSqlStore(name, keyType);
    H2BackedCache<K, V> cache = new H2BackedCache<K, V>(
        executor, store, keyType, (Cache<K, ValueHolder<V>>) mem);
    caches.add(cache);
    return cache;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <K, V> LoadingCache<K, V> build(
      String name,
      TypeLiteral<K> keyType,
      TypeLiteral<V> valType,
      CacheBuilder<K, ?> memoryBuilder,
      CacheLoader<K, V> loader) {
    Preconditions.checkState(!started, "cache must be built before start");

    if (cacheDir == null) {
      return ((CacheBuilder<K, V>) memoryBuilder).build(loader);
    }

    SqlStore<K, V> store = newSqlStore(name, keyType);
    Cache<K, ValueHolder<V>> mem =
        ((CacheBuilder<K, ValueHolder<V>>) memoryBuilder).build(
            new H2BackedCache.Loader<K, V>(executor, store, loader));
    H2BackedCache<K, V> cache =
        new H2BackedCache<K, V>(executor, store, keyType, mem);
    caches.add(cache);
    return cache;
  }

  private <V, K> SqlStore<K, V> newSqlStore(
      String name,
      TypeLiteral<K> keyType) {
    File db = new File(cacheDir, name).getAbsoluteFile();
    return new SqlStore<K,V>("jdbc:h2:" + db.toURI().toString(), keyType);
  }
}
