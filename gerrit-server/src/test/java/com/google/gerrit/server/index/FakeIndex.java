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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.server.index;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

class FakeIndex implements ChangeIndex {
  static Schema<ChangeData> V1 = new Schema<ChangeData>(1, false,
    ImmutableList.<FieldDef<ChangeData, ?>> of(
      ChangeField.STATUS));

  static Schema<ChangeData> V2 = new Schema<ChangeData>(2, false,
    ImmutableList.of(
      ChangeField.STATUS,
      ChangeField.FILE,
      ChangeField.SORTKEY));

  private static class Source implements ChangeDataSource {
    private final Predicate<ChangeData> p;

    Source(Predicate<ChangeData> p) {
      this.p = p;
    }

    @Override
    public int getCardinality() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasChange() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet<ChangeData> read() throws OrmException {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return p.toString();
    }
  }

  private final Schema<ChangeData> schema;

  FakeIndex(Schema<ChangeData> schema) {
    this.schema = schema;
  }

  @Override
  public void insert(ChangeData cd) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void replace(ChangeData cd) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void delete(ChangeData cd) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void deleteAll() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ChangeDataSource getSource(Predicate<ChangeData> p, int limit)
      throws QueryParseException {
    return new FakeIndex.Source(p);
  }

  @Override
  public Schema<ChangeData> getSchema() {
    return schema;
  }

  @Override
  public void close() {
  }

  @Override
  public void markReady(boolean ready) {
    throw new UnsupportedOperationException();
  }
}
