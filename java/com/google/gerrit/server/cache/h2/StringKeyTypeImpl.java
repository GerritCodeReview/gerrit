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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

enum StringKeyTypeImpl implements KeyType<String> {
  INSTANCE;

  @Override
  public String columnType() {
    return "VARCHAR(4096)";
  }

  @Override
  public String get(ResultSet rs, int col) throws SQLException {
    return rs.getString(col);
  }

  @Override
  public void set(PreparedStatement ps, int col, String value) throws SQLException {
    ps.setString(col, value);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Funnel<String> funnel() {
    Funnel<?> s = Funnels.unencodedCharsFunnel();
    return (Funnel<String>) s;
  }
}
