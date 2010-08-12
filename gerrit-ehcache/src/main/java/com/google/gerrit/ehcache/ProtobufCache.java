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

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
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
  private final Function<SerializableProtobuf<V>, V> unpack;

  ProtobufCache(Cache<SerializableProtobuf<K>, SerializableProtobuf<V>> self,
      Class<K> keyClass, Class<V> valueClass, Provider<V> valProvider) {
    keyCodec = CodecFactory.encoder(keyClass);
    valueCodec = CodecFactory.encoder(valueClass);
    valueProvider = valProvider;
    cache = self;

    unpack = new Function<SerializableProtobuf<V>, V>() {
      @Override
      public V apply(SerializableProtobuf<V> val) {
        return val != null ? val.toObject(valueCodec, valueProvider) : null;
      }
    };
  }

  @Override
  public ListenableFuture<V> get(K key) {
    return Futures.compose(cache.get(wrapKey(key)), unpack);
  }

  @Override
  public ListenableFuture<Void> putAsync(final K key, final V value) {
    return cache.putAsync(wrapKey(key), wrapValue(value));
  }

  @Override
  public ListenableFuture<Void> removeAsync(final K key) {
    if (key != null) {
      return cache.removeAsync(wrapKey(key));
    } else {
      return Futures.immediateFuture(null);
    }
  }

  @Override
  public ListenableFuture<Void> removeAllAsync() {
    return cache.removeAllAsync();
  }

  @Override
  public long getTimeToLive(TimeUnit unit) {
    return cache.getTimeToLive(unit);
  }

  @Override
  public String toString() {
    return cache.toString();
  }

  private SerializableProtobuf<K> wrapKey(K key) {
    return new SerializableProtobuf<K>(key, keyCodec);
  }

  private SerializableProtobuf<V> wrapValue(final V value) {
    return new SerializableProtobuf<V>(value, valueCodec);
  }
}
