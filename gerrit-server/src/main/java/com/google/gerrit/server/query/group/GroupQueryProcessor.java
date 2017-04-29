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

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.index.IndexConfig;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.gerrit.server.index.group.GroupIndexRewriter;
import com.google.gerrit.server.index.group.GroupSchemaDefinitions;
import com.google.gerrit.server.query.AndSource;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryProcessor;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class GroupQueryProcessor extends QueryProcessor<AccountGroup> {
  private final GroupControl.GenericFactory groupControlFactory;

  static {
    // It is assumed that basic rewrites do not touch visibleto predicates.
    checkState(
        !GroupIsVisibleToPredicate.class.isAssignableFrom(IndexPredicate.class),
        "GroupQueryProcessor assumes visibleto is not used by the index rewriter.");
  }

  @Inject
  protected GroupQueryProcessor(
      Provider<CurrentUser> userProvider,
      CapabilityControl.Factory capabilityFactory,
      Metrics metrics,
      IndexConfig indexConfig,
      GroupIndexCollection indexes,
      GroupIndexRewriter rewriter,
      GroupControl.GenericFactory groupControlFactory) {
    super(
        userProvider,
        capabilityFactory,
        metrics,
        GroupSchemaDefinitions.INSTANCE,
        indexConfig,
        indexes,
        rewriter,
        FIELD_LIMIT);
    this.groupControlFactory = groupControlFactory;
  }

  @Override
  protected Predicate<AccountGroup> enforceVisibility(Predicate<AccountGroup> pred) {
    return new AndSource<>(
        pred, new GroupIsVisibleToPredicate(groupControlFactory, userProvider.get()), start);
  }
}
