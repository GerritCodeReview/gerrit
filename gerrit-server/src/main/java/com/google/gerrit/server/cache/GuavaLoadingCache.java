// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.gerrit.server.cache;

import com.google.common.base.Preconditions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;

/** An in-memory cache backed by Guava's LoadingCache. */
public class GuavaLoadingCache<K,V> implements Cache<K,V> {
  private static final Logger log = LoggerFactory.getLogger(GuavaLoadingCache.class);

  private final String name;
  private final com.google.common.cache.LoadingCache<K, V> cache;

  public GuavaLoadingCache(String name, com.google.common.cache.LoadingCache<K, V> cache) {
    this.name = name;
    this.cache = Preconditions.checkNotNull(cache);
  }

  @Override
  public V get(K key) {
    try {
      return cache.get(key);
    } catch (ExecutionException e) {
      // This should never happen because EntryCreator uses missing(Key)
      // when the createEntry(Key) method fails with an exception of any kind.
      log.error(String.format("Cache %s failed to load %s", name, key), e.getCause());
      return null;
    }
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
