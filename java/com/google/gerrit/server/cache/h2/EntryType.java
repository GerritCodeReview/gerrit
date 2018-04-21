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
import com.google.gerrit.server.cache.CacheSerializer;
import com.google.inject.TypeLiteral;

interface EntryType<K, V> {
  @SuppressWarnings("unchecked")
  static <K, V> EntryType<K, V> create(
      TypeLiteral<K> keyType,
      CacheSerializer<K> keySerializer,
      CacheSerializer<V> valueSerializer) {
    if (keyType.getRawType() == String.class) {
      return (EntryType<K, V>)
          new StringKeyTypeImpl<>((CacheSerializer<String>) keySerializer, valueSerializer);
    }
    return new ObjectKeyTypeImpl<>(keySerializer, valueSerializer);
  }

  String keyColumnType();

  Funnel<K> keyFunnel();

  CacheSerializer<K> keySerializer();

  CacheSerializer<V> valueSerializer();
}
