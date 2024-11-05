// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.cache.AbstractLoadingCache;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.cache.PersistentCache;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.TypeLiteral;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * Hybrid in-memory and database backed cache built on H2.
 *
 * <p>This cache can be used as either a recall cache, or a loading cache if a CacheLoader was
 * supplied to its constructor at build time. Before creating an entry the in-memory cache is
 * checked for the item, then the database is checked, and finally the CacheLoader is used to
 * construct the item. This is mostly useful for CacheLoaders that are computationally intensive,
 * such as the PatchListCache.
 *
 * <p>Cache stores and invalidations are performed on a background thread, hiding the latency
 * associated with serializing the key and value pairs and writing them to the database log.
 *
 * <p>A BloomFilter is used around the database to reduce the number of SELECTs issued against the
 * database for new cache items that have not been seen before, a common operation for the
 * PatchListCache. The BloomFilter is sized when the cache starts to be 64,000 entries or double the
 * number of items currently in the database table.
 *
 * <p>This cache does not export its items as a ConcurrentMap.
 *
 * @see H2CacheFactory
 */
public class H2CacheImpl<K, V> extends AbstractLoadingCache<K, V> implements PersistentCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Executor executor;
  private final SqlStore<K, V> store;
  private final TypeLiteral<K> keyType;
  private final Cache<K, ValueHolder<V>> mem;
  private final String cacheName;

  H2CacheImpl(
      Executor executor,
      SqlStore<K, V> store,
      TypeLiteral<K> keyType,
      Cache<K, ValueHolder<V>> mem) {
    this.executor = executor;
    this.store = store;
    this.keyType = keyType;
    this.mem = mem;
    this.cacheName = store.getUrl().substring(store.getUrl().lastIndexOf('/') + 1);
  }

  @Nullable
  @Override
  public V getIfPresent(Object objKey) {
    if (!keyType.getRawType().isInstance(objKey)) {
      return null;
    }

    @SuppressWarnings("unchecked")
    K key = (K) objKey;

    ValueHolder<V> h = mem.getIfPresent(key);
    if (h != null) {
      return h.value;
    }

    if (store.mightContain(key)) {
      h = store.getIfPresent(key);
      if (h != null) {
        mem.put(key, h);
        return h.value;
      }
    }
    return null;
  }

  @Override
  public V get(K key) throws ExecutionException {
    if (mem instanceof LoadingCache) {
      LoadingCache<K, ValueHolder<V>> asLoadingCache = (LoadingCache<K, ValueHolder<V>>) mem;
      ValueHolder<V> valueHolder = asLoadingCache.get(key);
      if (store.needsRefresh(valueHolder.created)) {
        asLoadingCache.refresh(key);
      }
      return valueHolder.value;
    }
    throw new UnsupportedOperationException();
  }

  @Override
  public ImmutableMap<K, V> getAll(Iterable<? extends K> keys) throws ExecutionException {
    if (mem instanceof LoadingCache) {
      ImmutableMap.Builder<K, V> result = ImmutableMap.builder();
      LoadingCache<K, ValueHolder<V>> asLoadingCache = (LoadingCache<K, ValueHolder<V>>) mem;
      ImmutableMap<K, ValueHolder<V>> values = asLoadingCache.getAll(keys);
      for (Map.Entry<K, ValueHolder<V>> entry : values.entrySet()) {
        result.put(entry.getKey(), entry.getValue().value);
        if (store.needsRefresh(entry.getValue().created)) {
          asLoadingCache.refresh(entry.getKey());
        }
      }
      return result.build();
    }
    throw new UnsupportedOperationException();
  }

  @Override
  public V get(K key, Callable<? extends V> valueLoader) throws ExecutionException {
    return mem.get(
            key,
            () -> {
              if (store.mightContain(key)) {
                ValueHolder<V> h = store.getIfPresent(key);
                if (h != null) {
                  return h;
                }
              }

              ValueHolder<V> h =
                  new ValueHolder<>(valueLoader.call(), Instant.ofEpochMilli(TimeUtil.nowMs()));
              executor.execute(() -> store.put(key, h));
              return h;
            })
        .value;
  }

  @Override
  public void put(K key, V val) {
    final ValueHolder<V> h = new ValueHolder<>(val, Instant.ofEpochMilli(TimeUtil.nowMs()));
    mem.put(key, h);
    executor.execute(() -> store.put(key, h));
  }

  @SuppressWarnings("unchecked")
  @Override
  public void invalidate(Object key) {
    if (keyType.getRawType().isInstance(key) && store.mightContain((K) key)) {
      executor.execute(() -> store.invalidate((K) key));
    }
    mem.invalidate(key);
  }

  @Override
  public void invalidateAll() {
    store.invalidateAll();
    mem.invalidateAll();
  }

  @Override
  public long size() {
    return mem.size();
  }

  @Override
  public CacheStats stats() {
    return mem.stats();
  }

  @Override
  public DiskStats diskStats() {
    return store.diskStats();
  }

  void start() {
    store.open();
  }

  void stop() {
    for (Map.Entry<K, ValueHolder<V>> e : mem.asMap().entrySet()) {
      ValueHolder<V> h = e.getValue();
      if (!h.clean) {
        store.put(e.getKey(), h);
      }
    }
    store.close();
  }

  void prune() {
    logger.atFine().log("Pruning cache %s...", cacheName);
    store.prune(mem);
    logger.atFine().log("Finished pruning cache %s...", cacheName);
  }

  String getCacheName() {
    return cacheName;
  }

  static class ValueHolder<V> {
    final V value;
    final Instant created;
    volatile boolean clean;

    ValueHolder(V value, Instant created) {
      this.value = value;
      this.created = created;
    }
  }

  static class Loader<K, V> extends CacheLoader<K, ValueHolder<V>> {
    private final Executor executor;
    private final SqlStore<K, V> store;
    private final CacheLoader<K, V> loader;

    Loader(Executor executor, SqlStore<K, V> store, CacheLoader<K, V> loader) {
      this.executor = executor;
      this.store = store;
      this.loader = loader;
    }

    @Override
    public ValueHolder<V> load(K key) throws Exception {
      try (TraceTimer timer =
          TraceContext.newTimer(
              "Loading value from cache", Metadata.builder().cacheKey(key.toString()).build())) {
        if (store.mightContain(key)) {
          ValueHolder<V> h = store.getIfPresent(key);
          if (h != null) {
            return h;
          }
        }

        final ValueHolder<V> h =
            new ValueHolder<>(loader.load(key), Instant.ofEpochMilli(TimeUtil.nowMs()));
        executor.execute(() -> store.put(key, h));
        return h;
      }
    }

    @Override
    public Map<K, ValueHolder<V>> loadAll(Iterable<? extends K> keys) throws Exception {
      try (TraceTimer timer = TraceContext.newTimer("Loading multiple values from cache")) {
        List<K> notInMemory = new ArrayList<>();
        Map<K, ValueHolder<V>> result = new HashMap<>();
        for (K key : keys) {
          if (!store.mightContain(key)) {
            notInMemory.add(key);
            continue;
          }
          ValueHolder<V> h = store.getIfPresent(key);
          if (h != null) {
            result.put(key, h);
          } else {
            notInMemory.add(key);
          }
        }
        try {
          Map<K, V> remaining = loader.loadAll(notInMemory);
          Instant instant = Instant.ofEpochMilli(TimeUtil.nowMs());
          storeInDatabase(remaining, instant);
          remaining
              .entrySet()
              .forEach(e -> result.put(e.getKey(), new ValueHolder<>(e.getValue(), instant)));
        } catch (UnsupportedLoadingOperationException e) {
          // Fallback to the default load() if loadAll() is not implemented
          for (K k : notInMemory) {
            result.put(k, load(k)); // No need to storeInDatabase here; load(k) does that.
          }
        }
        return result;
      }
    }

    @Override
    public ListenableFuture<ValueHolder<V>> reload(K key, ValueHolder<V> oldValue)
        throws Exception {
      ListenableFuture<V> reloadedValue = loader.reload(key, oldValue.value);
      Futures.addCallback(
          reloadedValue,
          new FutureCallback<V>() {
            @Override
            public void onSuccess(V result) {
              store.put(key, new ValueHolder<>(result, TimeUtil.now()));
            }

            @Override
            public void onFailure(Throwable t) {
              logger.atWarning().withCause(t).log("Unable to reload cache value");
            }
          },
          executor);

      return Futures.transform(reloadedValue, v -> new ValueHolder<>(v, TimeUtil.now()), executor);
    }

    private void storeInDatabase(Map<K, V> entries, Instant instant) {
      executor.execute(
          () -> {
            for (Map.Entry<K, V> entry : entries.entrySet()) {
              store.put(entry.getKey(), new ValueHolder<>(entry.getValue(), instant));
            }
          });
    }
  }

  static class SqlHandle {
    private final String url;
    Connection conn;
    PreparedStatement get;
    PreparedStatement put;
    PreparedStatement touch;
    PreparedStatement invalidate;

    SqlHandle(String url, KeyType<?> type) throws SQLException {
      this.url = url;
      this.conn = org.h2.Driver.load().connect(url, null);
      try (Statement stmt = conn.createStatement()) {
        stmt.addBatch(
            "CREATE TABLE IF NOT EXISTS data"
                + "(k "
                + type.columnType()
                + " NOT NULL PRIMARY KEY HASH"
                + ",v OTHER NOT NULL"
                + ",created TIMESTAMP NOT NULL"
                + ",accessed TIMESTAMP NOT NULL"
                + ")");
        stmt.addBatch(
            "ALTER TABLE data ADD COLUMN IF NOT EXISTS "
                + "space BIGINT AS OCTET_LENGTH(k) + OCTET_LENGTH(v)");
        stmt.addBatch("ALTER TABLE data ADD COLUMN IF NOT EXISTS version INT DEFAULT 0 NOT NULL");
        stmt.addBatch("CREATE INDEX IF NOT EXISTS version_key ON data(version, k)");
        stmt.addBatch("CREATE INDEX IF NOT EXISTS accessed ON data(accessed)");
        stmt.executeBatch();
      }
    }

    void close() {
      get = closeStatement(get);
      put = closeStatement(put);
      touch = closeStatement(touch);
      invalidate = closeStatement(invalidate);

      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException e) {
          logger.atWarning().withCause(e).log("Cannot close connection to %s", url);
        } finally {
          conn = null;
        }
      }
    }

    @Nullable
    private PreparedStatement closeStatement(PreparedStatement ps) {
      if (ps != null) {
        try {
          ps.close();
        } catch (SQLException e) {
          logger.atWarning().withCause(e).log("Cannot close statement for %s", url);
        }
      }
      return null;
    }
  }
}
