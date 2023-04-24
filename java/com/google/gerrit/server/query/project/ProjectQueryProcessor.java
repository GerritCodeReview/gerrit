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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.project.ProjectData;
import com.google.gerrit.index.project.ProjectIndexCollection;
import com.google.gerrit.index.project.ProjectIndexRewriter;
import com.google.gerrit.index.project.ProjectSchemaDefinitions;
import com.google.gerrit.index.query.AndSource;
import com.google.gerrit.index.query.IndexPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryProcessor;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountLimits;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * Query processor for the project index.
 *
 * <p>Instances are one-time-use. Other singleton classes should inject a Provider rather than
 * holding on to a single instance.
 *
 * <p>By default, enforces visibility to CurrentUser.
 */
public class ProjectQueryProcessor extends QueryProcessor<ProjectData> {
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> userProvider;
  private final ProjectCache projectCache;
  private final IndexConfig indexConfig;

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
      MetricMaker metricMaker,
      IndexConfig indexConfig,
      ProjectIndexCollection indexes,
      ProjectIndexRewriter rewriter,
      PermissionBackend permissionBackend,
      ProjectCache projectCache) {
    super(
        metricMaker,
        ProjectSchemaDefinitions.INSTANCE,
        indexConfig,
        indexes,
        rewriter,
        FIELD_LIMIT,
        () -> limitsFactory.create(userProvider.get()).getQueryLimit());
    this.permissionBackend = permissionBackend;
    this.userProvider = userProvider;
    this.projectCache = projectCache;
    this.indexConfig = indexConfig;
  }

  @Override
  protected Predicate<ProjectData> enforceVisibility(Predicate<ProjectData> pred) {
    return new AndSource<>(
        ImmutableList.of(
            pred, new ProjectIsVisibleToPredicate(permissionBackend, userProvider.get())),
        start,
        indexConfig);
  }

  @Override
  protected String formatForLogging(ProjectData projectData) {
    return projectData.getProject().getName();
  }

  @Override
  protected int getIndexSize() {
    return projectCache.all().size();
  }

  @Override
  protected int getBatchSize() {
    return 1;
  }
}
