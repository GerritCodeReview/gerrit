// Copyright (C) 2019 The Android Open Source Project
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
import com.google.inject.TypeLiteral;

class PersistentLegacyCacheProvider<K, V> extends PersistentCacheProvider<K, V> {
  PersistentLegacyCacheProvider(
      CacheModule module, String name, TypeLiteral<K> keyType, TypeLiteral<V> valType) {
    super(module, name, keyType, valType);
  }

  @Override
  public Cache<K, V> get() {
    if (persistentCacheFactory == null) {
      return super.get();
    }
    checkState(version >= 0, "version is required");
    checkSerializer(keyType(), keySerializer, "key");
    checkSerializer(valueType(), valueSerializer, "value");
    freeze();
    CacheLoader<K, V> ldr = loader();
    return ldr != null
        ? persistentCacheFactory.buildLegacy(this, ldr)
        : persistentCacheFactory.buildLegacy(this);
  }
}
