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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.cache.PersistentCache.DiskStats;
import com.google.gerrit.server.cache.h2.H2CacheImpl.SqlStore;
import com.google.gerrit.server.cache.h2.H2CacheImpl.ValueHolder;
import com.google.gerrit.server.util.time.TimeUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** A {@link Store} that can replace its underlying {@link SqlStore} while it is in use. */
class ReinitableStore<K, V> implements Store<K, V> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Supplier<SqlStore<K, V>> storeFactory;
  private final AtomicReference<SqlStore<K, V>> store;

  ReinitableStore(Supplier<SqlStore<K, V>> storeFactory) {
    this.storeFactory = storeFactory;
    this.store = new AtomicReference<>(storeFactory.get());
  }

  /**
   * Closes the current store, moves its file aside as {@code <name>.mv.db.old-<millis>} so that it
   * can be inspected, and continues with a new store.
   */
  @Override
  public void reinit() throws IOException {
    SqlStore<K, V> old = store.get();
    try {
      old.close();
      Path file = old.cacheFilePath;
      if (file != null && Files.exists(file)) {
        Path backup = file.resolveSibling(file.getFileName() + ".old-" + TimeUtil.nowMs());
        Files.move(file, backup);
        logger.atWarning().log("Reinitialized cache %s, backup: %s", url(), backup);
      }
    } finally {
      SqlStore<K, V> fresh = storeFactory.get();
      fresh.open();
      store.set(fresh);
    }
  }

  @Override
  public String url() {
    return store.get().url();
  }

  @Override
  public void open() {
    store.get().open();
  }

  @Override
  public void close() {
    store.get().close();
  }

  @Override
  public boolean mightContain(K key) {
    return store.get().mightContain(key);
  }

  @Override
  public ValueHolder<V> getIfPresent(K key) {
    return store.get().getIfPresent(key);
  }

  @Override
  public boolean needsRefresh(Instant created) {
    return store.get().needsRefresh(created);
  }

  @Override
  public void put(K key, ValueHolder<V> holder) {
    store.get().put(key, holder);
  }

  @Override
  public void invalidate(K key) {
    store.get().invalidate(key);
  }

  @Override
  public void invalidateAll() {
    store.get().invalidateAll();
  }

  @Override
  public void prune(Cache<K, ?> mem) {
    store.get().prune(mem);
  }

  @Override
  public DiskStats diskStats() {
    return store.get().diskStats();
  }
}
