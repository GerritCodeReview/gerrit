// Copyright (C) 2022 The Android Open Source Project
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

import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheLoader.UnsupportedLoadingOperationException;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

/** Implementation of a NOOP cache that just passes all gets to the loader */
public class PassthroughLoadingCache<K, V> implements LoadingCache<K, V> {

  private final CacheLoader<? super K, V> cacheLoader;

  public PassthroughLoadingCache(CacheLoader<? super K, V> cacheLoader) {
    this.cacheLoader = cacheLoader;
  }

  @Override
  public @Nullable V getIfPresent(Object key) {
    return null;
  }

  @Override
  public V get(K key, Callable<? extends V> loader) throws ExecutionException {
    try {
      return loader.call();
    } catch (Exception e) {
      throw new ExecutionException(e);
    }
  }

  @Override
  public ImmutableMap<K, V> getAllPresent(Iterable<?> keys) {
    return ImmutableMap.of();
  }

  @Override
  public void put(K key, V value) {}

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {}

  @Override
  public void invalidate(Object key) {}

  @Override
  public void invalidateAll(Iterable<?> keys) {}

  @Override
  public void invalidateAll() {}

  @Override
  public long size() {
    return 0;
  }

  @Override
  public CacheStats stats() {
    return new CacheStats(0, 0, 0, 0, 0, 0);
  }

  @Override
  public void cleanUp() {}

  @Override
  public V get(K key) throws ExecutionException {
    try {
      return cacheLoader.load(key);
    } catch (Exception e) {
      throw new ExecutionException(e);
    }
  }

  @Override
  public V getUnchecked(K key) {
    try {
      return cacheLoader.load(key);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public ImmutableMap<K, V> getAll(Iterable<? extends K> keys) throws ExecutionException {
    try {
      try {
        return getAllBulk(keys);
      } catch (UnsupportedLoadingOperationException e) {
        return getAllIndividually(keys);
      }
    } catch (Exception e) {
      throw new ExecutionException(e);
    }
  }

  private ImmutableMap<K, V> getAllIndividually(Iterable<? extends K> keys) throws Exception {
    ImmutableMap.Builder<K, V> builder = ImmutableMap.builder();
    for (K k : keys) {
      builder.put(k, cacheLoader.load(k));
    }
    return builder.build();
  }

  @SuppressWarnings("unchecked")
  private ImmutableMap<K, V> getAllBulk(Iterable<? extends K> keys) throws Exception {
    return (ImmutableMap<K, V>) ImmutableMap.copyOf(cacheLoader.loadAll(keys));
  }

  @Override
  public V apply(K key) {
    return getUnchecked(key);
  }

  @Override
  public void refresh(K key) {}

  @Override
  public ConcurrentMap<K, V> asMap() {
    return new ConcurrentHashMap<>();
  }
}
