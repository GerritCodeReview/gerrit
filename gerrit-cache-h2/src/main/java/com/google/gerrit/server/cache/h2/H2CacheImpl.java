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
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;
import com.google.common.hash.PrimitiveSink;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.server.cache.PersistentCache;
import com.google.inject.TypeLiteral;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
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
import org.h2.jdbc.JdbcSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private static final Logger log = LoggerFactory.getLogger(H2CacheImpl.class);

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

  private static class KeyType<K> {
    String columnType() {
      return "OTHER";
    }

    @SuppressWarnings("unchecked")
    K get(ResultSet rs, int col) throws SQLException {
      return (K) rs.getObject(col);
    }

    void set(PreparedStatement ps, int col, K value) throws SQLException {
      ps.setObject(col, value, Types.JAVA_OBJECT);
    }

    Funnel<K> funnel() {
      return new Funnel<K>() {
        private static final long serialVersionUID = 1L;

        @Override
        public void funnel(K from, PrimitiveSink into) {
          try (ObjectOutputStream ser = new ObjectOutputStream(new SinkOutputStream(into))) {
            ser.writeObject(from);
            ser.flush();
          } catch (IOException err) {
            throw new RuntimeException("Cannot hash as Serializable", err);
          }
        }
      };
    }

    @SuppressWarnings("unchecked")
    static <K> KeyType<K> create(TypeLiteral<K> type) {
      if (type.getRawType() == String.class) {
        return (KeyType<K>) STRING;
      }
      return (KeyType<K>) OTHER;
    }

    static final KeyType<?> OTHER = new KeyType<>();
    static final KeyType<String> STRING =
        new KeyType<String>() {
          @Override
          String columnType() {
            return "VARCHAR(4096)";
          }

          @Override
          String get(ResultSet rs, int col) throws SQLException {
            return rs.getString(col);
          }

          @Override
          void set(PreparedStatement ps, int col, String value) throws SQLException {
            ps.setString(col, value);
          }

          @SuppressWarnings("unchecked")
          @Override
          Funnel<String> funnel() {
            Funnel<?> s = Funnels.unencodedCharsFunnel();
            return (Funnel<String>) s;
          }
        };
  }

  static class SqlStore<K, V> {
    private final String url;
    private final KeyType<K> keyType;
    private final long maxSize;
    private final long expireAfterWrite;
    private final BlockingQueue<SqlHandle> handles;
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private volatile BloomFilter<K> bloomFilter;
    private int estimatedSize;

    SqlStore(String jdbcUrl, TypeLiteral<K> keyType, long maxSize, long expireAfterWrite) {
      this.url = jdbcUrl;
      this.keyType = KeyType.create(keyType);
      this.maxSize = maxSize;
      this.expireAfterWrite = expireAfterWrite;

      int cores = Runtime.getRuntime().availableProcessors();
      int keep = Math.min(cores, 16);
      this.handles = new ArrayBlockingQueue<>(keep);
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
        try (Statement s = c.conn.createStatement()) {
          if (estimatedSize <= 0) {
            try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM data")) {
              estimatedSize = r.next() ? r.getInt(1) : 0;
            }
          }

          BloomFilter<K> b = newBloomFilter();
          try (ResultSet r = s.executeQuery("SELECT k FROM data")) {
            while (r.next()) {
              b.put(keyType.get(r, 1));
            }
          } catch (JdbcSQLException e) {
            if (e.getCause() instanceof InvalidClassException) {
              log.warn(
                  "Entries cached for "
                      + url
                      + " have an incompatible class and can't be deserialized. "
                      + "Cache is flushed.");
              invalidateAll();
            } else {
              throw e;
            }
          }
          return b;
        }
      } catch (SQLException e) {
        log.warn("Cannot build BloomFilter for " + url + ": " + e.getMessage());
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
          c.get = c.conn.prepareStatement("SELECT v, created FROM data WHERE k=?");
        }
        keyType.set(c.get, 1, key);
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

          @SuppressWarnings("unchecked")
          V val = (V) r.getObject(1);
          ValueHolder<V> h = new ValueHolder<>(val);
          h.clean = true;
          hitCount.incrementAndGet();
          touch(c, key);
          return h;
        } finally {
          c.get.clearParameters();
        }
      } catch (SQLException e) {
        if (!isOldClassNameError(e)) {
          log.warn("Cannot read cache " + url + " for " + key, e);
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
      if (expireAfterWrite == 0) {
        return false;
      }
      long age = TimeUtil.nowMs() - created.getTime();
      return 1000 * expireAfterWrite < age;
    }

    private void touch(SqlHandle c, K key) throws SQLException {
      if (c.touch == null) {
        c.touch = c.conn.prepareStatement("UPDATE data SET accessed=? WHERE k=?");
      }
      try {
        c.touch.setTimestamp(1, TimeUtil.nowTs());
        keyType.set(c.touch, 2, key);
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
              c.conn.prepareStatement("MERGE INTO data (k, v, created, accessed) VALUES(?,?,?,?)");
        }
        try {
          keyType.set(c.put, 1, key);
          c.put.setObject(2, holder.value, Types.JAVA_OBJECT);
          c.put.setTimestamp(3, new Timestamp(holder.created));
          c.put.setTimestamp(4, TimeUtil.nowTs());
          c.put.executeUpdate();
          holder.clean = true;
        } finally {
          c.put.clearParameters();
        }
      } catch (SQLException e) {
        log.warn("Cannot put into cache " + url, e);
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
      } catch (SQLException e) {
        log.warn("Cannot invalidate cache " + url, e);
        c = close(c);
      } finally {
        release(c);
      }
    }

    private void invalidate(SqlHandle c, K key) throws SQLException {
      if (c.invalidate == null) {
        c.invalidate = c.conn.prepareStatement("DELETE FROM data WHERE k=?");
      }
      try {
        keyType.set(c.invalidate, 1, key);
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
        log.warn("Cannot invalidate cache " + url, e);
        c = close(c);
      } finally {
        release(c);
      }
    }

    void prune(Cache<K, ?> mem) {
      SqlHandle c = null;
      try {
        c = acquire();
        try (Statement s = c.conn.createStatement()) {
          long used = 0;
          try (ResultSet r = s.executeQuery("SELECT SUM(space) FROM data")) {
            used = r.next() ? r.getLong(1) : 0;
          }
          if (used <= maxSize) {
            return;
          }

          try (ResultSet r =
              s.executeQuery(
                  "SELECT" + " k" + ",space" + ",created" + " FROM data" + " ORDER BY accessed")) {
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
      } catch (SQLException e) {
        log.warn("Cannot prune cache " + url, e);
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
            ResultSet r = s.executeQuery("SELECT" + " COUNT(*)" + ",SUM(space)" + " FROM data")) {
          if (r.next()) {
            size = r.getLong(1);
            space = r.getLong(2);
          }
        }
      } catch (SQLException e) {
        log.warn("Cannot get DiskStats for " + url, e);
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
          log.warn("Cannot close connection to " + url, e);
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
          log.warn("Cannot close statement for " + url, e);
        }
      }
      return null;
    }
  }

  private static class SinkOutputStream extends OutputStream {
    private final PrimitiveSink sink;

    SinkOutputStream(PrimitiveSink sink) {
      this.sink = sink;
    }

    @Override
    public void write(int b) {
      sink.putByte((byte) b);
    }

    @Override
    public void write(byte[] b, int p, int n) {
      sink.putBytes(b, p, n);
    }
  }
}
