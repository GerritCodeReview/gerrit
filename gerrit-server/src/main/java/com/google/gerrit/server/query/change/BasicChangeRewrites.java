// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.ChangeStatus;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.query.IntPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryRewriter;
import com.google.inject.Inject;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.name.Named;

public class BasicChangeRewrites extends QueryRewriter<ChangeData> {
  private static final ChangeQueryBuilder BUILDER = new ChangeQueryBuilder(
      new ChangeQueryBuilder.Arguments( //
          new InvalidProvider<ReviewDb>(), //
          new InvalidProvider<ChangeQueryRewriter>(), //
          null, null, null, null, null, null, null, //
          null, null, null, null, null, null, null, null, null), null);

  private static final QueryRewriter.Definition<ChangeData, BasicChangeRewrites> mydef =
      new QueryRewriter.Definition<ChangeData, BasicChangeRewrites>(
          BasicChangeRewrites.class, BUILDER);

  static Schema<ChangeData> schema(@Nullable IndexCollection indexes) {
    ChangeIndex index = indexes != null ? indexes.getSearchIndex() : null;
    return index != null ? index.getSchema() : null;
  }

  protected final Provider<ReviewDb> dbProvider;
  private final IndexCollection indexes;

  @Inject
  public BasicChangeRewrites(Provider<ReviewDb> dbProvider, IndexCollection indexes) {
    super(mydef);
    this.dbProvider = dbProvider;
    this.indexes = indexes;
  }

  @Rewrite("-status:open")
  @NoCostComputation
  public Predicate<ChangeData> r00_notOpen() {
    return ChangeStatusPredicate.closed(dbProvider);
  }

  @Rewrite("-status:closed")
  @NoCostComputation
  public Predicate<ChangeData> r00_notClosed() {
    return ChangeStatusPredicate.open(dbProvider);
  }

  @SuppressWarnings("unchecked")
  @NoCostComputation
  @Rewrite("-status:merged")
  public Predicate<ChangeData> r00_notMerged() {
    return or(ChangeStatusPredicate.open(dbProvider),
        new ChangeStatusPredicate(ChangeStatus.ABANDONED));
  }

  @SuppressWarnings("unchecked")
  @NoCostComputation
  @Rewrite("-status:abandoned")
  public Predicate<ChangeData> r00_notAbandoned() {
    return or(ChangeStatusPredicate.open(dbProvider),
        new ChangeStatusPredicate(ChangeStatus.MERGED));
  }

  @SuppressWarnings("unchecked")
  @NoCostComputation
  @Rewrite("sortkey_before:z A=(age:*)")
  public Predicate<ChangeData> r00_ageToSortKey(@Named("A") AgePredicate a) {
    String cut = ChangeUtil.sortKey(a.getCut(), Integer.MAX_VALUE);
    return and(new SortKeyPredicate.Before(schema(indexes), dbProvider, cut), a);
  }

  @NoCostComputation
  @Rewrite("A=(limit:*) B=(limit:*)")
  public Predicate<ChangeData> r00_smallestLimit(
      @Named("A") IntPredicate<ChangeData> a,
      @Named("B") IntPredicate<ChangeData> b) {
    return a.intValue() <= b.intValue() ? a : b;
  }

  @NoCostComputation
  @Rewrite("A=(sortkey_before:*) B=(sortkey_before:*)")
  public Predicate<ChangeData> r00_oldestSortKey(
      @Named("A") SortKeyPredicate.Before a,
      @Named("B") SortKeyPredicate.Before b) {
    return a.getValue().compareTo(b.getValue()) <= 0 ? a : b;
  }

  @NoCostComputation
  @Rewrite("A=(sortkey_after:*) B=(sortkey_after:*)")
  public Predicate<ChangeData> r00_newestSortKey(
      @Named("A") SortKeyPredicate.After a, @Named("B") SortKeyPredicate.After b) {
    return a.getValue().compareTo(b.getValue()) >= 0 ? a : b;
  }

  private static final class InvalidProvider<T> implements Provider<T> {
    @Override
    public T get() {
      throw new OutOfScopeException("Not available at init");
    }
  }
}
