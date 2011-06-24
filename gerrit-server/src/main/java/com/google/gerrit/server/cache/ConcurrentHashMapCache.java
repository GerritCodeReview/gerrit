// Copyright (C) 2011 The Android Open Source Project
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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.server.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * An infinitely sized cache backed by java.util.ConcurrentHashMap.
 * <p>
 * This cache type is only suitable for unit tests, as it has no upper limit on
 * number of items held in the cache. No upper limit can result in memory leaks
 * in production servers.
 */
public class ConcurrentHashMapCache<K, V> implements Cache<K, V> {
  private final ConcurrentHashMap<K, V> map = new ConcurrentHashMap<K, V>();

  @Override
  public V get(K key) {
    return map.get(key);
  }

  @Override
  public void put(K key, V value) {
    map.put(key, value);
  }

  @Override
  public void remove(K key) {
    map.remove(key);
  }

  @Override
  public void removeAll() {
    map.clear();
  }

  @Override
  public long getTimeToLive(TimeUnit unit) {
    return 0;
  }
}
