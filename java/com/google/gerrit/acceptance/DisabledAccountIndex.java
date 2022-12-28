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

package com.google.gerrit.acceptance;

import com.google.gerrit.entities.Account;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.index.account.AccountIndex;

/** This class wraps an index and assumes the search index can't handle any queries. */
public class DisabledAccountIndex implements AccountIndex {
  private final AccountIndex index;

  public DisabledAccountIndex(AccountIndex index) {
    this.index = index;
  }

  public AccountIndex unwrap() {
    return index;
  }

  @Override
  public Schema<AccountState> getSchema() {
    return index.getSchema();
  }

  @Override
  public void close() {
    index.close();
  }

  @Override
  public void insert(AccountState obj) {
    throw new UnsupportedOperationException("AccountIndex is disabled");
  }

  @Override
  public void replace(AccountState obj) {
    throw new UnsupportedOperationException("AccountIndex is disabled");
  }

  @Override
  public void deleteByValue(AccountState value) {
    throw new UnsupportedOperationException("AccountIndex is disabled");
  }

  @Override
  public void delete(Account.Id key) {
    throw new UnsupportedOperationException("AccountIndex is disabled");
  }

  @Override
  public void deleteAll() {
    throw new UnsupportedOperationException("AccountIndex is disabled");
  }

  @Override
  public DataSource<AccountState> getSource(Predicate<AccountState> p, QueryOptions opts) {
    throw new UnsupportedOperationException("AccountIndex is disabled");
  }

  @Override
  public void markReady(boolean ready) {
    throw new UnsupportedOperationException("AccountIndex is disabled");
  }
}
