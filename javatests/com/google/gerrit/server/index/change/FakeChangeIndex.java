// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.index.change;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Change;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.index.query.ResultSet;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import org.junit.Ignore;

@Ignore
public class FakeChangeIndex implements ChangeIndex {
  static final Schema<ChangeData> V1 = new Schema<>(1, false, ImmutableList.of(ChangeField.STATUS));

  static final Schema<ChangeData> V2 =
      new Schema<>(
          2, false, ImmutableList.of(ChangeField.STATUS, ChangeField.PATH, ChangeField.UPDATED));

  private static class Source implements ChangeDataSource {
    private final Predicate<ChangeData> p;

    Source(Predicate<ChangeData> p) {
      this.p = p;
    }

    @Override
    public int getCardinality() {
      return 1;
    }

    @Override
    public boolean hasChange() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet<ChangeData> read() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet<FieldBundle> readRaw() {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String toString() {
      return p.toString();
    }
  }

  private final Schema<ChangeData> schema;

  FakeChangeIndex(Schema<ChangeData> schema) {
    this.schema = schema;
  }

  @Override
  public void replace(ChangeData cd) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void delete(Change.Id id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void deleteAll() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ChangeDataSource getSource(Predicate<ChangeData> p, QueryOptions opts)
      throws QueryParseException {
    return new FakeChangeIndex.Source(p);
  }

  @Override
  public Schema<ChangeData> getSchema() {
    return schema;
  }

  @Override
  public void close() {}

  @Override
  public void markReady(boolean ready) {
    throw new UnsupportedOperationException();
  }
}
