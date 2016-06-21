// Copyright (C) 2010 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.query.change.ChangeQueryBuilder.FIELD_LIMIT;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.index.IndexConfig;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.QueryOptions;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.change.ChangeIndexRewriter;
import com.google.gerrit.server.index.change.IndexedChangeQuery;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryProcessor;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Set;

public class ChangeQueryProcessor extends QueryProcessor<ChangeData> {
  private final Provider<ReviewDb> db;
  private final Provider<CurrentUser> userProvider;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final ChangeNotes.Factory notesFactory;

  private boolean enforceVisibility = true;

  static {
    // It is assumed that basic rewrites do not touch visibleto predicates.
    checkState(
        !IsVisibleToPredicate.class.isAssignableFrom(IndexPredicate.class),
        "ChangeQueryProcessor assumes visibleto is not used by the index rewriter.");
  }

  @Inject
  ChangeQueryProcessor(Metrics.Factory metricsFactory,
      IndexConfig indexConfig,
      ChangeIndexCollection indexes,
      ChangeIndexRewriter rewriter,
      Provider<ReviewDb> db,
      Provider<CurrentUser> userProvider,
      ChangeControl.GenericFactory changeControlFactory,
      ChangeNotes.Factory notesFactory) {
    super(metricsFactory, "change", indexConfig, indexes, rewriter,
        FIELD_LIMIT);
    this.db = db;
    this.userProvider = userProvider;
    this.changeControlFactory = changeControlFactory;
    this.notesFactory = notesFactory;
  }

  public ChangeQueryProcessor enforceVisibility(boolean enforce) {
    enforceVisibility = enforce;
    return this;
  }

  @Override
  protected QueryOptions createOptions(IndexConfig indexConfig, int start,
      int limit, Set<String> requestedFields) {
    return IndexedChangeQuery.createOptions(indexConfig, start, limit + 1,
        requestedFields);
  }

  @Override
  protected Predicate<ChangeData> postRewrite(Predicate<ChangeData> pred) {
    if (enforceVisibility) {
      return new AndSource(ImmutableList.of(pred, new IsVisibleToPredicate(db,
          notesFactory, changeControlFactory, userProvider.get())), start);
    }

    return super.postRewrite(pred);
  }

  @Override
  protected int getPermittedLimit() {
    if (enforceVisibility) {
      return userProvider.get().getCapabilities()
        .getRange(GlobalCapability.QUERY_LIMIT)
        .getMax();
    }
    return Integer.MAX_VALUE;
  }
}
