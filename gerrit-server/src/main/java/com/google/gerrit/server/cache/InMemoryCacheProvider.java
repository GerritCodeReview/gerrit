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

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import org.eclipse.jgit.lib.Config;

import java.util.concurrent.TimeUnit;

class InMemoryCacheProvider<K, V>
    implements Provider<Cache<K, V>>, InMemoryCacheBinding<K, V> {
  private final String name;
  private final CacheModule module;
  private long maxSize;
  private long maxAge;
  private Provider<EntryCreator<K, V>> entryCreator;
  private Config config;

  InMemoryCacheProvider(String name,
      CacheModule module) {
    this.name = name;
    this.module = module;
  }

  String getName() {
    return name;
  }

  @Inject
  void setGerritServerConfig(@GerritServerConfig Config cfg) {
    this.config = cfg;
  }

  @Override
  public InMemoryCacheBinding<K, V> memoryLimit(int objects) {
    maxSize = objects;
    return this;
  }

  @Override
  public InMemoryCacheBinding<K, V> maxAge(long duration, TimeUnit unit) {
    maxAge = SECONDS.convert(duration, unit);
    return this;
  }

  @Override
  public InMemoryCacheBinding<K, V> populateWith(
      Class<? extends EntryCreator<K, V>> creator) {
    entryCreator = module.getEntryCreator(this, creator);
    return this;
  }

  @Override
  public Cache<K, V> get() {
    if (config == null) {
      throw new ProvisionException("Need @GerritServerConfig to make cache");
    }

    @SuppressWarnings("unchecked")
    CacheBuilder<K, V> builder = (CacheBuilder<K, V>) CacheBuilder.newBuilder();

    long size = config.getLong("cache", name, "memoryLimit", maxSize);
    if (0 < size) {
      builder.maximumSize(size);
    }

    long expires = lookupMaxAgeSeconds();
    if (0 < expires) {
      builder.expireAfterWrite(expires, TimeUnit.SECONDS);
    }

    com.google.common.cache.Cache<K, V> store;
    if (entryCreator != null) {
      final EntryCreator<K, V> creator = entryCreator.get();
      store = builder.build(new CacheLoader<K, V>() {
        @Override
        public V load(K key) throws Exception {
          try {
            return creator.createEntry(key);
          } catch (Exception err) {
            return creator.missing(key);
          }
        }
      });
    } else {
      store = builder.build();
    }

    if (store instanceof LoadingCache) {
      return new GuavaBackedCache.Loading<K, V>(
          name, size, expires,
          (LoadingCache<K, V>) store);
    } else {
      return new GuavaBackedCache<K, V>(name, size, expires, store);
    }
  }

  private long lookupMaxAgeSeconds() {
    return MINUTES.toSeconds(ConfigUtil.getTimeUnit(config,
        "cache", name, "maxAge",
        SECONDS.toMinutes(maxAge), MINUTES));
  }
}
