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

import com.google.common.base.Throwables;
import com.google.common.cache.AbstractLoadingCache;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.BloomFilter;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.cache.PersistentCache;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.TypeLiteral;
import java.io.IOException;
import java.io.InvalidClassException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Calendar;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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

  private static final ImmutableSet<String> OLD_CLASS_NAMES =
      ImmutableSet.of("com.google.gerrit.server.change.ChangeKind");

  private final Executor executor;
  private final SqlStore<K, V> store;
  private final TypeLiteral<K> keyType;
  private final Cache<K, ValueHolder<V>> mem;

  H2CacheImpl(
      Executor executor,
      SqlStore<K, V> store,
      TypeLiteral<K> keyType,
      Cache<K, ValueHolder<V>> mem) {
    this.executor = executor;
    this.store = store;
    this.keyType = keyType;
    this.mem = mem;
  }

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
      return ((LoadingCache<K, ValueHolder<V>>) mem).get(key).value;
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

              ValueHolder<V> h = new ValueHolder<>(valueLoader.call());
              h.created = TimeUtil.nowMs();
              executor.execute(() -> store.put(key, h));
              return h;
            })
        .value;
  }

  @Override
  public void put(K key, V val) {
    final ValueHolder<V> h = new ValueHolder<>(val);
    h.created = TimeUtil.nowMs();
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

  void prune(ScheduledExecutorService service) {
    store.prune(mem);

    Calendar cal = Calendar.getInstance();
    cal.set(Calendar.HOUR_OF_DAY, 01);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    cal.add(Calendar.DAY_OF_MONTH, 1);

    long delay = cal.getTimeInMillis() - TimeUtil.nowMs();
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError =
        service.schedule(() -> prune(service), delay, TimeUnit.MILLISECONDS);
  }

  static class ValueHolder<V> {
    final V value;
    long created;
    volatile boolean clean;

    ValueHolder(V value) {
      this.value = value;
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

        final ValueHolder<V> h = new ValueHolder<>(loader.load(key));
        h.created = TimeUtil.nowMs();
        executor.execute(() -> store.put(key, h));
        return h;
      }
    }
  }

  static class SqlStore<K, V> {
    private final String url;
    private final KeyType<K> keyType;
    private final CacheSerializer<V> valueSerializer;
    private final int version;
    private final long maxSize;
    @Nullable private final Duration expireAfterWrite;
    private final BlockingQueue<SqlHandle> handles;
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private volatile BloomFilter<K> bloomFilter;
    private int estimatedSize;

    SqlStore(
        String jdbcUrl,
        TypeLiteral<K> keyType,
        CacheSerializer<K> keySerializer,
        CacheSerializer<V> valueSerializer,
        int version,
        long maxSize,
        @Nullable Duration expireAfterWrite) {
      this.url = jdbcUrl;
      this.keyType = createKeyType(keyType, keySerializer);
      this.valueSerializer = valueSerializer;
      this.version = version;
      this.maxSize = maxSize;
      this.expireAfterWrite = expireAfterWrite;

      int cores = Runtime.getRuntime().availableProcessors();
      int keep = Math.min(cores, 16);
      this.handles = new ArrayBlockingQueue<>(keep);
    }

    @SuppressWarnings("unchecked")
    private static <T> KeyType<T> createKeyType(
        TypeLiteral<T> type, CacheSerializer<T> serializer) {
      if (type.getRawType() == String.class) {
        return (KeyType<T>) StringKeyTypeImpl.INSTANCE;
      }
      return new ObjectKeyTypeImpl<>(serializer);
    }

    synchronized void open() {
      if (bloomFilter == null) {
        bloomFilter = buildBloomFilter();
      }
    }

    void close() {
      SqlHandle h;
      while ((h = handles.poll()) != null) {
        h.close();
      }
    }

    boolean mightContain(K key) {
      BloomFilter<K> b = bloomFilter;
      if (b == null) {
        synchronized (this) {
          b = bloomFilter;
          if (b == null) {
            b = buildBloomFilter();
            bloomFilter = b;
          }
        }
      }
      return b == null || b.mightContain(key);
    }

    private BloomFilter<K> buildBloomFilter() {
      SqlHandle c = null;
      try {
        c = acquire();
        if (estimatedSize <= 0) {
          try (PreparedStatement ps =
              c.conn.prepareStatement("SELECT COUNT(*) FROM data WHERE version=?")) {
            ps.setInt(1, version);
            try (ResultSet r = ps.executeQuery()) {
              estimatedSize = r.next() ? r.getInt(1) : 0;
            }
          }
        }

        BloomFilter<K> b = newBloomFilter();
        try (PreparedStatement ps = c.conn.prepareStatement("SELECT k FROM data WHERE version=?")) {
          ps.setInt(1, version);
          try (ResultSet r = ps.executeQuery()) {
            while (r.next()) {
              b.put(keyType.get(r, 1));
            }
          }
        } catch (Exception e) {
          if (Throwables.getCausalChain(e).stream()
              .anyMatch(InvalidClassException.class::isInstance)) {
            // If deserialization failed using default Java serialization, this means we are using
            // the old serialVersionUID-based invalidation strategy. In that case, authors are
            // most likely bumping serialVersionUID rather than using the new versioning in the
            // CacheBinding.  That's ok; we'll continue to support both for now.
            // TODO(dborowitz): Remove this case when Java serialization is no longer used.
            logger.atWarning().log(
                "Entries cached for %s have an incompatible class and can't be deserialized. "
                    + "Cache is flushed.",
                url);
            invalidateAll();
          } else {
            throw e;
          }
        }
        return b;
      } catch (IOException | SQLException e) {
        logger.atWarning().log("Cannot build BloomFilter for %s: %s", url, e.getMessage());
        c = close(c);
        return null;
      } finally {
        release(c);
      }
    }

    ValueHolder<V> getIfPresent(K key) {
      SqlHandle c = null;
      try {
        c = acquire();
        if (c.get == null) {
          c.get = c.conn.prepareStatement("SELECT v, created FROM data WHERE k=? AND version=?");
        }
        keyType.set(c.get, 1, key);

        // Silently no results when the only value in the database is an older version. This will
        // result in put overwriting the stored value with the new version, which is intended.
        c.get.setInt(2, version);

        try (ResultSet r = c.get.executeQuery()) {
          if (!r.next()) {
            missCount.incrementAndGet();
            return null;
          }

          Timestamp created = r.getTimestamp(2);
          if (expired(created)) {
            invalidate(key);
            missCount.incrementAndGet();
            return null;
          }

          V val = valueSerializer.deserialize(r.getBytes(1));
          ValueHolder<V> h = new ValueHolder<>(val);
          h.clean = true;
          hitCount.incrementAndGet();
          touch(c, key);
          return h;
        } finally {
          c.get.clearParameters();
        }
      } catch (IOException | SQLException e) {
        if (!isOldClassNameError(e)) {
          logger.atWarning().withCause(e).log("Cannot read cache %s for %s", url, key);
        }
        c = close(c);
        return null;
      } finally {
        release(c);
      }
    }

    private static boolean isOldClassNameError(Throwable t) {
      for (Throwable c : Throwables.getCausalChain(t)) {
        if (c instanceof ClassNotFoundException && OLD_CLASS_NAMES.contains(c.getMessage())) {
          return true;
        }
      }
      return false;
    }

    private boolean expired(Timestamp created) {
      if (expireAfterWrite == null) {
        return false;
      }
      Duration age = Duration.between(created.toInstant(), TimeUtil.now());
      return age.compareTo(expireAfterWrite) > 0;
    }

    private void touch(SqlHandle c, K key) throws IOException, SQLException {
      if (c.touch == null) {
        c.touch = c.conn.prepareStatement("UPDATE data SET accessed=? WHERE k=? AND version=?");
      }
      try {
        c.touch.setTimestamp(1, TimeUtil.nowTs());
        keyType.set(c.touch, 2, key);
        c.touch.setInt(3, version);
        c.touch.executeUpdate();
      } finally {
        c.touch.clearParameters();
      }
    }

    void put(K key, ValueHolder<V> holder) {
      if (holder.clean) {
        return;
      }

      BloomFilter<K> b = bloomFilter;
      if (b != null) {
        b.put(key);
        bloomFilter = b;
      }

      SqlHandle c = null;
      try {
        c = acquire();
        if (c.put == null) {
          c.put =
              c.conn.prepareStatement(
                  "MERGE INTO data (k, v, version, created, accessed) VALUES(?,?,?,?,?)");
        }
        try {
          keyType.set(c.put, 1, key);
          c.put.setBytes(2, valueSerializer.serialize(holder.value));
          c.put.setInt(3, version);
          c.put.setTimestamp(4, new Timestamp(holder.created));
          c.put.setTimestamp(5, TimeUtil.nowTs());
          c.put.executeUpdate();
          holder.clean = true;
        } finally {
          c.put.clearParameters();
        }
      } catch (IOException | SQLException e) {
        logger.atWarning().withCause(e).log("Cannot put into cache %s", url);
        c = close(c);
      } finally {
        release(c);
      }
    }

    void invalidate(K key) {
      SqlHandle c = null;
      try {
        c = acquire();
        invalidate(c, key);
      } catch (IOException | SQLException e) {
        logger.atWarning().withCause(e).log("Cannot invalidate cache %s", url);
        c = close(c);
      } finally {
        release(c);
      }
    }

    private void invalidate(SqlHandle c, K key) throws IOException, SQLException {
      if (c.invalidate == null) {
        c.invalidate = c.conn.prepareStatement("DELETE FROM data WHERE k=? and version=?");
      }
      try {
        keyType.set(c.invalidate, 1, key);
        c.invalidate.setInt(2, version);
        c.invalidate.executeUpdate();
      } finally {
        c.invalidate.clearParameters();
      }
    }

    void invalidateAll() {
      SqlHandle c = null;
      try {
        c = acquire();
        try (Statement s = c.conn.createStatement()) {
          s.executeUpdate("DELETE FROM data");
        }
        bloomFilter = newBloomFilter();
      } catch (SQLException e) {
        logger.atWarning().withCause(e).log("Cannot invalidate cache %s", url);
        c = close(c);
      } finally {
        release(c);
      }
    }

    void prune(Cache<K, ?> mem) {
      SqlHandle c = null;
      try {
        c = acquire();
        try (PreparedStatement ps = c.conn.prepareStatement("DELETE FROM data WHERE version!=?")) {
          ps.setInt(1, version);
          int oldEntries = ps.executeUpdate();
          if (oldEntries > 0) {
            logger.atInfo().log(
                "Pruned %d entries not matching version %d from cache %s",
                oldEntries, version, url);
          }
        }
        try (Statement s = c.conn.createStatement()) {
          // Compute size without restricting to version (although obsolete data was just pruned
          // anyway).
          long used;
          try (ResultSet r = s.executeQuery("SELECT SUM(space) FROM data")) {
            used = r.next() ? r.getLong(1) : 0;
          }
          if (used <= maxSize) {
            return;
          }

          try (ResultSet r =
              s.executeQuery("SELECT k, space, created FROM data ORDER BY accessed")) {
            while (maxSize < used && r.next()) {
              K key = keyType.get(r, 1);
              Timestamp created = r.getTimestamp(3);
              if (mem.getIfPresent(key) != null && !expired(created)) {
                touch(c, key);
              } else {
                invalidate(c, key);
                used -= r.getLong(2);
              }
            }
          }
        }
      } catch (IOException | SQLException e) {
        logger.atWarning().withCause(e).log("Cannot prune cache %s", url);
        c = close(c);
      } finally {
        release(c);
      }
    }

    DiskStats diskStats() {
      long size = 0;
      long space = 0;
      SqlHandle c = null;
      try {
        c = acquire();
        try (Statement s = c.conn.createStatement();
            // Stats include total size regardless of version.
            ResultSet r = s.executeQuery("SELECT COUNT(*), SUM(space) FROM data")) {
          if (r.next()) {
            size = r.getLong(1);
            space = r.getLong(2);
          }
        }
      } catch (SQLException e) {
        logger.atWarning().withCause(e).log("Cannot get DiskStats for %s", url);
        c = close(c);
      } finally {
        release(c);
      }
      return new DiskStats(size, space, hitCount.get(), missCount.get());
    }

    private SqlHandle acquire() throws SQLException {
      SqlHandle h = handles.poll();
      return h != null ? h : new SqlHandle(url, keyType);
    }

    private void release(SqlHandle h) {
      if (h != null && !handles.offer(h)) {
        h.close();
      }
    }

    private SqlHandle close(SqlHandle h) {
      if (h != null) {
        h.close();
      }
      return null;
    }

    private BloomFilter<K> newBloomFilter() {
      int cnt = Math.max(64 * 1024, 2 * estimatedSize);
      return BloomFilter.create(keyType.funnel(), cnt);
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
