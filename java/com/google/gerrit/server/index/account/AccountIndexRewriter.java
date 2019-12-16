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

import static java.util.Objects.requireNonNull;

import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.IndexRewriter;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.query.IndexPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.index.query.TooManyTermsInQueryException;
import com.google.gerrit.server.account.AccountState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.util.MutableInteger;

/** Rewriter for the account index. See {@link IndexRewriter} for details. */
@Singleton
public class AccountIndexRewriter implements IndexRewriter<AccountState> {
  private final AccountIndexCollection indexes;
  private final IndexConfig config;

  @Inject
  AccountIndexRewriter(AccountIndexCollection indexes, IndexConfig config) {
    this.indexes = indexes;
    this.config = config;
  }

  @Override
  public Predicate<AccountState> rewrite(Predicate<AccountState> in, QueryOptions opts)
      throws QueryParseException {
    AccountIndex index = indexes.getSearchIndex();
    requireNonNull(index, "no active search index configured for accounts");
    validateMaxTermsInQuery(in);
    return new IndexedAccountQuery(index, in, opts);
  }

  /**
   * Validates whether a query has too many terms.
   *
   * @param predicate the predicate for which the leaf predicates should be counted
   * @throws QueryParseException thrown if the query has too many terms
   */
  public void validateMaxTermsInQuery(Predicate<AccountState> predicate)
      throws QueryParseException {
    MutableInteger leafTerms = new MutableInteger();
    validateMaxTermsInQuery(predicate, leafTerms);
  }

  private void validateMaxTermsInQuery(Predicate<AccountState> predicate, MutableInteger leafTerms)
      throws TooManyTermsInQueryException {
    if (!(predicate instanceof IndexPredicate)) {
      if (++leafTerms.value > config.maxTerms()) {
        throw new TooManyTermsInQueryException();
      }
    }

    for (Predicate<AccountState> childPredicate : predicate.getChildren()) {
      validateMaxTermsInQuery(childPredicate, leafTerms);
    }
  }
}
