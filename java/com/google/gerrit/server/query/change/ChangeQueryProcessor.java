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
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.common.PluginDefinedInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.Extension;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.query.IndexPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryProcessor;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.DynamicOptions.DynamicBean;
import com.google.gerrit.server.account.AccountLimits;
import com.google.gerrit.server.change.ChangePluginDefinedInfoFactory;
import com.google.gerrit.server.change.PluginDefinedAttributesFactories;
import com.google.gerrit.server.change.PluginDefinedInfosFactory;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.change.ChangeIndexRewriter;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import com.google.gerrit.server.index.change.IndexedChangeQuery;
import com.google.gerrit.server.notedb.Sequences;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Query processor for the change index.
 *
 * <p>Instances are one-time-use. Other singleton classes should inject a Provider rather than
 * holding on to a single instance.
 */
public class ChangeQueryProcessor extends QueryProcessor<ChangeData>
    implements DynamicOptions.BeanReceiver, DynamicOptions.BeanProvider, PluginDefinedInfosFactory {
  private final Provider<CurrentUser> userProvider;
  private final ChangeIsVisibleToPredicate.Factory changeIsVisibleToPredicateFactory;
  private final Map<String, DynamicBean> dynamicBeans = new HashMap<>();
  private final List<Extension<ChangePluginDefinedInfoFactory>>
      changePluginDefinedInfoFactoriesByPlugin = new ArrayList<>();
  private final Sequences sequences;
  private final IndexConfig indexConfig;

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
      Sequences sequences,
      ChangeIsVisibleToPredicate.Factory changeIsVisibleToPredicateFactory,
      DynamicSet<ChangePluginDefinedInfoFactory> changePluginDefinedInfoFactories) {
    super(
        metricMaker,
        ChangeSchemaDefinitions.INSTANCE,
        indexConfig,
        indexes,
        rewriter,
        FIELD_LIMIT,
        () -> limitsFactory.create(userProvider.get()).getQueryLimit());
    this.userProvider = userProvider;
    this.changeIsVisibleToPredicateFactory = changeIsVisibleToPredicateFactory;
    this.sequences = sequences;
    this.indexConfig = indexConfig;

    changePluginDefinedInfoFactories
        .entries()
        .forEach(e -> changePluginDefinedInfoFactoriesByPlugin.add(e));
  }

  @Override
  public ChangeQueryProcessor enforceVisibility(boolean enforce) {
    super.enforceVisibility(enforce);
    return this;
  }

  @Override
  protected QueryOptions createOptions(
      IndexConfig indexConfig,
      int start,
      int pageSize,
      int pageSizeMultiplier,
      int limit,
      Set<String> requestedFields) {
    return IndexedChangeQuery.createOptions(
        indexConfig, start, pageSize, pageSizeMultiplier, limit, requestedFields);
  }

  @Override
  public void setDynamicBean(String plugin, DynamicBean dynamicBean) {
    dynamicBeans.put(plugin, dynamicBean);
  }

  @Override
  public DynamicBean getDynamicBean(String plugin) {
    return dynamicBeans.get(plugin);
  }

  public PluginDefinedInfosFactory getInfosFactory() {
    return this::createPluginDefinedInfos;
  }

  @Override
  public ImmutableListMultimap<Change.Id, PluginDefinedInfo> createPluginDefinedInfos(
      Collection<ChangeData> cds) {
    return PluginDefinedAttributesFactories.createAll(
        cds, this, changePluginDefinedInfoFactoriesByPlugin.stream());
  }

  @Override
  protected Predicate<ChangeData> enforceVisibility(Predicate<ChangeData> pred) {
    return new AndChangeSource(
        ImmutableList.of(pred, changeIsVisibleToPredicateFactory.forUser(userProvider.get())),
        start,
        indexConfig);
  }

  @Override
  protected String formatForLogging(ChangeData changeData) {
    return changeData.getId().toString();
  }

  @Override
  protected int getIndexSize() {
    return sequences.lastChangeId();
  }

  @Override
  protected int getBatchSize() {
    return sequences.changeBatchSize();
  }

  @Override
  protected int getInitialPageSize(int limit) {
    return Math.min(getUserQueryLimit().getAsInt(), limit);
  }
}
