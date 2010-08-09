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

import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * A fast in-memory and/or on-disk based cache.
 *
 * @type <K> type of key used to lookup entries in the cache.
 * @type <V> type of value stored within each cache entry.
 */
public interface Cache<K, V> {
  /** Get the element from the cache, or null if not stored in the cache. */
  public V get(K key);

  /**
   * Get a map containing entries for elements with the specified keys; no entry
   * will be returned for a key if there is no element in the cache with that
   * key. An empty map will be returned if none of the keys have elements in the
   * cache. Duplicate keys will be ignored.
   */
  public Map<K, V> getAll(Iterable<K> keys);

  /** Put one element into the cache, replacing any existing value. */
  public void put(K key, V value);

  /** Remove any existing value from the cache, no-op if not present. */
  public void remove(K key);

  /** Remove all cached items. */
  public void removeAll();

  /**
   * Get the time an element will survive in the cache.
   *
   * @param unit desired units of the return value.
   * @return time an item can live before being purged.
   */
  public long getTimeToLive(TimeUnit unit);
}
