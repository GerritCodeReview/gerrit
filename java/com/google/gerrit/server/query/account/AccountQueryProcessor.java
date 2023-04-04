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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.query.AndSource;
import com.google.gerrit.index.query.IndexPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryProcessor;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountControl;
import com.google.gerrit.server.account.AccountLimits;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.index.account.AccountIndexCollection;
import com.google.gerrit.server.index.account.AccountIndexRewriter;
import com.google.gerrit.server.index.account.AccountSchemaDefinitions;
import com.google.gerrit.server.notedb.Sequences;
import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * Query processor for the account index.
 *
 * <p>Instances are one-time-use. Other singleton classes should inject a Provider rather than
 * holding on to a single instance.
 */
public class AccountQueryProcessor extends QueryProcessor<AccountState> {
  private final AccountControl.Factory accountControlFactory;
  private final Sequences sequences;
  private final IndexConfig indexConfig;

  static {
    // It is assumed that basic rewrites do not touch visibleto predicates.
    checkState(
        !AccountIsVisibleToPredicate.class.isAssignableFrom(IndexPredicate.class),
        "AccountQueryProcessor assumes visibleto is not used by the index rewriter.");
  }

  @Inject
  protected AccountQueryProcessor(
      Provider<CurrentUser> userProvider,
      AccountLimits.Factory limitsFactory,
      MetricMaker metricMaker,
      IndexConfig indexConfig,
      AccountIndexCollection indexes,
      AccountIndexRewriter rewriter,
      AccountControl.Factory accountControlFactory,
      Sequences sequences) {
    super(
        metricMaker,
        AccountSchemaDefinitions.INSTANCE,
        indexConfig,
        indexes,
        rewriter,
        FIELD_LIMIT,
        () -> limitsFactory.create(userProvider.get()).getQueryLimit());
    this.accountControlFactory = accountControlFactory;
    this.sequences = sequences;
    this.indexConfig = indexConfig;
  }

  @Override
  protected Predicate<AccountState> enforceVisibility(Predicate<AccountState> pred) {
    return new AndSource<>(
        ImmutableList.of(pred, new AccountIsVisibleToPredicate(accountControlFactory.get())),
        start,
        indexConfig);
  }

  @Override
  protected String formatForLogging(AccountState accountState) {
    return accountState.account().id().toString();
  }

  @Override
  protected int getIndexSize() {
    return sequences.lastAccountId();
  }

  @Override
  protected int getBatchSize() {
    return sequences.accountBatchSize();
  }
}
