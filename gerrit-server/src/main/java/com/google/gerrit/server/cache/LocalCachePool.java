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

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

/** In-memory caches within this process. */
public class LocalCachePool implements CachePool {
  private static final Logger log = LoggerFactory.getLogger(GuavaLoadingCache.class);

  private final ConcurrentMap<String, LocalCacheHandle> cacheHandles;
  private final CachePool diskPool;
  private final Config config;
  private final boolean canUseDisk;

  @Inject
  LocalCachePool(@Nullable @PersistentCachePool CachePool diskPool,
      @GerritServerConfig Config cfg,
      SitePaths site) {
    this.cacheHandles = Maps.newConcurrentMap();
    this.diskPool = diskPool;
    this.config = cfg;
    this.canUseDisk = diskPool != null && canUseDisk(cfg, site);
  }

  public Iterable<LocalCacheHandle> getCaches() {
    return cacheHandles.values();
  }

  void remove(String name, LocalCacheHandle handle) {
    cacheHandles.remove(name, handle);
  }

  private static boolean canUseDisk(Config config, SitePaths site) {
    File dir = site.resolve(config.getString("cache", null, "directory"));
    return dir != null && (dir.exists() || dir.mkdirs()) && dir.canWrite();
  }

  @Override
  public <K, V> Cache<K, V> register(CacheProvider<K, V> provider) {
    String name = provider.getName();
    Preconditions.checkState(
        !cacheHandles.containsKey(name),
        "Cache %s already defined", name);

    if (canUseDisk && diskPool != null
        && provider.disk()
        && 0 < config.getInt("cache", name, "diskLimit", provider.diskLimit())) {
      return diskPool.register(provider);
    }

    @SuppressWarnings("unchecked")
    CacheBuilder<K, V> builder = (CacheBuilder<K, V>) CacheBuilder.newBuilder();

    long size = config.getLong("cache", name, "memoryLimit", provider.memoryLimit());
    if (0 < size) {
      builder.maximumSize(size);
    }

    long maxAge = parseMaxAgeSeconds(name, provider.maxAge());
    if (0 < maxAge) {
      builder.expireAfterWrite(maxAge, TimeUnit.SECONDS);
    }

    com.google.common.cache.Cache<K, V> store = build(provider, builder);
    LocalCacheHandle handle = new LocalCacheHandle(
        this,
        name,
        size, maxAge,
        store);
    cacheHandles.put(name, handle);
    if (store instanceof LoadingCache) {
      return new GuavaLoadingCache<K, V>(name, (LoadingCache<K, V>) store);
    } else {
      return new GuavaRecallCache<K, V>(store);
    }
  }

  private static <K, V> com.google.common.cache.Cache<K, V> build(
      CacheProvider<K, V> provider, CacheBuilder<K, V> builder) {
    final EntryCreator<K, V> creator = provider.getEntryCreator();
    if (creator != null) {
      final String name = provider.getName();
      return builder.build(new CacheLoader<K, V>() {
        @Override
        public V load(K key) throws Exception {
          try {
            return creator.createEntry(key);
          } catch (Exception err) {
            log.warn(String.format("Cache %s failed to load %s", name, key), err);
            return creator.missing(key);
          }
        }
      });
    } else {
      return builder.build();
    }
  }

  private long parseMaxAgeSeconds(String name, long defAgeSecs) {
    return MINUTES.toSeconds(ConfigUtil.getTimeUnit(config,
        "cache", name, "maxAge",
        SECONDS.toMinutes(defAgeSecs), MINUTES));
  }
}
