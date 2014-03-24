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

package com.google.gerrit.server.index.account;

import com.google.common.collect.Lists;
import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.server.index.Index;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.account.AccountDataSource;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Secondary index implementation for accounts.
 * <p>
 * {@link AccountInfo} objects are inserted into the index and are queried by
 * converting special {@link com.google.gerrit.server.query.Predicate} instances
 * into index-aware predicates that use the index search results as a source.
 * <p>
 * Implementations must be thread-safe and should batch inserts/updates where
 * appropriate.
 */
public class AccountIndex implements Index<AccountInfo> {

  private final Schema<AccountInfo> schema;

  static interface Factory {
    AccountIndex create(Schema<AccountInfo> schema);
  }

  AccountIndex(@Assisted Schema<AccountInfo> schema) {
    this.schema = schema;
  }

  @Override
  public Schema<AccountInfo> getSchema() {
    return schema;
  }

  @Override
  public void close() {

  }

  @Override
  public void insert(AccountInfo account) throws IOException {

  }

  @Override
  public void replace(AccountInfo account) throws IOException {

  }

  @Override
  public void delete(AccountInfo account) throws IOException {

  }

  @Override
  public void deleteAll() throws IOException {

  }

  @Override
  public AccountDataSource getSource(Predicate<AccountInfo> p, int start,
      int limit) throws QueryParseException {
    // TODO(dpursehouse)
    return new QuerySource();
  }

  @Override
  public void markReady(boolean ready) throws IOException {

  }

  private class QuerySource implements AccountDataSource {

    private QuerySource() {
    }

    @Override
    public int getCardinality() {
      return 10; // TODO(dpursehouse)
    }

    @Override
    public ResultSet<AccountInfo> read() throws OrmException {
      // TODO(dpursehouse)
      List<AccountInfo> result =
          Lists.newArrayListWithCapacity(1);
      final List<AccountInfo> r = Collections.unmodifiableList(result);
      return new ResultSet<AccountInfo>() {
        @Override
        public Iterator<AccountInfo> iterator() {
          return r.iterator();
        }

        @Override
        public List<AccountInfo> toList() {
          return r;
        }

        @Override
        public void close() {
          // Do nothing.
        }
      };
    }
  }

}
