// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.vhost;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Objects;
import com.google.common.cache.AbstractLoadingCache;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.cache.CacheBinding;
import com.google.gerrit.server.cache.MemoryCacheFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Site-specific {@link MemoryCacheFactory} that delegates to a shared Guava
 * cache.
 * <p>
 * Because Gerrit does not know at initialization time how many sites there are,
 * it does not make sense to set per-site cache configuration. Per-site
 * configuration is ignored and global settings are applied from the
 * {@code GerritGlobalConfig} by {@link GlobalCachePool}.
 */
class SiteCacheFactory implements MemoryCacheFactory {
  static class Module extends AbstractModule {
    @Override
    protected void configure() {
      bind(MemoryCacheFactory.class).to(SiteCacheFactory.class);
    }
  }

  static final class SiteKey<K, V> {
    private final String siteName;
    private final K key;
    private transient CacheLoader<K, V> loader;

    SiteKey(String siteName, K key, CacheLoader<K, V> loader) {
      this.siteName = siteName;
      this.key = key;
      this.loader = loader;
    }

    String getSiteName() {
      return siteName;
    }

    K getKey() {
      return key;
    }

    @Nullable
    CacheLoader<K, V> getLoader() {
      return loader;
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof SiteKey) {
        SiteKey<?, ?> k = (SiteKey<?, ?>) o;
        return siteName.equals(k.siteName) && key.equals(k.key);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(siteName, key);
    }

    @Override
    public String toString() {
      return String.format("[%s]%s", siteName, key);
    }
  }

  private final GlobalCachePool globalPool;
  private final String siteName;

  @Inject
  SiteCacheFactory(GlobalCachePool globalPool, @SiteName String siteName) {
    this.globalPool = globalPool;
    this.siteName = siteName;
  }

  @Override
  public <K, V> Cache<K, V> build(CacheBinding<K, V> def) {
    checkNotNull(def, "def");

    Cache<SiteKey<K, V>, V> cache = globalPool.get(def.name());
    checkNotNull(cache, "cache %s not configured", def.name());
    return new SiteCache<>(siteName, cache, null);
  }

  @Override
  public <K, V> LoadingCache<K, V> build(
      CacheBinding<K, V> def,
      CacheLoader<K, V> loader) {
    checkNotNull(def, "def");
    checkNotNull(loader, "loader");

    Cache<SiteKey<K, V>, V> cache = globalPool.get(def.name());
    checkNotNull(cache, "cache %s not configured", def.name());
    checkState(cache instanceof LoadingCache,
        "cache %s must be LoadingCache",
        def.name());
    return new SiteCache<>(siteName, cache, loader);
  }

  private static class SiteCache<K, V> extends AbstractLoadingCache<K, V> {
    private final String siteName;
    private final Cache<SiteKey<K, V>, V> cache;
    private final CacheLoader<K, V> loader;

    SiteCache(String siteName,
        Cache<SiteKey<K, V>, V> cache,
        CacheLoader<K, V> loader) {
      this.siteName = siteName;
      this.cache = cache;
      this.loader = loader;
    }

    @Override
    public V get(K key) throws ExecutionException {
      if (loader == null) {
        throw new UnsupportedOperationException();
      }
      SiteKey<K, V> k = wrap(key);
      try {
        k.loader = loader;
        return ((LoadingCache<SiteKey<K, V>, V>) cache).get(k);
      } finally {
        k.loader = null;
      }
    }

    @Override
    public ImmutableMap<K, V> getAll(Iterable<? extends K> keys)
        throws ExecutionException {
      if (loader == null) {
        throw new UnsupportedOperationException();
      }

      Set<SiteKey<K, V>> tmpKeys = Sets.newHashSet();
      for (K key : keys) {
        SiteKey<K, V> k = wrap(key);
        k.loader = loader;
        tmpKeys.add(k);
      }

      ImmutableMap<SiteKey<K, V>, V> tmpVals;
      try {
        tmpVals = ((LoadingCache<SiteKey<K, V>, V>) cache).getAll(tmpKeys);
      } finally {
        for (SiteKey<K, V> k : tmpKeys) {
          k.loader = null;
        }
      }

      ImmutableMap.Builder<K, V> result = ImmutableMap.builder();
      for (Map.Entry<SiteKey<K, V>, V> e : tmpVals.entrySet()) {
        result.put(e.getKey().getKey(), e.getValue());
      }
      return result.build();
    }

    @SuppressWarnings("unchecked")
    @Override
    public V getIfPresent(Object rawKey) {
      return cache.getIfPresent(wrap((K) rawKey));
    }

    @Override
    public void put(K key, V val) {
      cache.put(wrap(key), val);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void invalidate(Object key) {
      cache.invalidate(wrap((K) key));
    }

    @Override
    public void invalidateAll() {
      // There is no easy method to invalidate only the entries associated
      // with a single site, short of iterating the entire cache as a map and
      // checking the siteName in the SiteKey. This is O(N) size of the cache,
      // so instead punt and flush the entire cache.
      cache.invalidateAll();
    }

    @Override
    public long size() {
      return cache.size();
    }

    @Override
    public CacheStats stats() {
      return cache.stats();
    }

    private SiteKey<K, V> wrap(K rawKey) {
      return new SiteKey<>(siteName, rawKey, null);
    }
  }
}
