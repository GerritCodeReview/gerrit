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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;

import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

class CacheProvider<K, V>
    implements Provider<Cache<K, V>>,
    CacheBinding<K, V> {
  final String name;
  final TypeLiteral<K> keyType;
  final TypeLiteral<V> valType;
  private final CacheModule module;
  private long maxSize;
  private long maxAge;
  private boolean persist;
  private Provider<CacheLoader<K, V>> loader;
  private PersistentCacheFactory store;

  CacheProvider(String name,
      TypeLiteral<K> keyType,
      TypeLiteral<V> valType,
      CacheModule module) {
    this.name = name;
    this.keyType = keyType;
    this.valType = valType;
    this.module = module;
  }

  @Inject
  void setPersistentCacheFactory(@Nullable PersistentCacheFactory factory) {
    this.store = factory;
  }

  CacheBinding<K, V> persist(boolean p) {
    persist = p;
    return this;
  }

  @Override
  public CacheBinding<K, V> memoryLimit(int objects) {
    maxSize = objects;
    return this;
  }

  @Override
  public CacheBinding<K, V> maxAge(long duration, TimeUnit unit) {
    maxAge = SECONDS.convert(duration, unit);
    return this;
  }

  @Override
  public CacheBinding<K, V> populateWith(Class<? extends CacheLoader<K, V>> impl) {
    loader = module.bindCacheLoader(this, impl);
    return this;
  }

  @Override
  public Cache<K, V> get() {
    CacheBuilder<K, V> builder = newCacheBuilder();
    builder.maximumSize(maxSize);
    if (0 < maxAge) {
      builder.expireAfterWrite(maxAge, TimeUnit.SECONDS);
    }
    if (loader != null) {
      CacheLoader<K, V> ldr = loader.get();
      if (persist && store != null) {
        return store.build(name, keyType, valType, builder, ldr);
      }
      return builder.build(ldr);
    } else if (persist && store != null) {
      return store.build(name, keyType, valType, builder);
    } else {
      return builder.build();
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static <K, V> CacheBuilder<K, V> newCacheBuilder() {
    CacheBuilder builder = CacheBuilder.newBuilder();
    return builder;
  }
}
