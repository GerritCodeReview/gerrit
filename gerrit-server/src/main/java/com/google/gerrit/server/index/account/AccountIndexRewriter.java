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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.index.IndexRewriter;
import com.google.gerrit.server.index.QueryOptions;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.account.AccountPredicates;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class AccountIndexRewriter implements IndexRewriter<AccountState> {

  private final AccountIndexCollection indexes;

  @Inject
  AccountIndexRewriter(AccountIndexCollection indexes) {
    this.indexes = indexes;
  }

  @Override
  public Predicate<AccountState> rewrite(Predicate<AccountState> in, QueryOptions opts)
      throws QueryParseException {
    if (!AccountPredicates.hasActive(in)) {
      in = Predicate.and(in, AccountPredicates.isActive());
    }
    AccountIndex index = indexes.getSearchIndex();
    checkNotNull(index, "no active search index configured for accounts");
    return new IndexedAccountQuery(index, in, opts);
  }
}
