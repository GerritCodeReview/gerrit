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

/**
 * Creates a cache entry on demand when its not found.
 *
 * @param <K> type of the cache's key.
 * @param <V> type of the cache's value element.
 */
public abstract class EntryCreator<K, V> {
  /**
   * Invoked on a cache miss, to compute the cache entry.
   *
   * @param key entry whose content needs to be obtained.
   * @return new cache content. The caller will automatically put this object
   *         into the cache.
   * @throws Exception the cache content cannot be computed. No entry will be
   *         stored in the cache, and {@link #missing(Object)} will be invoked
   *         instead. Future requests for the same key will retry this method.
   */
  public abstract V createEntry(K key) throws Exception;

  /** Invoked when {@link #createEntry(Object)} fails, by default return null. */
  public V missing(K key) {
    return null;
  }
}
