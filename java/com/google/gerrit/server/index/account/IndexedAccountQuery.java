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

import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.IndexedQuery;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountState;
import java.util.HashSet;
import java.util.Set;

public class IndexedAccountQuery extends IndexedQuery<Account.Id, AccountState>
    implements DataSource<AccountState> {

  public static QueryOptions createOptions(
      IndexConfig config, int start, int limit, Set<String> fields) {
    // Always include Account.Id since it is needed to load the account from NoteDb.
    if (!fields.contains(AccountField.ID.getName())) {
      fields = new HashSet<>(fields);
      fields.add(AccountField.ID.getName());
    }
    return QueryOptions.create(config, start, limit, fields);
  }

  public IndexedAccountQuery(
      Index<Account.Id, AccountState> index, Predicate<AccountState> pred, QueryOptions opts)
      throws QueryParseException {
    super(index, pred, opts.convertForBackend());
  }
}
