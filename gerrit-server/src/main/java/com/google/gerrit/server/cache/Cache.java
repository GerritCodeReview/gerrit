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

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.TimeUnit;

/**
 * An in-memory and/or on-disk based cache.
 *
 * The cache may need to perform operations over the network, in which case the
 * returned Future can be used to wait for operation completion. Opening
 * multiple concurrent Futures on such a cache may permit the cache to batch the
 * operations together (but that is implementation specific).
 *
 * @type <K> type of key used to lookup entries in the cache.
 * @type <V> type of value stored within each cache entry.
 */
public interface Cache<K, V> {
  /** Get the element from the cache, or null if not stored in the cache. */
  public ListenableFuture<V> get(K key);

  /** Put one element into the cache, replacing any existing value. */
  public ListenableFuture<Void> putAsync(K key, V value);

  /** Remove any existing value from the cache, no-op if not present. */
  public ListenableFuture<Void> removeAsync(K key);

  /** Remove all cached items. */
  public ListenableFuture<Void> removeAllAsync();

  /**
   * Get the configured time an element will survive in the cache.
   *
   * @param unit desired units of the return value.
   * @return time an item can live before being purged.
   */
  public long getTimeToLive(TimeUnit unit);
}
