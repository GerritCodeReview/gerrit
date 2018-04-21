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

import com.google.common.hash.Funnel;
import com.google.common.hash.PrimitiveSink;
import com.google.gerrit.server.cache.CacheSerializer;
import com.google.gerrit.server.cache.h2.H2CacheImpl.SinkOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

class ObjectKeyTypeImpl<K, V> implements EntryType<K, V> {
  private final CacheSerializer<K> keySerializer;
  private final CacheSerializer<V> valueSerializer;

  ObjectKeyTypeImpl(CacheSerializer<K> keySerializer, CacheSerializer<V> valueSerializer) {
    this.keySerializer = keySerializer;
    this.valueSerializer = valueSerializer;
  }

  @Override
  public String keyColumnType() {
    return "OTHER";
  }

  @Override
  public Funnel<K> keyFunnel() {
    return new Funnel<K>() {
      private static final long serialVersionUID = 1L;

      @Override
      public void funnel(K from, PrimitiveSink into) {
        try (ObjectOutputStream ser = new ObjectOutputStream(new SinkOutputStream(into))) {
          ser.writeObject(from);
          ser.flush();
        } catch (IOException err) {
          throw new RuntimeException("Cannot hash as Serializable", err);
        }
      }
    };
  }

  @Override
  public CacheSerializer<K> keySerializer() {
    return keySerializer;
  }

  @Override
  public CacheSerializer<V> valueSerializer() {
    return valueSerializer;
  }
}
