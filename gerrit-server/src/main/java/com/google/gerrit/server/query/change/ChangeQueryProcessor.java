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

import com.google.gerrit.extensions.common.PluginDefinedInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.CapabilityControl;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ChangeQueryProcessor extends QueryProcessor<ChangeData>
    implements PluginDefinedAttributesFactory {
  /**
   * Register a ChangeAttributeFactory in a config Module like this:
   *
   * <p>bind(ChangeAttributeFactory.class) .annotatedWith(Exports.named("export-name"))
   * .to(YourClass.class);
   */
  public interface ChangeAttributeFactory {
    PluginDefinedInfo create(ChangeData a, ChangeQueryProcessor qp, String plugin);
  }

  private final Provider<ReviewDb> db;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final ChangeNotes.Factory notesFactory;
  private final DynamicMap<ChangeAttributeFactory> attributeFactories;

  static {
    // It is assumed that basic rewrites do not touch visibleto predicates.
    checkState(
        !ChangeIsVisibleToPredicate.class.isAssignableFrom(IndexPredicate.class),
        "ChangeQueryProcessor assumes visibleto is not used by the index rewriter.");
  }

  @Inject
  ChangeQueryProcessor(
      Provider<CurrentUser> userProvider,
      CapabilityControl.Factory capabilityFactory,
      Metrics metrics,
      IndexConfig indexConfig,
      ChangeIndexCollection indexes,
      ChangeIndexRewriter rewriter,
      Provider<ReviewDb> db,
      ChangeControl.GenericFactory changeControlFactory,
      ChangeNotes.Factory notesFactory,
      DynamicMap<ChangeAttributeFactory> attributeFactories) {
    super(
        userProvider,
        capabilityFactory,
        metrics,
        ChangeSchemaDefinitions.INSTANCE,
        indexConfig,
        indexes,
        rewriter,
        FIELD_LIMIT);
    this.db = db;
    this.changeControlFactory = changeControlFactory;
    this.notesFactory = notesFactory;
    this.attributeFactories = attributeFactories;
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
  public List<PluginDefinedInfo> create(ChangeData cd) {
    List<PluginDefinedInfo> plugins = new ArrayList<>(attributeFactories.plugins().size());
    for (String plugin : attributeFactories.plugins()) {
      for (Provider<ChangeAttributeFactory> provider :
          attributeFactories.byPlugin(plugin).values()) {
        PluginDefinedInfo pda = null;
        try {
          pda = provider.get().create(cd, this, plugin);
        } catch (RuntimeException e) {
          /* Eat runtime exceptions so that queries don't fail. */
        }
        if (pda != null) {
          pda.name = plugin;
          plugins.add(pda);
        }
      }
    }
    if (plugins.isEmpty()) {
      plugins = null;
    }
    return plugins;
  }

  @Override
  protected Predicate<ChangeData> enforceVisibility(Predicate<ChangeData> pred) {
    return new AndChangeSource(
        pred,
        new ChangeIsVisibleToPredicate(db, notesFactory, changeControlFactory, userProvider.get()),
        start);
  }
}
