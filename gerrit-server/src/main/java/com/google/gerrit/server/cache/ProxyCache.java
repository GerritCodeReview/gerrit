// Copyright (C) 2010 The Android Open Source Project
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Proxy around a cache which has not yet been created. */
public final class ProxyCache<K, V> implements Cache<K, V> {
  private volatile Cache<K, V> self;

  public void bind(Cache<K, V> self) {
    this.self = self;
  }

  @Override
  public V get(K key) {
    return self.get(key);
  }

  @Override
  public Map<K, V> getAll(Iterable<K> keys) {
    HashMap<K, V> map = new HashMap<K, V>();
    for (K k : keys) {
      if (!map.containsKey(k)) {
        V v = get(k);
        if (v != null) {
          map.put(k, v);
        }
      }
    }
    return map;
  }

  @Override
  public long getTimeToLive(TimeUnit unit) {
    return self.getTimeToLive(unit);
  }

  @Override
  public void put(K key, V value) {
    self.put(key, value);
  }

  @Override
  public void remove(K key) {
    self.remove(key);
  }

  @Override
  public void removeAll() {
    self.removeAll();
  }
}
