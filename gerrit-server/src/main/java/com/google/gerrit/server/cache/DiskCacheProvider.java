// Copyright (C) 2009 The Android Open Source Project
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

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import java.util.concurrent.TimeUnit;

public final class DiskCacheProvider<K, V>
    implements Provider<Cache<K, V>>, DiskCacheBinding<K, V> {
  private final String cacheName;
  private final CacheModule module;
  private int memoryLimit;
  private int diskLimit;
  private long maxAge;
  private Cache<K, V> cache;
  private Provider<EntryCreator<K, V>> entryCreator;

  DiskCacheProvider(String name, CacheModule module) {
    this.cacheName = name;
    this.module = module;
  }

  @Inject
  void setCachePool(DiskCachePool pool) {
    this.cache = pool.register(this);
  }

  public void bind(Cache<K, V> impl) {
    if (cache instanceof ProxyCache) {
      ((ProxyCache<K, V>) cache).bind(impl);
    } else if (cache == null) {
      throw new ProvisionException("Cache was never registered");
    } else {
      throw new ProvisionException("Cache is not a proxy, cannot rebind");
    }
  }

  public EntryCreator<K, V> getEntryCreator() {
    return entryCreator != null ? entryCreator.get() : null;
  }

  public String getName() {
    return cacheName;
  }

  public int memoryLimit() {
    return memoryLimit;
  }

  public int diskLimit() {
    return diskLimit;
  }

  public long maxAge() {
    return maxAge;
  }

  @Override
  public DiskCacheBinding<K, V> memoryLimit(int objects) {
    memoryLimit = objects;
    return this;
  }

  @Override
  public DiskCacheBinding<K, V> diskLimit(int objects) {
    diskLimit = objects;
    return this;
  }

  @Override
  public DiskCacheBinding<K, V> maxAge(long duration, TimeUnit unit) {
    maxAge = SECONDS.convert(duration, unit);
    return this;
  }

  @Override
  public DiskCacheBinding<K, V> populateWith(
      Class<? extends EntryCreator<K, V>> creator) {
    entryCreator = module.getEntryCreator(this, creator);
    return this;
  }

  @Override
  public Cache<K, V> get() {
    if (cache == null) {
      throw new ProvisionException("Cache \"" + cacheName + "\" not available");
    }
    return cache;
  }
}
