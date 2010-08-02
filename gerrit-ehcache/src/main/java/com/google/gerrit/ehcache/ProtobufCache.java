// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.ehcache;

import com.google.gerrit.server.cache.Cache;
import com.google.gwtorm.protobuf.CodecFactory;
import com.google.gwtorm.protobuf.ProtobufCodec;
import com.google.inject.Provider;

import java.util.concurrent.TimeUnit;

class ProtobufCache<K, V> implements Cache<K, V> {

  private final Cache<SerializableProtobuf<K>, SerializableProtobuf<V>> cache;
  private final ProtobufCodec<K> keyCodec;
  private final ProtobufCodec<V> valueCodec;
  private final Provider<V> valueProvider;

  ProtobufCache(Cache self, Class<K> keyClass, Class<V> valueClass,
      Provider<V> valueProvider) {
    keyCodec = CodecFactory.encoder(keyClass);
    valueCodec = CodecFactory.encoder(valueClass);
    this.valueProvider = valueProvider;
    cache = self;
  }

  @Override
  public V get(K key) {
    SerializableProtobuf<V> val =
        cache.get(new SerializableProtobuf<K>(key, keyCodec));

    return val != null ? val.toObject(valueCodec, valueProvider) : null;
  }

  @Override
  public long getTimeToLive(TimeUnit unit) {
    return cache.getTimeToLive(unit);
  }

  @Override
  public void put(K key, V value) {
    cache.put(new SerializableProtobuf<K>(key, keyCodec),
        new SerializableProtobuf<V>(value, valueCodec));
  }

  @Override
  public void remove(K key) {
    if (key != null) {
      cache.remove(new SerializableProtobuf<K>(key, keyCodec));
    }
  }

  @Override
  public void removeAll() {
    cache.removeAll();
  }

  @Override
  public String toString() {
    return cache.toString();
  }
}
