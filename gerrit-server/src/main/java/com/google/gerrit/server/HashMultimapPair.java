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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;

import java.io.Serializable;
import java.util.Set;

/**
 * A pair of {@link HashMultimap} with redundant storage that allows efficient
 * lookup by two keys.
 *
 * @param <K1> type of first key
 * @param <K2> type of second key
 */
class HashMultimapPair<K1, K2> implements Serializable {
  private static final long serialVersionUID = 1L;

  private final HashMultimap<K1, K2> byFirstKey;
  private final HashMultimap<K2, K1> bySecondKey;

  public static <K1, K2> HashMultimapPair<K1, K2> create() {
    return new HashMultimapPair<>();
  }

  public static <K1, K2> HashMultimapPair<K1, K2> create(
      HashMultimapPair<K1, K2> mapPair) {
    return new HashMultimapPair<>(HashMultimap.create(mapPair.byFirstKey),
        HashMultimap.create(mapPair.bySecondKey));
  }

  private HashMultimapPair() {
    this.byFirstKey = HashMultimap.create();
    this.bySecondKey = HashMultimap.create();
  }

  private HashMultimapPair(HashMultimap<K1, K2> byFirstKey,
      HashMultimap<K2, K1> bySecondKey) {
    this.byFirstKey = byFirstKey;
    this.bySecondKey = bySecondKey;
  }

  public boolean contains(K1 key1, K2 key2) {
    return byFirstKey.containsEntry(key1, key2);
  }

  public ImmutableSet<K2> getByFirstKey(K1 key1) {
    return ImmutableSet.copyOf(byFirstKey.get(key1));
  }

  public ImmutableSet<K1> getBySecondKey(K2 key2) {
    return ImmutableSet.copyOf(bySecondKey.get(key2));
  }

  public synchronized boolean put(K1 key1, K2 key2) {
    byFirstKey.put(key1, key2);
    return bySecondKey.put(key2, key1);
  }

  public synchronized boolean remove(K1 key1, K2 key2) {
    byFirstKey.remove(key1, key2);
    return bySecondKey.remove(key2, key1);
  }

  public synchronized ImmutableSet<K2> removeAllByFirstKey(K1 key1) {
    Set<K2> removedKeys = byFirstKey.removeAll(key1);
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
