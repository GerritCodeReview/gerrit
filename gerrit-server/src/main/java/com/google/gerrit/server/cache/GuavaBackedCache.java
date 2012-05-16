// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.gerrit.server.cache;

import com.google.common.cache.CacheStats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** An in-memory cache backed by Guava's cache implementations. */
public class GuavaBackedCache<K,V> implements Cache<K,V> {
  private final String name;
  private final long maxSize;
  private final long maxAge;
  private final com.google.common.cache.Cache<K, V> cache;

  GuavaBackedCache(String name,
      long maxSize,
      long maxAge,
      com.google.common.cache.Cache<K, V> cache) {
    this.name = name;
    this.maxSize = maxSize;
    this.maxAge = maxAge;
    this.cache = cache;
  }

  public String getName() {
    return name;
  }

  public long getMaxSize() {
    return maxSize;
  }

  public long getMaxAge(TimeUnit unit) {
    return unit.convert(maxAge, TimeUnit.SECONDS);
  }

  public CacheStats stats() {
    return cache.stats();
  }

  public long size() {
    return cache.size();
  }

  @Override
  public V get(K key) {
    return cache.getIfPresent(key);
  }

  @Override
  public void put(K key, V value) {
    cache.put(key, value);
  }

  @Override
  public void remove(K key) {
    cache.invalidate(key);
  }

  @Override
  public void removeAll() {
    cache.invalidateAll();
  }

  static class Loading<K, V> extends GuavaBackedCache<K, V> {
    private static final Logger log = LoggerFactory.getLogger(Loading.class);
    private final com.google.common.cache.LoadingCache<K, V> cache;

    Loading(String name,
        long maxSize,
        long maxAge,
        com.google.common.cache.LoadingCache<K, V> cache) {
      super(name, maxSize, maxAge, cache);
      this.cache = cache;
    }

    @Override
    public V get(K key) {
      try {
        return cache.get(key);
      } catch (ExecutionException e) {
        // This should never happen because EntryCreator uses missing(Key)
        // when the createEntry(Key) method fails with an exception of any kind.
        log.error(String.format("Cache %s failed to load %s",
            getName(), key), e.getCause());
        return null;
      }
    }
  }
}
