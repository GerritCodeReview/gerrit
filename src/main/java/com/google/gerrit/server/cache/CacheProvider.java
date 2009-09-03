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

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import net.sf.ehcache.Ehcache;

import java.util.concurrent.TimeUnit;

final class CacheProvider<K, V> implements Provider<Cache<K, V>>,
    NamedCacheBinding, UnnamedCacheBinding {
  private final boolean disk;
  private int memoryLimit = 1024;
  private int diskLimit = 16384;
  private long timeToIdle = DEFAULT_TIME;
  private long timeToLive = DEFAULT_TIME;
  private EvictionPolicy evictionPolicy = EvictionPolicy.LFU;
  private String cacheName;
  private ProxyEhcache cache;

  CacheProvider(final boolean disk) {
    this.disk = disk;
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

  long timeToIdle() {
    return timeToIdle;
  }

  long timeToLive() {
    return timeToLive;
  }

  EvictionPolicy evictionPolicy() {
    return evictionPolicy;
  }

  public NamedCacheBinding name(final String name) {
    if (cacheName != null) {
      throw new IllegalStateException("Cache name already set");
    }
    cacheName = name;
    return this;
  }

  public NamedCacheBinding memoryLimit(final int objects) {
    memoryLimit = objects;
    return this;
  }

  public NamedCacheBinding diskLimit(final int objects) {
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

  public NamedCacheBinding timeToIdle(final long duration, final TimeUnit unit) {
    if (timeToIdle >= 0) {
      throw new IllegalStateException("Cache timeToIdle already set");
    }
    timeToIdle = TimeUnit.SECONDS.convert(duration, unit);
    return this;
  }

  public NamedCacheBinding timeToLive(final long duration, final TimeUnit unit) {
    if (timeToLive >= 0) {
      throw new IllegalStateException("Cache timeToLive already set");
    }
    timeToLive = TimeUnit.SECONDS.convert(duration, unit);
    return this;
  }

  @Override
  public NamedCacheBinding evictionPolicy(final EvictionPolicy policy) {
    evictionPolicy = policy;
    return this;
  }

  public Cache<K, V> get() {
    if (cache == null) {
      throw new ProvisionException("Cache \"" + cacheName + "\" not available");
    }
    return new SimpleCache<K, V>(cache);
  }
}
