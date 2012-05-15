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

import static com.google.gerrit.server.cache.EvictionPolicy.LFU;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import java.util.concurrent.TimeUnit;

public final class CacheProvider<K, V> implements Provider<Cache<K, V>>,
    NamedCacheBinding<K, V>, UnnamedCacheBinding<K, V> {
  private final CacheModule module;
  private final boolean disk;
  private int memoryLimit;
  private int diskLimit;
  private long maxAge;
  private EvictionPolicy evictionPolicy;
  private String cacheName;
  private Cache<K, V> cache;
  private Provider<EntryCreator<K, V>> entryCreator;

  CacheProvider(final boolean disk, CacheModule module) {
    this.disk = disk;
    this.module = module;

    memoryLimit(1024);
    maxAge(90, DAYS);
    evictionPolicy(LFU);

    if (disk) {
      diskLimit(16384);
    }
  }

  @Inject
  void setCachePool(final CachePool pool) {
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
    if (cacheName == null) {
      throw new ProvisionException("Cache has no name");
    }
    return cacheName;
  }

  public boolean disk() {
    return disk;
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

  public EvictionPolicy evictionPolicy() {
    return evictionPolicy;
  }

  public NamedCacheBinding<K, V> name(final String name) {
    if (cacheName != null) {
      throw new IllegalStateException("Cache name already set");
    }
    cacheName = name;
    return this;
  }

  public NamedCacheBinding<K, V> memoryLimit(final int objects) {
    memoryLimit = objects;
    return this;
  }

  public NamedCacheBinding<K, V> diskLimit(final int objects) {
    if (!disk) {
      // TODO This should really be a compile time type error, but I'm
      // too lazy to create the mess of permutations required to setup
      // type safe returns for bindings in our little DSL.
      //
      throw new IllegalStateException("Cache is not disk based");
    }
    diskLimit = objects;
    return this;
  }

  public NamedCacheBinding<K, V> maxAge(final long duration, final TimeUnit unit) {
    maxAge = SECONDS.convert(duration, unit);
    return this;
  }

  @Override
  public NamedCacheBinding<K, V> evictionPolicy(final EvictionPolicy policy) {
    evictionPolicy = policy;
    return this;
  }

  public NamedCacheBinding<K, V> populateWith(
      Class<? extends EntryCreator<K, V>> creator) {
    entryCreator = module.getEntryCreator(this, creator);
    return this;
  }

  public Cache<K, V> get() {
    if (cache == null) {
      throw new ProvisionException("Cache \"" + cacheName + "\" not available");
    }
    return cache;
  }
}
