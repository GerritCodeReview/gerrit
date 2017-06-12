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

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.index.IndexConfig;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.QueryOptions;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.change.ChangeIndexRewriter;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
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
  private final ChangeControl.GenericFactory changeControlFactory;
  private final ChangeNotes.Factory notesFactory;

  static {
    // It is assumed that basic rewrites do not touch visibleto predicates.
    checkState(
        !ChangeIsVisibleToPredicate.class.isAssignableFrom(IndexPredicate.class),
        "ChangeQueryProcessor assumes visibleto is not used by the index rewriter.");
  }

  @Inject
  ChangeQueryProcessor(
      Provider<CurrentUser> userProvider,
      Metrics metrics,
      IndexConfig indexConfig,
      ChangeIndexCollection indexes,
      ChangeIndexRewriter rewriter,
      Provider<ReviewDb> db,
      ChangeControl.GenericFactory changeControlFactory,
      ChangeNotes.Factory notesFactory) {
    super(
        userProvider,
        metrics,
        ChangeSchemaDefinitions.INSTANCE,
        indexConfig,
        indexes,
        rewriter,
        FIELD_LIMIT);
    this.db = db;
    this.changeControlFactory = changeControlFactory;
    this.notesFactory = notesFactory;
  }

  @Override
  public ChangeQueryProcessor enforceVisibility(boolean enforce) {
    super.enforceVisibility(enforce);
    return this;
  }

  @Override
  protected QueryOptions createOptions(
      IndexConfig indexConfig, int start, int limit, Set<String> requestedFields) {
    return IndexedChangeQuery.createOptions(indexConfig, start, limit, requestedFields);
  }

  @Override
  protected Predicate<ChangeData> enforceVisibility(Predicate<ChangeData> pred) {
    return new AndChangeSource(
        pred,
        new ChangeIsVisibleToPredicate(db, notesFactory, changeControlFactory, userProvider.get()),
        start);
  }
}
