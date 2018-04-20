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

import com.google.common.cache.CacheLoader;
import com.google.common.cache.Weigher;
import java.util.concurrent.TimeUnit;

/** Configure a persistent cache declared within a {@link CacheModule} instance. */
public interface PersistentCacheBinding<K, V> extends CacheBinding<K, V> {
  @Override
  PersistentCacheBinding<K, V> maximumWeight(long weight);

  @Override
  PersistentCacheBinding<K, V> expireAfterWrite(long duration, TimeUnit durationUnits);

  @Override
  PersistentCacheBinding<K, V> loader(Class<? extends CacheLoader<K, V>> clazz);

  @Override
  PersistentCacheBinding<K, V> weigher(Class<? extends Weigher<K, V>> clazz);

  /** Set the total on-disk limit of the cache */
  PersistentCacheBinding<K, V> diskLimit(long limit);
}
