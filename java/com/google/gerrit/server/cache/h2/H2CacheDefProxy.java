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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.cache.PersistentCacheDef;
import com.google.gerrit.server.cache.h2.H2CacheImpl.ValueHolder;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.inject.TypeLiteral;
import java.time.Duration;

class H2CacheDefProxy<K, V> implements PersistentCacheDef<K, V> {
  private final PersistentCacheDef<K, V> source;

  H2CacheDefProxy(PersistentCacheDef<K, V> source) {
    this.source = source;
  }

  @Override
  @Nullable
  public Duration expireAfterWrite() {
    return source.expireAfterWrite();
  }

  @Override
  @Nullable
  public Duration expireFromMemoryAfterAccess() {
    return source.expireFromMemoryAfterAccess();
  }

  @Override
  public Weigher<K, V> weigher() {
    Weigher<K, V> weigher = source.weigher();
    if (weigher == null) {
      return null;
    }

    // introduce weigher that performs calculations
    // on value that is being stored not on ValueHolder
    Weigher<K, ValueHolder<V>> holderWeigher = (k, v) -> weigher.weigh(k, v.value);
    @SuppressWarnings("unchecked")
    Weigher<K, V> ret = (Weigher<K, V>) holderWeigher;
    return ret;
  }

  @Override
  public String name() {
    return source.name();
  }

  @Override
  public String configKey() {
    return source.configKey();
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
  public int version() {
    return source.version();
  }

  @Override
  public CacheSerializer<K> keySerializer() {
    return source.keySerializer();
  }

  @Override
  public CacheSerializer<V> valueSerializer() {
    return source.valueSerializer();
  }
}
