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

import net.sf.ehcache.Ehcache;

import java.util.concurrent.TimeUnit;

final class CacheProvider<K, V> implements Provider<Cache<K, V>>,
    NamedCacheBinding<K, V>, UnnamedCacheBinding<K, V> {
  private final CacheModule module;
  private final boolean disk;
  private int memoryLimit;
  private int diskLimit;
  private long maxAge;
  private EvictionPolicy evictionPolicy;
  private String cacheName;
  private ProxyEhcache cache;
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

  void bind(final Ehcache ehcache) {
    cache.bind(ehcache);
  }

  String getName() {
    if (cacheName == null) {
      throw new ProvisionException("Cache has no name");
    }
    return cacheName;
  }

  boolean disk() {
    return disk;
  }

  int memoryLimit() {
    return memoryLimit;
  }

  int diskLimit() {
    return diskLimit;
  }

  long maxAge() {
    return maxAge;
  }

  EvictionPolicy evictionPolicy() {
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
    if (entryCreator != null) {
      return new PopulatingCache<K, V>(cache, entryCreator.get());
    }
    return new SimpleCache<K, V>(cache);
  }
}
