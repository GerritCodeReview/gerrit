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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.CacheInfo;
import com.google.gerrit.server.cache.PersistentCache;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.util.concurrent.ConcurrentBloomFilter;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
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
    this.cacheName = store.url.substring(store.url.lastIndexOf('/') + 1);
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
    if (h == null) {
      h = store.getIfPresent(key);
      if (h == null) {
        return null;
      }
      mem.put(key, h);
    }
    return h.value;
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
              ValueHolder<V> h = store.getIfPresent(key);
              if (h == null) {
                h = new ValueHolder<>(valueLoader.call(), Instant.ofEpochMilli(TimeUtil.nowMs()));
                ValueHolder<V> fh = h;
                executor.execute(() -> store.put(key, fh));
              }
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
      store.invalidate((K) key);
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
        ValueHolder<V> h = store.getIfPresent(key);
        if (h == null) {
          h = new ValueHolder<>(loader.load(key), Instant.ofEpochMilli(TimeUtil.nowMs()));
          ValueHolder<V> fh = h;
          executor.execute(() -> store.put(key, fh));
        }
        return h;
      }
    }

    @Override
    public Map<K, ValueHolder<V>> loadAll(Iterable<? extends K> keys) throws Exception {
      try (TraceTimer timer = TraceContext.newTimer("Loading multiple values from cache")) {
        List<K> notInMemory = new ArrayList<>();
        Map<K, ValueHolder<V>> result = new HashMap<>();
        for (K key : keys) {
          ValueHolder<V> h = store.getIfPresent(key);
          if (h == null) {
            notInMemory.add(key);
            continue;
          }
          result.put(key, h);
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

  static class SqlStore<K, V> {
    private final String url;
    private final KeyType<K> keyType;
    private final CacheSerializer<V> valueSerializer;
    private final int version;
    private final long maxSize;
    @Nullable private final Duration expireAfterWrite;
    @Nullable private final Duration refreshAfterWrite;
    private final BlockingQueue<SqlHandle> handles;
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final ConcurrentBloomFilter<K> bloomFilter;
    private final boolean buildBloomFilter;
    private boolean trackLastAccess;
    private final AtomicBoolean isDiskCacheReadOnly;
    private volatile boolean ensuredSchemaCreation;

    SqlStore(
        String jdbcUrl,
        TypeLiteral<K> keyType,
        CacheSerializer<K> keySerializer,
        CacheSerializer<V> valueSerializer,
        int version,
        long maxSize,
        int maxInvalidated,
        @Nullable Duration expireAfterWrite,
        @Nullable Duration refreshAfterWrite,
        boolean buildBloomFilter,
        boolean trackLastAccess,
        AtomicBoolean isDiskCacheReadOnly) {
      this.url = jdbcUrl;
      this.keyType = createKeyType(keyType, keySerializer);
      this.valueSerializer = valueSerializer;
      this.version = version;
      this.maxSize = maxSize;
      this.expireAfterWrite = expireAfterWrite;
      this.refreshAfterWrite = refreshAfterWrite;
      this.buildBloomFilter = buildBloomFilter;
      this.trackLastAccess = trackLastAccess;
      this.isDiskCacheReadOnly = isDiskCacheReadOnly;

      int cores = Runtime.getRuntime().availableProcessors();
      int keep = Math.min(cores, 16);
      this.handles = new ArrayBlockingQueue<>(keep);
      bloomFilter =
          new ConcurrentBloomFilter<>(
              this.keyType.funnel(),
              buildBloomFilter ? this::buildBloomFilter : () -> {},
              maxInvalidated);
    }

    @SuppressWarnings("unchecked")
    private static <T> KeyType<T> createKeyType(
        TypeLiteral<T> type, CacheSerializer<T> serializer) {
      if (type.getRawType() == String.class) {
        return (KeyType<T>) StringKeyTypeImpl.INSTANCE;
      }
      return new ObjectKeyTypeImpl<>(serializer);
    }

    private void createSchema() throws SQLException {
      if (!ensuredSchemaCreation) {
        synchronized (this) {
          if (ensuredSchemaCreation) {
            return;
          }
          try (SqlHandle h = new SqlHandle(url)) {
            try (Statement stmt = h.conn.createStatement()) {
              stmt.addBatch(
                  "CREATE TABLE IF NOT EXISTS data"
                      + "(k "
                      + keyType.columnType()
                      + " NOT NULL PRIMARY KEY HASH"
                      + ",v OTHER NOT NULL"
                      + ",created TIMESTAMP NOT NULL"
                      + ",accessed TIMESTAMP NOT NULL"
                      + ")");
              stmt.addBatch(
                  "ALTER TABLE data ADD COLUMN IF NOT EXISTS "
                      + "space BIGINT AS OCTET_LENGTH(k) + OCTET_LENGTH(v)");
              stmt.addBatch(
                  "ALTER TABLE data ADD COLUMN IF NOT EXISTS version INT DEFAULT 0 NOT NULL");
              stmt.addBatch("CREATE INDEX IF NOT EXISTS version_key ON data(version, k)");
              stmt.addBatch("CREATE INDEX IF NOT EXISTS accessed ON data(accessed)");
              stmt.executeBatch();
              ensuredSchemaCreation = true;
            }
          }
        }
      }
    }

    void open() {
      bloomFilter.initIfNeeded();
    }

    void close() {
      SqlHandle h;
      while ((h = handles.poll()) != null) {
        h.close();
      }
      shutdown();
    }

    private void shutdown() {
      try (SqlHandle h = new SqlHandle(url)) {
        try (Statement stmt = h.conn.createStatement()) {
          stmt.execute("SHUTDOWN");
        }
      } catch (SQLException e) {
        logger.atSevere().withCause(e).log("Cannot shutdown cache %s", url);
      }
    }

    boolean mightContain(K key) {
      ConcurrentBloomFilter<K> b = bloomFilter;
      if (buildBloomFilter && b == null) {
        synchronized (this) {
          b = bloomFilter;
          if (b == null) {
            buildBloomFilter();
            b = bloomFilter;
          }
        }
      }
      return b == null || b.mightContain(key);
    }

    private void buildBloomFilter() {
      SqlHandle c = null;
      try (TraceTimer ignored = TraceContext.newTimer("Build bloom filter", Metadata.empty())) {
        c = acquire();
        if (bloomFilter.getEstimatedSize() <= 0) {
          try (PreparedStatement ps =
              c.conn.prepareStatement("SELECT COUNT(*) FROM data WHERE version=?")) {
            ps.setInt(1, version);
            try (ResultSet r = ps.executeQuery()) {
              bloomFilter.setEstimatedSize(r.next() ? r.getInt(1) : 0);
            }
          }
        }

        try (PreparedStatement ps = c.conn.prepareStatement("SELECT k FROM data WHERE version=?")) {
          ps.setInt(1, version);
          try (ResultSet r = ps.executeQuery()) {
            while (r.next()) {
              bloomFilter.buildPut(keyType.get(r, 1));
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
        bloomFilter.build();
      } catch (IOException | SQLException e) {
        logger.atWarning().log("Cannot build BloomFilter for %s: %s", url, e.getMessage());
        c = close(c);
      } finally {
        release(c);
      }
    }

    @Nullable
    ValueHolder<V> getIfPresent(K key) {
      if (!mightContain(key)) {
        return null;
      }

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
          if (expired(created.toInstant())) {
            invalidate(key);
            missCount.incrementAndGet();
            return null;
          }

          V val = valueSerializer.deserialize(r.getBytes(1));
          ValueHolder<V> h = new ValueHolder<>(val, created.toInstant());
          h.clean = true;
          hitCount.incrementAndGet();
          if (trackLastAccess) {
            touch(c, key);
          }
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

    private boolean expired(Instant created) {
      if (expireAfterWrite == null) {
        return false;
      }
      Duration age = Duration.between(created, TimeUtil.now());
      return age.compareTo(expireAfterWrite) > 0;
    }

    private boolean needsRefresh(Instant created) {
      if (refreshAfterWrite == null) {
        return false;
      }
      Duration age = Duration.between(created, TimeUtil.now());
      return age.compareTo(refreshAfterWrite) > 0;
    }

    private void touch(SqlHandle c, K key) throws IOException, SQLException {
      if (isDiskCacheReadOnly.get()) {
        return;
      }
      if (c.touch == null) {
        c.touch = c.conn.prepareStatement("UPDATE data SET accessed=? WHERE k=? AND version=?");
      }
      try {
        c.touch.setTimestamp(1, new Timestamp(TimeUtil.nowMs()));
        keyType.set(c.touch, 2, key);
        c.touch.setInt(3, version);
        c.touch.executeUpdate();
      } finally {
        c.touch.clearParameters();
      }
    }

    void put(K key, ValueHolder<V> holder) {
      if (isDiskCacheReadOnly.get() || holder.clean) {
        return;
      }

      bloomFilter.put(key);

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
          c.put.setTimestamp(4, Timestamp.from(holder.created));
          c.put.setTimestamp(5, new Timestamp(TimeUtil.nowMs()));
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
      if (isDiskCacheReadOnly.get()) {
        return;
      }
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
      bloomFilter.invalidate(key);
    }

    void invalidateAll() {
      if (isDiskCacheReadOnly.get()) {
        return;
      }
      SqlHandle c = null;
      try {
        c = acquire();
        try (Statement s = c.conn.createStatement()) {
          s.executeUpdate("DELETE FROM data");
        }
        bloomFilter.clear();
      } catch (SQLException e) {
        logger.atWarning().withCause(e).log("Cannot invalidate cache %s", url);
        c = close(c);
      } finally {
        release(c);
      }
    }

    synchronized void prune(Cache<K, ?> mem) {
      if (isDiskCacheReadOnly.get()) {
        return;
      }
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
          String formattedMaxSize = CacheInfo.EntriesInfo.bytes(maxSize);
          if (used <= maxSize) {
            logger.atFine().log(
                "Cache %s size (%s) is less than maxSize (%s), not pruning",
                url, CacheInfo.EntriesInfo.bytes(used), formattedMaxSize);
            return;
          }

          try (ResultSet r =
              s.executeQuery("SELECT k, space, created FROM data ORDER BY accessed")) {
            logger.atInfo().log(
                "Cache %s size (%s) is greater than maxSize (%s), pruning",
                url, CacheInfo.EntriesInfo.bytes(used), formattedMaxSize);
            while (maxSize < used && r.next()) {
              K key = keyType.get(r, 1);
              Timestamp created = r.getTimestamp(3);
              if (mem.getIfPresent(key) != null && !expired(created.toInstant())) {
                touch(c, key);
              } else {
                invalidate(c, key);
                used -= r.getLong(2);
              }
            }
            // We should be building a new bloomfilter here as we are iterating over
            // many entries anyway. The "sum' query above does not return all the rows,
            // but it likely has to read them from disk to get their size and that is
            // likely to be the slow part anyway?
            logger.atInfo().log(
                "Done pruning cache %s, size (%s) is now less than maxSize (%s)",
                url, CacheInfo.EntriesInfo.bytes(used), formattedMaxSize);
          }
        }
      } catch (IOException | SQLException e) {
        logger.atWarning().withCause(e).log("Cannot prune cache %s", url);
        c = close(c);
      } finally {
        release(c);
        bloomFilter.startBuildIfNeeded();
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
      return new DiskStats(
          size, space, hitCount.get(), missCount.get(), bloomFilter.getInvalidatedCount());
    }

    private SqlHandle acquire() throws SQLException {
      createSchema();
      SqlHandle h = handles.poll();
      return h != null ? h : new SqlHandle(url);
    }

    private void release(SqlHandle h) {
      if (h != null && !handles.offer(h)) {
        h.close();
      }
    }

    @Nullable
    private SqlHandle close(SqlHandle h) {
      if (h != null) {
        h.close();
      }
      return null;
    }
  }

  static class SqlHandle implements AutoCloseable {
    private final String url;
    Connection conn;
    PreparedStatement get;
    PreparedStatement put;
    PreparedStatement touch;
    PreparedStatement invalidate;

    SqlHandle(String url) throws SQLException {
      this.url = url;
      this.conn = org.h2.Driver.load().connect(url, null);
    }

    @Override
    public void close() {
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
