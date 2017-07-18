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

package com.google.gerrit.server.query.project;

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.query.project.ProjectQueryBuilder.FIELD_LIMIT;

import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountLimits;
import com.google.gerrit.server.index.IndexConfig;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.project.ProjectIndexCollection;
import com.google.gerrit.server.index.project.ProjectIndexRewriter;
import com.google.gerrit.server.index.project.ProjectSchemaDefinitions;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.AndSource;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryProcessor;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class ProjectQueryProcessor extends QueryProcessor<ProjectState> {
  private final ProjectControl.GenericFactory projectControlFactory;

  static {
    // It is assumed that basic rewrites do not touch visibleto predicates.
    checkState(
        !ProjectIsVisibleToPredicate.class.isAssignableFrom(IndexPredicate.class),
        "ProjectQueryProcessor assumes visibleto is not used by the index rewriter.");
  }

  @Inject
  protected ProjectQueryProcessor(
      Provider<CurrentUser> userProvider,
      AccountLimits.Factory limitsFactory,
      Metrics metrics,
      IndexConfig indexConfig,
      ProjectIndexCollection indexes,
      ProjectIndexRewriter rewriter,
      ProjectControl.GenericFactory projectControlFactory) {
    super(
        userProvider,
        limitsFactory,
        metrics,
        ProjectSchemaDefinitions.INSTANCE,
        indexConfig,
        indexes,
        rewriter,
        FIELD_LIMIT);
    this.projectControlFactory = projectControlFactory;
  }

  @Override
  protected Predicate<ProjectState> enforceVisibility(Predicate<ProjectState> pred) {
    return new AndSource<>(
        pred, new ProjectIsVisibleToPredicate(projectControlFactory, userProvider.get()), start);
  }
}
