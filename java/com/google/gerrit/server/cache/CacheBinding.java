// Copyright (C) 2009 The Android Open Source Project
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
import java.time.Duration;

/** Configure a cache declared within a {@link CacheModule} instance. */
public interface CacheBinding<K, V> {
  /** Set the total size of the cache. */
  CacheBinding<K, V> maximumWeight(long weight);

  /** Set the time an element lives after last write before being expired. */
  CacheBinding<K, V> expireAfterWrite(Duration duration);

  /** Set the time an element lives after last access before being expired. */
  CacheBinding<K, V> expireFromMemoryAfterAccess(Duration duration);

  /** Set the time after last write after which an element will be refreshed in the background. */
  CacheBinding<K, V> refreshAfterWrite(Duration duration);

  /**
   * Use something other than {@code refreshAfterWrite} for the key name in the config for
   * configuring {@link #refreshAfterWrite(Duration)}.
   *
   * @deprecated only for backwards compatibility, specifically for the project cache to use {@code
   *     cache.projects.checkFrequency}.
   */
  @Deprecated
  CacheBinding<K, V> refreshAfterWriteConfigName(String config);

  /** Populate the cache with items from the CacheLoader. */
  CacheBinding<K, V> loader(Class<? extends CacheLoader<K, V>> clazz);

  /** Algorithm to weigh an object with a method other than the unit weight 1. */
  CacheBinding<K, V> weigher(Class<? extends Weigher<K, V>> clazz);

  /**
   * Set the config name to something other than the cache name.
   *
   * @see CacheDef#configKey()
   */
  CacheBinding<K, V> configKey(String configKey);
}
