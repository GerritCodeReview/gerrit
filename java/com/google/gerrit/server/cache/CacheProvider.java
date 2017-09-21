// Copyright (C) 2012 The Android Open Source Project
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

import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.Weigher;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import java.util.concurrent.TimeUnit;

class CacheProvider<K, V> implements Provider<Cache<K, V>>, CacheBinding<K, V> {
  private final CacheModule module;
  final String name;
  private final TypeLiteral<K> keyType;
  private final TypeLiteral<V> valType;
  private boolean persist;
  private long maximumWeight;
  private long diskLimit;
  private Long expireAfterWrite;
  private Provider<CacheLoader<K, V>> loader;
  private Provider<Weigher<K, V>> weigher;

  private String plugin;
  private MemoryCacheFactory memoryCacheFactory;
  private PersistentCacheFactory persistentCacheFactory;
  private boolean frozen;

  CacheProvider(CacheModule module, String name, TypeLiteral<K> keyType, TypeLiteral<V> valType) {
    this.module = module;
    this.name = name;
    this.keyType = keyType;
    this.valType = valType;
  }

  @Inject(optional = true)
  void setPluginName(@PluginName String pluginName) {
    this.plugin = pluginName;
  }

  @Inject
  void setMemoryCacheFactory(MemoryCacheFactory factory) {
    this.memoryCacheFactory = factory;
  }

  @Inject(optional = true)
  void setPersistentCacheFactory(@Nullable PersistentCacheFactory factory) {
    this.persistentCacheFactory = factory;
  }

  CacheBinding<K, V> persist(boolean p) {
    Preconditions.checkState(!frozen, "binding frozen, cannot be modified");
    persist = p;
    return this;
  }

  @Override
  public CacheBinding<K, V> maximumWeight(long weight) {
    Preconditions.checkState(!frozen, "binding frozen, cannot be modified");
    maximumWeight = weight;
    return this;
  }

  @Override
  public CacheBinding<K, V> diskLimit(long limit) {
    Preconditions.checkState(!frozen, "binding frozen, cannot be modified");
    Preconditions.checkState(persist, "diskLimit supported for persistent caches only");
    diskLimit = limit;
    return this;
  }

  @Override
  public CacheBinding<K, V> expireAfterWrite(long duration, TimeUnit unit) {
    Preconditions.checkState(!frozen, "binding frozen, cannot be modified");
    expireAfterWrite = SECONDS.convert(duration, unit);
    return this;
  }

  @Override
  public CacheBinding<K, V> loader(Class<? extends CacheLoader<K, V>> impl) {
    Preconditions.checkState(!frozen, "binding frozen, cannot be modified");
    loader = module.bindCacheLoader(this, impl);
    return this;
  }

  @Override
  public CacheBinding<K, V> weigher(Class<? extends Weigher<K, V>> impl) {
    Preconditions.checkState(!frozen, "binding frozen, cannot be modified");
    weigher = module.bindWeigher(this, impl);
    return this;
  }

  @Override
  public String name() {
    if (!Strings.isNullOrEmpty(plugin)) {
      return plugin + "." + name;
    }
    return name;
  }

  @Override
  public TypeLiteral<K> keyType() {
    return keyType;
  }

  @Override
  public TypeLiteral<V> valueType() {
    return valType;
  }

  @Override
  public long maximumWeight() {
    return maximumWeight;
  }

  @Override
  public long diskLimit() {
    if (diskLimit > 0) {
      return diskLimit;
    }
    return 128 << 20;
  }

  @Override
  @Nullable
  public Long expireAfterWrite(TimeUnit unit) {
    return expireAfterWrite != null ? unit.convert(expireAfterWrite, SECONDS) : null;
  }

  @Override
  @Nullable
  public Weigher<K, V> weigher() {
    return weigher != null ? weigher.get() : null;
  }

  @Override
  @Nullable
  public CacheLoader<K, V> loader() {
    return loader != null ? loader.get() : null;
  }

  @Override
  public Cache<K, V> get() {
    frozen = true;

    if (loader != null) {
      CacheLoader<K, V> ldr = loader.get();
      if (persist && persistentCacheFactory != null) {
        return persistentCacheFactory.build(this, ldr);
      }
      return memoryCacheFactory.build(this, ldr);
    } else if (persist && persistentCacheFactory != null) {
      return persistentCacheFactory.build(this);
    } else {
      return memoryCacheFactory.build(this);
    }
  }
}
