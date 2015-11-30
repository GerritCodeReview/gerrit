// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

/**
 * A pair of {@link HashBasedTable} and {@link HashMultimap} with redundant
 * storage of keys that allows efficient lookup by both keys.
 *
 * @param <K1> type of first key
 * @param <K2> type of second key
 * @param <V> type of value
 */
public class HashBasedBiTable<K1, K2, V> implements Serializable {
  private static final long serialVersionUID = 1L;

  private final HashBasedTable<K1, K2, V> byFirstKey;
  private final HashMultimap<K2, K1> bySecondKey;

  public static <K1, K2, V> HashBasedBiTable<K1, K2, V> create() {
    return new HashBasedBiTable<>();
  }

  public static <K1, K2, V> HashBasedBiTable<K1, K2, V> create(
      HashBasedBiTable<K1, K2, V> mapPair) {
    return new HashBasedBiTable<>(HashBasedTable.create(mapPair.byFirstKey),
        HashMultimap.create(mapPair.bySecondKey));
  }

  private HashBasedBiTable() {
    this.byFirstKey = HashBasedTable.create();
    this.bySecondKey = HashMultimap.create();
  }

  private HashBasedBiTable(HashBasedTable<K1, K2, V> byFirstKey,
      HashMultimap<K2, K1> bySecondKey) {
    this.byFirstKey = byFirstKey;
    this.bySecondKey = bySecondKey;
  }

  public synchronized V get(K1 key1, K2 key2) {
    return byFirstKey.get(key1, key2);
  }

  public synchronized ImmutableSet<K2> getByFirstKey(K1 key1) {
    return ImmutableSet.copyOf(byFirstKey.row(key1).keySet());
  }

  public synchronized ImmutableSet<K1> getBySecondKey(K2 key2) {
    return ImmutableSet.copyOf(bySecondKey.get(key2));
  }

  public synchronized ImmutableMap<K2, V> getMapByFirstKey(K1 key1) {
    return ImmutableMap.copyOf(byFirstKey.row(key1));
  }

  public synchronized ImmutableMap<K1, V> getMapBySecondKey(K2 key2) {
    ImmutableMap.Builder<K1, V> b = ImmutableMap.builder();
    for (K1 key1 : bySecondKey.get(key2)) {
      b.put(key1, byFirstKey.get(key1, key2));
    }
    return b.build();
  }

  public synchronized boolean put(K1 key1, K2 key2, V value) {
    byFirstKey.put(key1, key2, value);
    return bySecondKey.put(key2, key1);
  }

  public synchronized boolean remove(K1 key1, K2 key2) {
    byFirstKey.remove(key1, key2);
    return bySecondKey.remove(key2, key1);
  }

  public synchronized ImmutableSet<K2> removeAllByFirstKey(K1 key1) {
    Map<K2, V> removed = byFirstKey.rowMap().remove(key1);
    if (removed == null) {
      return ImmutableSet.of();
    }
    Set<K2> removedKeys = removed.keySet();
    for (K2 key2 : removedKeys) {
      bySecondKey.remove(key2, key1);
    }
    return ImmutableSet.copyOf(removedKeys);
  }

  public synchronized ImmutableSet<K1> removeAllBySecondKey(K2 key2) {
    Set<K1> removedKeys = bySecondKey.removeAll(key2);
    for (K1 key1 : removedKeys) {
      byFirstKey.remove(key1, key2);
    }
    return ImmutableSet.copyOf(removedKeys);
  }
}
