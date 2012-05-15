// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.gerrit.server.cache;

import com.google.common.cache.CacheStats;
import com.google.gerrit.extensions.registration.RegistrationHandle;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

/** Description of an in-memory cache backed by Guava. */
public class LocalCacheHandle implements RegistrationHandle {
  private final LocalCachePool pool;
  private final String name;
  private final long maxSize;
  private final long maxAge;
  private final WeakReference<com.google.common.cache.Cache<?, ?>> cache;

  LocalCacheHandle(LocalCachePool pool,
      String name, long maxSize, long maxAge,
      com.google.common.cache.Cache<?, ?> cache) {
    this.pool = pool;
    this.name = name;
    this.maxSize = maxSize;
    this.maxAge = maxAge;
    this.cache = new WeakReference<com.google.common.cache.Cache<?, ?>>(cache);
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

  public long size() {
    com.google.common.cache.Cache<?,?> c = cache.get();
    return c != null ? c.size() : 0;
  }

  @Nullable
  public CacheStats stats() {
    com.google.common.cache.Cache<?,?> c = cache.get();
    return c != null ? c.stats() : null;
  }

  public void invalidateAll() {
    com.google.common.cache.Cache<?,?> c = cache.get();
    if (c != null) {
      c.invalidateAll();
    }
  }

  @Override
  public void remove() {
    pool.remove(getName(), this);
    cache.clear();
  }
}
