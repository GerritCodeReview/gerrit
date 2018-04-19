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
import com.google.common.hash.Funnels;
import com.google.common.hash.PrimitiveSink;
import com.google.gerrit.server.cache.h2.H2CacheImpl.SinkOutputStream;
import com.google.inject.TypeLiteral;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

class EntryTypeImpl<K> implements EntryType<K> {
  private static final EntryType<?> OTHER = new EntryTypeImpl<>();
  private static final EntryType<String> STRING =
      new EntryTypeImpl<String>() {
        @Override
        public String keyColumnType() {
          return "VARCHAR(4096)";
        }

        @Override
        public String getKey(ResultSet rs, int col) throws SQLException {
          return rs.getString(col);
        }

        @Override
        public void setKey(PreparedStatement ps, int col, String value) throws SQLException {
          ps.setString(col, value);
        }

        @SuppressWarnings("unchecked")
        @Override
        public Funnel<String> keyFunnel() {
          Funnel<?> s = Funnels.unencodedCharsFunnel();
          return (Funnel<String>) s;
        }
      };

  @SuppressWarnings("unchecked")
  static <K> EntryType<K> create(TypeLiteral<K> type) {
    if (type.getRawType() == String.class) {
      return (EntryType<K>) STRING;
    }
    return (EntryType<K>) OTHER;
  }

  @Override
  public String keyColumnType() {
    return "OTHER";
  }

  @Override
  @SuppressWarnings("unchecked")
  public K getKey(ResultSet rs, int col) throws SQLException {
    return (K) rs.getObject(col);
  }

  @Override
  public void setKey(PreparedStatement ps, int col, K key) throws SQLException {
    ps.setObject(col, key, Types.JAVA_OBJECT);
  }

  @Override
  public Funnel<K> keyFunnel() {
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
}
