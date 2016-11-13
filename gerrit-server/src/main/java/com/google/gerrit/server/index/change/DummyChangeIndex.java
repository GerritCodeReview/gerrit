// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.index.QueryOptions;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import java.io.IOException;

public class DummyChangeIndex implements ChangeIndex {
  @Override
  public Schema<ChangeData> getSchema() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void close() {}

  @Override
  public void replace(ChangeData cd) throws IOException {}

  @Override
  public void delete(Change.Id id) throws IOException {}

  @Override
  public void deleteAll() throws IOException {}

  @Override
  public ChangeDataSource getSource(Predicate<ChangeData> p, QueryOptions opts) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markReady(boolean ready) throws IOException {}

  public int getMaxLimit() {
    return Integer.MAX_VALUE;
  }
}
