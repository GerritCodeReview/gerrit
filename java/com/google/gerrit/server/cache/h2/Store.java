// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.cache.h2;

import com.google.common.cache.Cache;
import com.google.gerrit.server.cache.PersistentCache.DiskStats;
import com.google.gerrit.server.cache.h2.H2CacheImpl.ValueHolder;
import java.io.IOException;
import java.time.Instant;

/** The disk part of an {@link H2CacheImpl}. */
interface Store<K, V> {
  String url();

  void open();

  void close();

  /** Replaces the stored data with an empty store, keeping the old data aside. */
  default void reinit() throws IOException {
    throw new UnsupportedOperationException("reinit not supported");
  }

  boolean mightContain(K key);

  ValueHolder<V> getIfPresent(K key);

  boolean needsRefresh(Instant created);

  void put(K key, ValueHolder<V> holder);

  void invalidate(K key);

  void invalidateAll();

  void prune(Cache<K, ?> mem);

  DiskStats diskStats();
}
