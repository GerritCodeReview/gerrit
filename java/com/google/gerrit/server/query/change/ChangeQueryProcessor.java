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
import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.extensions.common.PluginDefinedInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.Extension;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.query.IndexPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryProcessor;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.DynamicOptions.DynamicBean;
import com.google.gerrit.server.account.AccountLimits;
import com.google.gerrit.server.change.ChangeAttributeFactory;
import com.google.gerrit.server.change.PluginDefinedAttributesFactories;
import com.google.gerrit.server.change.PluginDefinedAttributesFactory;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.change.ChangeIndexRewriter;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import com.google.gerrit.server.index.change.IndexedChangeQuery;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Query processor for the change index.
 *
 * <p>Instances are one-time-use. Other singleton classes should inject a Provider rather than
 * holding on to a single instance.
 */
public class ChangeQueryProcessor extends QueryProcessor<ChangeData>
    implements DynamicOptions.BeanReceiver, DynamicOptions.BeanProvider {
  private final Provider<CurrentUser> userProvider;
  private final ChangeNotes.Factory notesFactory;
  private final ImmutableListMultimap<String, ChangeAttributeFactory> attributeFactoriesByPlugin;
  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;
  private final Provider<AnonymousUser> anonymousUserProvider;
  private final Map<String, DynamicBean> dynamicBeans = new HashMap<>();

  static {
    // It is assumed that basic rewrites do not touch visibleto predicates.
    checkState(
        !ChangeIsVisibleToPredicate.class.isAssignableFrom(IndexPredicate.class),
        "ChangeQueryProcessor assumes visibleto is not used by the index rewriter.");
  }

  @Inject
  ChangeQueryProcessor(
      Provider<CurrentUser> userProvider,
      AccountLimits.Factory limitsFactory,
      MetricMaker metricMaker,
      IndexConfig indexConfig,
      ChangeIndexCollection indexes,
      ChangeIndexRewriter rewriter,
      ChangeNotes.Factory notesFactory,
      DynamicSet<ChangeAttributeFactory> attributeFactories,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      Provider<AnonymousUser> anonymousUserProvider) {
    super(
        metricMaker,
        ChangeSchemaDefinitions.INSTANCE,
        indexConfig,
        indexes,
        rewriter,
        FIELD_LIMIT,
        () -> limitsFactory.create(userProvider.get()).getQueryLimit());
    this.userProvider = userProvider;
    this.notesFactory = notesFactory;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.anonymousUserProvider = anonymousUserProvider;

    ImmutableListMultimap.Builder<String, ChangeAttributeFactory> factoriesBuilder =
        ImmutableListMultimap.builder();
    // Eagerly call Extension#get() rather than storing Extensions, since that method invokes the
    // Provider on every call, which could be expensive if we invoke it once for every change.
    attributeFactories.entries().forEach(e -> factoriesBuilder.put(e.getPluginName(), e.get()));
    attributeFactoriesByPlugin = factoriesBuilder.build();
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
  public void setDynamicBean(String plugin, DynamicBean dynamicBean) {
    dynamicBeans.put(plugin, dynamicBean);
  }

  @Override
  public DynamicBean getDynamicBean(String plugin) {
    return dynamicBeans.get(plugin);
  }

  public PluginDefinedAttributesFactory getAttributesFactory() {
    return this::buildPluginInfo;
  }

  private ImmutableList<PluginDefinedInfo> buildPluginInfo(ChangeData cd) {
    return PluginDefinedAttributesFactories.createAll(
        cd,
        this,
        attributeFactoriesByPlugin
            .entries()
            .stream()
            .map(e -> new Extension<>(e.getKey(), e::getValue)));
  }

  @Override
  protected Predicate<ChangeData> enforceVisibility(Predicate<ChangeData> pred) {
    return new AndChangeSource(
        pred,
        new ChangeIsVisibleToPredicate(
            notesFactory,
            userProvider.get(),
            permissionBackend,
            projectCache,
            anonymousUserProvider),
        start);
  }

  @Override
  protected String formatForLogging(ChangeData changeData) {
    return changeData.getId().toString();
  }
}
