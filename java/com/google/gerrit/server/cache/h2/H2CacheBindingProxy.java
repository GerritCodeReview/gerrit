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

package com.google.gerrit.server.cache.h2;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.Weigher;
import com.google.gerrit.server.cache.CacheBinding;
import com.google.gerrit.server.cache.h2.H2CacheImpl.ValueHolder;
import com.google.inject.TypeLiteral;
import java.util.concurrent.TimeUnit;

class H2CacheBindingProxy<K, V> implements CacheBinding<K, V> {
  private static final RuntimeException NOT_SUPPORTED =
      new RuntimeException("This is read-only wrapper. Modifications are not supported");

  private final CacheBinding<K, V> source;

  H2CacheBindingProxy(CacheBinding<K, V> source) {
    this.source = source;
  }

  @Override
  public Long expireAfterWrite(TimeUnit unit) {
    return source.expireAfterWrite(unit);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Weigher<K, V> weigher() {
    Weigher<K, V> weigher = source.weigher();
    if (weigher == null) {
      return null;
    }

    // introduce weigher that performs calculations
    // on value that is being stored not on ValueHolder
    return (Weigher<K, V>)
        new Weigher<K, ValueHolder<V>>() {
          @Override
          public int weigh(K key, ValueHolder<V> value) {
            return weigher.weigh(key, value.value);
          }
        };
  }

  @Override
  public String name() {
    return source.name();
  }

  @Override
  public TypeLiteral<K> keyType() {
    return source.keyType();
  }

  @Override
  public TypeLiteral<V> valueType() {
    return source.valueType();
  }

  @Override
  public long maximumWeight() {
    return source.maximumWeight();
  }

  @Override
  public long diskLimit() {
    return source.diskLimit();
  }

  @Override
  public CacheLoader<K, V> loader() {
    return source.loader();
  }

  @Override
  public CacheBinding<K, V> maximumWeight(long weight) {
    throw NOT_SUPPORTED;
  }

  @Override
  public CacheBinding<K, V> diskLimit(long limit) {
    throw NOT_SUPPORTED;
  }

  @Override
  public CacheBinding<K, V> expireAfterWrite(long duration, TimeUnit durationUnits) {
    throw NOT_SUPPORTED;
  }

  @Override
  public CacheBinding<K, V> loader(Class<? extends CacheLoader<K, V>> clazz) {
    throw NOT_SUPPORTED;
  }

  @Override
  public CacheBinding<K, V> weigher(Class<? extends Weigher<K, V>> clazz) {
    throw NOT_SUPPORTED;
  }
}
