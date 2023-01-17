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

package com.google.gerrit.server.cache.mem;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.github.benmanes.caffeine.cache.Weigher;
import com.github.benmanes.caffeine.guava.CaffeinatedGuava;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalNotification;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.cache.CacheBackend;
import com.google.gerrit.server.cache.CacheDef;
import com.google.gerrit.server.cache.ForwardingRemovalListener;
import com.google.gerrit.server.cache.MemoryCacheFactory;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.concurrent.Executor;
import org.eclipse.jgit.lib.Config;

class DefaultMemoryCacheFactory implements MemoryCacheFactory {
  static final String CACHE_EXECUTOR_PREFIX = "InMemoryCacheExecutor";
  private static final int DEFAULT_CACHE_EXECUTOR_THREADS = -1;

  private final Config cfg;
  private final ForwardingRemovalListener.Factory forwardingRemovalListenerFactory;
  private int executorThreads;
  private final Executor executor;

  @Inject
  DefaultMemoryCacheFactory(
      @GerritServerConfig Config config,
      ForwardingRemovalListener.Factory forwardingRemovalListenerFactory,
      WorkQueue workQueue) {
    this.cfg = config;
    this.forwardingRemovalListenerFactory = forwardingRemovalListenerFactory;
    this.executorThreads = config.getInt("cache", "threads", DEFAULT_CACHE_EXECUTOR_THREADS);

    if (executorThreads == 0) {
      executor = MoreExecutors.newDirectExecutorService();
    } else if (executorThreads > DEFAULT_CACHE_EXECUTOR_THREADS) {
      executor = workQueue.createQueue(executorThreads, CACHE_EXECUTOR_PREFIX);
    } else {
      executor = null;
    }
  }

  @Override
  public <K, V> Cache<K, V> build(CacheDef<K, V> def, CacheBackend backend) {
    return backend.isLegacyBackend()
        ? createLegacy(def).build()
        : CaffeinatedGuava.build(create(def));
  }

  @Override
  public <K, V> LoadingCache<K, V> build(
      CacheDef<K, V> def, CacheLoader<K, V> loader, CacheBackend backend) {
    return cacheMaximumWeight(def) == 0
        ? new PassthroughLoadingCache<>(loader)
        : (backend.isLegacyBackend()
            ? createLegacy(def).build(loader)
            : CaffeinatedGuava.build(create(def), loader));
  }

  @SuppressWarnings("unchecked")
  private <K, V> CacheBuilder<K, V> createLegacy(CacheDef<K, V> def) {
    CacheBuilder<K, V> builder = newLegacyCacheBuilder();
    builder.recordStats();
    builder.maximumWeight(cacheMaximumWeight(def));

    builder = builder.removalListener(forwardingRemovalListenerFactory.create(def.name()));

    com.google.common.cache.Weigher<K, V> weigher = def.weigher();
    if (weigher == null) {
      weigher = unitWeight();
    }
    builder.weigher(weigher);

    Duration expireAfterWrite = def.expireAfterWrite();
    if (has(def.configKey(), "maxAge")) {
      builder.expireAfterWrite(
          ConfigUtil.getTimeUnit(
              cfg, "cache", def.configKey(), "maxAge", toSeconds(expireAfterWrite), SECONDS),
          SECONDS);
    } else if (expireAfterWrite != null) {
      builder.expireAfterWrite(expireAfterWrite.toNanos(), NANOSECONDS);
    }

    Duration expireAfterAccess = def.expireFromMemoryAfterAccess();
    if (has(def.configKey(), "expireFromMemoryAfterAccess")) {
      builder.expireAfterAccess(
          ConfigUtil.getTimeUnit(
              cfg,
              "cache",
              def.configKey(),
              "expireFromMemoryAfterAccess",
              toSeconds(expireAfterAccess),
              SECONDS),
          SECONDS);
    } else if (expireAfterAccess != null) {
      builder.expireAfterAccess(expireAfterAccess.toNanos(), NANOSECONDS);
    }

    Duration refreshAfterWrite = def.refreshAfterWrite();
    if (has(def.configKey(), "refreshAfterWrite")) {
      builder.refreshAfterWrite(
          ConfigUtil.getTimeUnit(
              cfg,
              "cache",
              def.configKey(),
              "refreshAfterWrite",
              toSeconds(refreshAfterWrite),
              SECONDS),
          SECONDS);
    } else if (refreshAfterWrite != null) {
      builder.refreshAfterWrite(refreshAfterWrite.toNanos(), NANOSECONDS);
    }

    return builder;
  }

  private <K, V> Caffeine<K, V> create(CacheDef<K, V> def) {
    Caffeine<K, V> builder = newCacheBuilder();
    builder.recordStats();
    builder.maximumWeight(cacheMaximumWeight(def));
    builder = builder.removalListener(newRemovalListener(def.name()));

    if (executor != null) {
      builder.executor(executor);
    }
    builder.weigher(newWeigher(def.weigher()));

    Duration expireAfterWrite = def.expireAfterWrite();
    if (has(def.configKey(), "maxAge")) {
      builder.expireAfterWrite(
          ConfigUtil.getTimeUnit(
              cfg, "cache", def.configKey(), "maxAge", toSeconds(expireAfterWrite), SECONDS),
          SECONDS);
    } else if (expireAfterWrite != null) {
      builder.expireAfterWrite(expireAfterWrite.toNanos(), NANOSECONDS);
    }

    Duration expireAfterAccess = def.expireFromMemoryAfterAccess();
    if (has(def.configKey(), "expireFromMemoryAfterAccess")) {
      builder.expireAfterAccess(
          ConfigUtil.getTimeUnit(
              cfg,
              "cache",
              def.configKey(),
              "expireFromMemoryAfterAccess",
              toSeconds(expireAfterAccess),
              SECONDS),
          SECONDS);
    } else if (expireAfterAccess != null) {
      builder.expireAfterAccess(expireAfterAccess.toNanos(), NANOSECONDS);
    }

    Duration refreshAfterWrite = def.refreshAfterWrite();
    if (has(def.configKey(), "refreshAfterWrite")) {
      builder.refreshAfterWrite(
          ConfigUtil.getTimeUnit(
              cfg,
              "cache",
              def.configKey(),
              "refreshAfterWrite",
              toSeconds(refreshAfterWrite),
              SECONDS),
          SECONDS);
    } else if (refreshAfterWrite != null) {
      builder.refreshAfterWrite(refreshAfterWrite.toNanos(), NANOSECONDS);
    }

    return builder;
  }

  private <K, V> long cacheMaximumWeight(CacheDef<K, V> def) {
    return cfg.getLong("cache", def.configKey(), "memoryLimit", def.maximumWeight());
  }

  private static long toSeconds(@Nullable Duration duration) {
    return duration != null ? duration.getSeconds() : 0;
  }

  private boolean has(String name, String var) {
    return !Strings.isNullOrEmpty(cfg.getString("cache", name, var));
  }

  @SuppressWarnings("unchecked")
  private static <K, V> CacheBuilder<K, V> newLegacyCacheBuilder() {
    return (CacheBuilder<K, V>) CacheBuilder.newBuilder();
  }

  private static <K, V> com.google.common.cache.Weigher<K, V> unitWeight() {
    return (key, value) -> 1;
  }

  @SuppressWarnings("unchecked")
  private static <K, V> Caffeine<K, V> newCacheBuilder() {
    return (Caffeine<K, V>) Caffeine.newBuilder();
  }

  @SuppressWarnings("unchecked")
  private <V, K> RemovalListener<K, V> newRemovalListener(String cacheName) {
    return (k, v, cause) ->
        forwardingRemovalListenerFactory
            .create(cacheName)
            .onRemoval(
                RemovalNotification.create(
                    k, v, com.google.common.cache.RemovalCause.valueOf(cause.name())));
  }

  private static <K, V> Weigher<K, V> newWeigher(
      com.google.common.cache.Weigher<K, V> guavaWeigher) {
    return guavaWeigher == null ? Weigher.singletonWeigher() : (k, v) -> guavaWeigher.weigh(k, v);
  }
}
