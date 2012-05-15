// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.gerrit.server.cache;

import com.google.common.base.Preconditions;

/** An in-memory cache backed by Guava's cache implementation. */
public class GuavaRecallCache<K,V> implements Cache<K,V> {
  private final com.google.common.cache.Cache<K, V> cache;

  public GuavaRecallCache(com.google.common.cache.Cache<K, V> cache) {
    this.cache = Preconditions.checkNotNull(cache);
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
}
