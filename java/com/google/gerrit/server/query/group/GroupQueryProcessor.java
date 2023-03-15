// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.query.group;

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.query.group.GroupQueryBuilder.FIELD_LIMIT;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.query.AndSource;
import com.google.gerrit.index.query.IndexPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryProcessor;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountLimits;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.gerrit.server.index.group.GroupIndexRewriter;
import com.google.gerrit.server.index.group.GroupSchemaDefinitions;
import com.google.gerrit.server.notedb.Sequences;
import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * Query processor for the group index.
 *
 * <p>Instances are one-time-use. Other singleton classes should inject a Provider rather than
 * holding on to a single instance.
 */
public class GroupQueryProcessor extends QueryProcessor<InternalGroup> {
  private final Provider<CurrentUser> userProvider;
  private final GroupControl.GenericFactory groupControlFactory;
  private final Sequences sequences;
  private final IndexConfig indexConfig;

  static {
    // It is assumed that basic rewrites do not touch visibleto predicates.
    checkState(
        !GroupIsVisibleToPredicate.class.isAssignableFrom(IndexPredicate.class),
        "GroupQueryProcessor assumes visibleto is not used by the index rewriter.");
  }

  @Inject
  protected GroupQueryProcessor(
      Provider<CurrentUser> userProvider,
      AccountLimits.Factory limitsFactory,
      MetricMaker metricMaker,
      IndexConfig indexConfig,
      GroupIndexCollection indexes,
      GroupIndexRewriter rewriter,
      GroupControl.GenericFactory groupControlFactory,
      Sequences sequences) {
    super(
        metricMaker,
        GroupSchemaDefinitions.INSTANCE,
        indexConfig,
        indexes,
        rewriter,
        FIELD_LIMIT,
        () -> limitsFactory.create(userProvider.get()).getQueryLimit());
    this.userProvider = userProvider;
    this.groupControlFactory = groupControlFactory;
    this.sequences = sequences;
    this.indexConfig = indexConfig;
  }

  @Override
  protected Predicate<InternalGroup> enforceVisibility(Predicate<InternalGroup> pred) {
    return new AndSource<>(
        ImmutableList.of(
            pred, new GroupIsVisibleToPredicate(groupControlFactory, userProvider.get())),
        start,
        indexConfig);
  }

  @Override
  protected String formatForLogging(InternalGroup internalGroup) {
    return internalGroup.getGroupUUID().get();
  }

  @Override
  protected int getIndexSize() {
    return sequences.lastGroupId();
  }

  @Override
  protected int getBatchSize() {
    return sequences.groupBatchSize();
  }
}
