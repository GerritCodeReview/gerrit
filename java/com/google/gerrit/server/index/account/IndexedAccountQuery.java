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

package com.google.gerrit.server.index.account;

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.IndexedQuery;
import com.google.gerrit.index.query.Matchable;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountState;

public class IndexedAccountQuery extends IndexedQuery<Account.Id, AccountState>
    implements DataSource<AccountState>, Matchable<AccountState> {

  public IndexedAccountQuery(
      Index<Account.Id, AccountState> index, Predicate<AccountState> pred, QueryOptions opts)
      throws QueryParseException {
    super(index, pred, opts.convertForBackend());
  }

  @Override
  public boolean match(AccountState accountState) throws StorageException {
    Predicate<AccountState> pred = getChild(0);
    checkState(
        pred.isMatchable(),
        "match invoked, but child predicate %s doesn't implement %s",
        pred,
        Matchable.class.getName());
    return pred.asMatchable().match(accountState);
  }

  @Override
  public int getCost() {
    return 1;
  }
}
