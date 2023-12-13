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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Duration;

/** Configure a cache declared within a {@link CacheModule} instance. */
public interface CacheBinding<K, V> {
  /** Set the total size of the cache. */
  @CanIgnoreReturnValue
  CacheBinding<K, V> maximumWeight(long weight);

  /** Set the time an element lives after last write before being expired. */
  @CanIgnoreReturnValue
  CacheBinding<K, V> expireAfterWrite(Duration duration);

  /** Set the time an element lives after last access before being expired. */
  @CanIgnoreReturnValue
  CacheBinding<K, V> expireFromMemoryAfterAccess(Duration duration);

  /**
   * Set the time that an element will be refreshed after. Elements older than this but younger than
   * {@link #expireAfterWrite(Duration)} will still be returned, but on access a task is queued to
   * refresh their value asynchronously.
   */
  @CanIgnoreReturnValue
  CacheBinding<K, V> refreshAfterWrite(Duration duration);

  /** Populate the cache with items from the CacheLoader. */
  @CanIgnoreReturnValue
  CacheBinding<K, V> loader(Class<? extends CacheLoader<K, V>> clazz);

  /** Algorithm to weigh an object with a method other than the unit weight 1. */
  @CanIgnoreReturnValue
  CacheBinding<K, V> weigher(Class<? extends Weigher<K, V>> clazz);

  /**
   * Set the config name to something other than the cache name.
   *
   * @see CacheDef#configKey()
   */
  @CanIgnoreReturnValue
  CacheBinding<K, V> configKey(String configKey);
}
