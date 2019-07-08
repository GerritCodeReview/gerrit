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

package com.google.gerrit.server.query.account;

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.query.account.AccountQueryBuilder.FIELD_LIMIT;

import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountControl;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.index.IndexConfig;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.account.AccountIndexCollection;
import com.google.gerrit.server.index.account.AccountIndexRewriter;
import com.google.gerrit.server.index.account.AccountSchemaDefinitions;
import com.google.gerrit.server.query.AndSource;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryProcessor;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class AccountQueryProcessor extends QueryProcessor<AccountState> {
  private final AccountControl.Factory accountControlFactory;

  static {
    // It is assumed that basic rewrites do not touch visibleto predicates.
    checkState(
        !AccountIsVisibleToPredicate.class.isAssignableFrom(IndexPredicate.class),
        "AccountQueryProcessor assumes visibleto is not used by the index rewriter.");
  }

  @Inject
  protected AccountQueryProcessor(
      Provider<CurrentUser> userProvider,
      Metrics metrics,
      IndexConfig indexConfig,
      AccountIndexCollection indexes,
      AccountIndexRewriter rewriter,
      AccountControl.Factory accountControlFactory) {
    super(
        userProvider,
        metrics,
        AccountSchemaDefinitions.INSTANCE,
        indexConfig,
        indexes,
        rewriter,
        FIELD_LIMIT);
    this.accountControlFactory = accountControlFactory;
  }

  @Override
  protected Predicate<AccountState> enforceVisibility(Predicate<AccountState> pred) {
    return new AndSource<>(
        pred, new AccountIsVisibleToPredicate(accountControlFactory.get()), start);
  }
}
