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

import com.google.common.cache.Cache;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.BloomFilter;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.CacheInfo;
import com.google.gerrit.server.cache.h2.H2CacheImpl.SqlHandle;
import com.google.gerrit.server.cache.h2.H2CacheImpl.ValueHolder;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.TypeLiteral;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;

class ReadWriteSqlStore<K, V> extends ReadOnlySqlStore<K, V> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final long maxSize;

  ReadWriteSqlStore(
      String jdbcUrl,
      TypeLiteral<K> keyType,
      CacheSerializer<K> keySerializer,
      CacheSerializer<V> valueSerializer,
      int version,
      long maxSize,
      @Nullable Duration expireAfterWrite,
      @Nullable Duration refreshAfterWrite,
      boolean buildBloomFilter,
      boolean isOfflineReindex) {
    super(
        jdbcUrl,
        keyType,
        keySerializer,
        valueSerializer,
        version,
        expireAfterWrite,
        refreshAfterWrite,
        buildBloomFilter,
        isOfflineReindex);
    this.maxSize = maxSize;
  }

  @Override
  void touch(SqlHandle c, K key) throws IOException, SQLException {
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

  @Override
  public void put(K key, ValueHolder<V> holder) {
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

  @Override
  public void invalidate(K key) {
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

  @Override
  public void invalidateAll() {
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

  @Override
  public void prune(Cache<K, ?> mem) {
    SqlHandle c = null;
    try {
      c = acquire();
      try (PreparedStatement ps = c.conn.prepareStatement("DELETE FROM data WHERE version!=?")) {
        ps.setInt(1, version);
        int oldEntries = ps.executeUpdate();
        if (oldEntries > 0) {
          logger.atInfo().log(
              "Pruned %d entries not matching version %d from cache %s", oldEntries, version, url);
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

        try (ResultSet r = s.executeQuery("SELECT k, space, created FROM data ORDER BY accessed")) {
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
    }
  }
}
