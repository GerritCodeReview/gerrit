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
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

enum ObjectKeyTypeImpl implements KeyType<Object> {
  INSTANCE;

  @Override
  public String columnType() {
    return "OTHER";
  }

  @Override
  public Object get(ResultSet rs, int col) throws SQLException {
    return rs.getObject(col);
  }

  @Override
  public void set(PreparedStatement ps, int col, Object key) throws SQLException {
    ps.setObject(col, key, Types.JAVA_OBJECT);
  }

  @Override
  public Funnel<Object> funnel() {
    return new Funnel<Object>() {
      private static final long serialVersionUID = 1L;

      @Override
      public void funnel(Object from, PrimitiveSink into) {
        try (ObjectOutputStream ser = new ObjectOutputStream(new SinkOutputStream(into))) {
          ser.writeObject(from);
          ser.flush();
        } catch (IOException err) {
          throw new RuntimeException("Cannot hash as Serializable", err);
        }
      }
    };
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
