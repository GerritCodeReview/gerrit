// Copyright (C) 2018 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.Weigher;
import com.google.gerrit.common.Nullable;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import java.util.concurrent.TimeUnit;

class PersistentCacheProvider<K, V> extends CacheProvider<K, V>
    implements Provider<Cache<K, V>>, PersistentCacheBinding<K, V>, PersistentCacheDef<K, V> {
  private long diskLimit;
  private CacheSerializer<K> keySerializer;
  private CacheSerializer<V> valueSerializer;

  private PersistentCacheFactory persistentCacheFactory;

  PersistentCacheProvider(
      CacheModule module, String name, TypeLiteral<K> keyType, TypeLiteral<V> valType) {
    super(module, name, keyType, valType);
  }

  @Inject(optional = true)
  void setPersistentCacheFactory(@Nullable PersistentCacheFactory factory) {
    this.persistentCacheFactory = factory;
  }

  @Override
  public PersistentCacheBinding<K, V> maximumWeight(long weight) {
    return (PersistentCacheBinding<K, V>) super.maximumWeight(weight);
  }

  @Override
  public PersistentCacheBinding<K, V> expireAfterWrite(long duration, TimeUnit durationUnits) {
    return (PersistentCacheBinding<K, V>) super.expireAfterWrite(duration, durationUnits);
  }

  @Override
  public PersistentCacheBinding<K, V> loader(Class<? extends CacheLoader<K, V>> clazz) {
    return (PersistentCacheBinding<K, V>) super.loader(clazz);
  }

  @Override
  public PersistentCacheBinding<K, V> weigher(Class<? extends Weigher<K, V>> clazz) {
    return (PersistentCacheBinding<K, V>) super.weigher(clazz);
  }

  @Override
  public PersistentCacheBinding<K, V> keySerializer(CacheSerializer<K> keySerializer) {
    this.keySerializer = keySerializer;
    return this;
  }

  @Override
  public PersistentCacheBinding<K, V> valueSerializer(CacheSerializer<V> valueSerializer) {
    this.valueSerializer = valueSerializer;
    return this;
  }

  @Override
  public PersistentCacheBinding<K, V> diskLimit(long limit) {
    checkNotFrozen();
    diskLimit = limit;
    return this;
  }

  @Override
  public long diskLimit() {
    if (diskLimit > 0) {
      return diskLimit;
    }
    return 128 << 20;
  }

  @Override
  public CacheSerializer<K> keySerializer() {
    return keySerializer;
  }

  @Override
  public CacheSerializer<V> valueSerializer() {
    return valueSerializer;
  }

  @Override
  public Cache<K, V> get() {
    if (persistentCacheFactory == null) {
      return super.get();
    }
    checkState(keySerializer != null, "keySerializer is required");
    checkState(valueSerializer != null, "valueSerializer is required");
    freeze();
    CacheLoader<K, V> ldr = loader();
    return ldr != null
        ? persistentCacheFactory.build(this, ldr)
        : persistentCacheFactory.build(this);
  }
}
