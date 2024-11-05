// Copyright (C) 2024 The Android Open Source Project
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
import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.BloomFilter;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.cache.PersistentCache.DiskStats;
import com.google.gerrit.server.cache.h2.H2CacheImpl.SqlHandle;
import com.google.gerrit.server.cache.h2.H2CacheImpl.ValueHolder;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.TypeLiteral;
import java.io.IOException;
import java.io.InvalidClassException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

class ReadOnlySqlStore<K, V> implements SqlStore<K, V> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static final ImmutableSet<String> OLD_CLASS_NAMES =
      ImmutableSet.of("com.google.gerrit.server.change.ChangeKind");

  final String url;
  final KeyType<K> keyType;
  final CacheSerializer<V> valueSerializer;
  final int version;
  @Nullable private final Duration expireAfterWrite;
  @Nullable private final Duration refreshAfterWrite;
  private final BlockingQueue<SqlHandle> handles;
  private final AtomicLong hitCount = new AtomicLong();
  private final AtomicLong missCount = new AtomicLong();
  volatile BloomFilter<K> bloomFilter;
  private int estimatedSize;
  private boolean buildBloomFilter;
  private boolean isOfflineReindex;

  ReadOnlySqlStore(
      String jdbcUrl,
      TypeLiteral<K> keyType,
      CacheSerializer<K> keySerializer,
      CacheSerializer<V> valueSerializer,
      int version,
      @Nullable Duration expireAfterWrite,
      @Nullable Duration refreshAfterWrite,
      boolean buildBloomFilter,
      boolean isOfflineReindex) {
    this.url = jdbcUrl;
    this.keyType = createKeyType(keyType, keySerializer);
    this.valueSerializer = valueSerializer;
    this.version = version;
    this.expireAfterWrite = expireAfterWrite;
    this.refreshAfterWrite = refreshAfterWrite;
    this.buildBloomFilter = buildBloomFilter;
    this.isOfflineReindex = isOfflineReindex;

    int cores = Runtime.getRuntime().availableProcessors();
    int keep = Math.min(cores, 16);
    this.handles = new ArrayBlockingQueue<>(keep);
  }

  @SuppressWarnings("unchecked")
  private static <T> KeyType<T> createKeyType(TypeLiteral<T> type, CacheSerializer<T> serializer) {
    if (type.getRawType() == String.class) {
      return (KeyType<T>) StringKeyTypeImpl.INSTANCE;
    }
    return new ObjectKeyTypeImpl<>(serializer);
  }

  @Override
  public synchronized void open() {
    if (buildBloomFilter && bloomFilter == null) {
      bloomFilter = buildBloomFilter();
    }
  }

  @Override
  public void close() {
    SqlHandle h;
    while ((h = handles.poll()) != null) {
      h.close();
    }
  }

  @Override
  public String getUrl() {
    return url;
  }

  @Override
  public boolean mightContain(K key) {
    BloomFilter<K> b = bloomFilter;
    if (buildBloomFilter && b == null) {
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

  @Nullable
  private BloomFilter<K> buildBloomFilter() {
    SqlHandle c = null;
    try (TraceTimer ignored = TraceContext.newTimer("Build bloom filter", Metadata.empty())) {
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

  @Nullable
  @Override
  public ValueHolder<V> getIfPresent(K key) {
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
        if (!isOfflineReindex) {
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

  boolean expired(Instant created) {
    if (expireAfterWrite == null) {
      return false;
    }
    Duration age = Duration.between(created, TimeUtil.now());
    return age.compareTo(expireAfterWrite) > 0;
  }

  @Override
  public boolean needsRefresh(Instant created) {
    if (refreshAfterWrite == null) {
      return false;
    }
    Duration age = Duration.between(created, TimeUtil.now());
    return age.compareTo(refreshAfterWrite) > 0;
  }

  @SuppressWarnings("unused")
  void touch(SqlHandle c, K key) throws IOException, SQLException {}

  @Override
  public void put(K key, ValueHolder<V> holder) {}

  @Override
  public void invalidate(K key) {}

  @Override
  public void invalidateAll() {}

  @Override
  public void prune(Cache<K, ?> mem) {}

  @Override
  public DiskStats diskStats() {
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

  SqlHandle acquire() throws SQLException {
    SqlHandle h = handles.poll();
    return h != null ? h : new SqlHandle(url, keyType);
  }

  void release(SqlHandle h) {
    if (h != null && !handles.offer(h)) {
      h.close();
    }
  }

  @Nullable
  SqlHandle close(SqlHandle h) {
    if (h != null) {
      h.close();
    }
    return null;
  }

  BloomFilter<K> newBloomFilter() {
    int cnt = Math.max(64 * 1024, 2 * estimatedSize);
    return BloomFilter.create(keyType.funnel(), cnt);
  }
}
