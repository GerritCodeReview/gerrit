// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.schema.backup;

import com.google.gwtorm.client.Key;
import com.google.gwtorm.client.ResultSet;
import com.google.gwtorm.nosql.IndexFunction;
import com.google.gwtorm.nosql.NoSqlAccess;
import com.google.gwtorm.protobuf.ProtobufCodec;

public abstract class BackupAccess<T, K extends Key<?>> extends
    NoSqlAccess<T, K> {
  @SuppressWarnings("unchecked")
  protected BackupAccess(BackupSchema s) {
    super(s);
  }

  @Override
  public abstract ProtobufCodec<T> getObjectCodec();

  @SuppressWarnings("unchecked")
  @Override
  protected ResultSet scanIndex(IndexFunction index, byte[] fromKey,
      byte[] toKey, int limit) {
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("unchecked")
  @Override
  protected ResultSet scanPrimaryKey(byte[] fromKey, byte[] toKey, int limit) {
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("unchecked")
  @Override
  public void delete(Iterable instances) {
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("unchecked")
  @Override
  public T get(K key) {
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("unchecked")
  @Override
  public void insert(Iterable instances) {
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("unchecked")
  @Override
  public ResultSet iterateAllEntities() {
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("unchecked")
  @Override
  public void update(Iterable instances) {
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("unchecked")
  @Override
  public void upsert(Iterable instances) {
    throw new UnsupportedOperationException();
  }
}
