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
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

class ObjectKeyTypeImpl<K> implements KeyType<K> {
  private final CacheSerializer<K> serializer;

  ObjectKeyTypeImpl(CacheSerializer<K> serializer) {
    this.serializer = serializer;
  }

  @Override
  public String columnType() {
    return "OTHER";
  }

  @Override
  public K get(ResultSet rs, int col) throws IOException, SQLException {
    return serializer.deserialize(rs.getBytes(col));
  }

  @Override
  public void set(PreparedStatement ps, int col, K key) throws IOException, SQLException {
    ps.setBytes(col, serializer.serialize(key));
  }

  @Override
  public Funnel<K> funnel() {
    return new Funnel<K>() {
      private static final long serialVersionUID = 1L;

      @Override
      public void funnel(K from, PrimitiveSink into) {
        Funnels.byteArrayFunnel().funnel(serializer.serialize(from), into);
      }
    };
  }
}
