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
  private long timeToIdle = -1;
  private long timeToLive = -1;
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

  long timeToIdle() {
    return timeToIdle;
  }

  long timeToLive() {
    return timeToLive;
  }

  public NamedCacheBinding name(final String name) {
    if (cacheName != null) {
      throw new IllegalStateException("Cache name already set");
    }
    cacheName = name;
    return this;
  }

  public NamedCacheBinding timeToIdle(final int duration, final TimeUnit unit) {
    if (timeToIdle >= 0) {
      throw new IllegalStateException("Cache timeToIdle already set");
    }
    timeToIdle = TimeUnit.SECONDS.convert(duration, unit);
    return this;
  }

  public NamedCacheBinding timeToLive(final int duration, final TimeUnit unit) {
    if (timeToLive >= 0) {
      throw new IllegalStateException("Cache timeToLive already set");
    }
    timeToLive = TimeUnit.SECONDS.convert(duration, unit);
    return this;
  }

  public Cache<K, V> get() {
    if (cache == null) {
      throw new ProvisionException("Cache \"" + cacheName + "\" not available");
    }
    return new SimpleCache<K, V>(cache);
  }
}
