// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gerrit.server.index.QueryOptions;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.query.DataSource;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import java.io.IOException;

public class ReadOnlyChangeIndex implements ChangeIndex {
  private final ChangeIndex index;

  public ReadOnlyChangeIndex(ChangeIndex index) {
    this.index = index;
  }

  public ChangeIndex unwrap() {
    return index;
  }

  @Override
  public Schema<ChangeData> getSchema() {
    return index.getSchema();
  }

  @Override
  public void close() {
    index.close();
  }

  @Override
  public void replace(ChangeData obj) throws IOException {
    // do nothing
  }

  @Override
  public void delete(Id key) throws IOException {
    // do nothing
  }

  @Override
  public void deleteAll() throws IOException {
    // do nothing
  }

  @Override
  public DataSource<ChangeData> getSource(Predicate<ChangeData> p, QueryOptions opts)
      throws QueryParseException {
    return index.getSource(p, opts);
  }

  @Override
  public void markReady(boolean ready) throws IOException {
    // do nothing
  }
}
