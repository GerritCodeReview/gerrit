// Copyright (C) 2018 The Android Open Source Project
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

import com.google.common.hash.Funnel;
import com.google.common.hash.PrimitiveSink;
import com.google.gerrit.server.cache.ProtoConverter;
import com.google.gerrit.server.cache.h2.H2CacheImpl.SinkOutputStream;
import com.google.protobuf.GeneratedMessageV3;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

class ProtoEntryTypeImpl<K, KP extends GeneratedMessageV3, V, VP extends GeneratedMessageV3>
    implements EntryType<K, V> {
  private final ProtoConverter<K> keyConverter;
  private final ProtoConverter<V> valueConverter;

  ProtoEntryTypeImpl(ProtoConverter<K> keyConverter, ProtoConverter<V> valueConverter) {
    this.keyConverter = keyConverter;
    this.valueConverter = valueConverter;
  }

  @Override
  public String keyColumnType() {
    // For backwards compatibility with existing cache tables that used Java object serialization.
    // The underlying type in the H2 database is a byte array, and getBytes/setBytes just work with
    // this column type.
    return "OTHER";
  }

  @Override
  public K getKey(ResultSet rs, int col) throws SQLException {
    try {
      return keyConverter.fromProtoBytes(rs.getBytes(col));
    } catch (Exception e) {
      // TODO(dborowitz): Include class name?
      throw new SQLException("failed to parse proto for key type");
    }
  }

  @Override
  public void setKey(PreparedStatement ps, int col, K key) throws SQLException {
    ps.setBytes(col, keyConverter.toProtoBytes(key));
  }

  @Override
  public Funnel<K> keyFunnel() {
    return new Funnel<K>() {
      private static final long serialVersionUID = 1L;

      @Override
      public void funnel(K from, PrimitiveSink into) {
        try (OutputStream out = new SinkOutputStream(into)) {
          keyConverter.toProtoBytes(from);
        } catch (IOException err) {
          throw new RuntimeException("Cannot hash as proto", err);
        }
      }
    };
  }

  @Override
  public V getValue(ResultSet rs, int col) throws SQLException {
    try {
      return valueConverter.fromProtoBytes(rs.getBytes(col));
    } catch (Exception e) {
      throw new SQLException("failed to parser proto for value type");
    }
  }

  @Override
  public void setValue(PreparedStatement ps, int col, V value) throws SQLException {
    ps.setBytes(col, valueConverter.toProtoBytes(value));
  }
}
