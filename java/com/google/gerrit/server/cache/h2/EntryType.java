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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.hash.Funnel;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.server.cache.ChangeKindKeyProtoConverter;
import com.google.gerrit.server.cache.ChangeKindProtoConverter;
import com.google.gerrit.server.change.ChangeKindCacheImpl;
import com.google.inject.TypeLiteral;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

interface EntryType<K, V> {
  @SuppressWarnings("unchecked")
  static <K, V> EntryType<K, V> create(TypeLiteral<K> keyType, TypeLiteral<V> valueType) {
    if (keyType.getRawType() == ChangeKindCacheImpl.Key.class) {
      checkArgument(valueType.getRawType() == ChangeKind.class);
      return (EntryType<K, V>)
          new ProtoEntryTypeImpl<>(
              new ChangeKindKeyProtoConverter(), new ChangeKindProtoConverter());
    }
    if (keyType.getRawType() == String.class) {
      return (EntryType<K, V>) StringKeyTypeImpl.INSTANCE;
    }
    return (EntryType<K, V>) ObjectKeyTypeImpl.INSTANCE;
  }

  String keyColumnType();

  K getKey(ResultSet rs, int col) throws SQLException;

  void setKey(PreparedStatement ps, int col, K key) throws SQLException;

  Funnel<K> keyFunnel();

  V getValue(ResultSet rs, int col) throws SQLException;

  void setValue(PreparedStatement ps, int col, V value) throws SQLException;
}
