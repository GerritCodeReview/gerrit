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

package com.google.gerrit.pgm.init.index;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.index.IndexDefinition;
import com.google.gerrit.server.index.SchemaDefinitions;
import com.google.gerrit.server.index.SingleVersionModule;
import com.google.gerrit.server.index.SingleVersionModule.SingleVersionListener;
import com.google.gerrit.server.index.account.AccountIndexCollection;
import com.google.gerrit.server.index.account.AccountIndexDefinition;
import com.google.gerrit.server.index.account.AccountSchemaDefinitions;
import com.google.gerrit.server.index.account.AllAccountsIndexer;
import com.google.gerrit.server.index.group.AllGroupsIndexer;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.gerrit.server.index.group.GroupIndexDefinition;
import com.google.gerrit.server.index.group.GroupSchemaDefinitions;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.google.inject.util.Providers;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class IndexModuleOnInit extends AbstractModule {
  static final String INDEX_MANAGER = "IndexModuleOnInit/IndexManager";

  private static final ImmutableCollection<SchemaDefinitions<?>> ALL_SCHEMA_DEFS =
      ImmutableList.<SchemaDefinitions<?>>of(
          AccountSchemaDefinitions.INSTANCE, GroupSchemaDefinitions.INSTANCE);

  @Override
  protected void configure() {
    // The AccountIndex implementations (LuceneAccountIndex and
    // ElasticAccountIndex) need AccountCache only for reading from the index.
    // On init we only want to write to the index, hence we don't need the
    // account cache.
    bind(AccountCache.class).toProvider(Providers.of(null));

    // AccountIndexDefinition wants to have AllAccountsIndexer but it is only
    // used by the Reindex program and the OnlineReindexer which are both not
    // used during init, hence we don't need AllAccountsIndexer.
    bind(AllAccountsIndexer.class).toProvider(Providers.of(null));

    bind(AccountIndexCollection.class);

    // The GroupIndex implementations (LuceneGroupIndex and ElasticGroupIndex)
    // need GroupCache only for reading from the index. On init we only want to
    // write to the index, hence we don't need the group cache.
    bind(GroupCache.class).toProvider(Providers.of(null));

    // GroupIndexDefinition wants to have AllGroupsIndexer but it is only used
    // by the Reindex program and the OnlineReindexer which are both not used
    // during init, hence we don't need AllGroupsIndexer.
    bind(AllGroupsIndexer.class).toProvider(Providers.of(null));

    bind(GroupIndexCollection.class);

    bind(new TypeLiteral<Map<String, Integer>>() {})
        .annotatedWith(Names.named(SingleVersionModule.SINGLE_VERSIONS))
        .toInstance(ImmutableMap.<String, Integer>of());
    bind(LifecycleListener.class)
        .annotatedWith(Names.named(INDEX_MANAGER))
        .to(SingleVersionListener.class);
  }

  @Provides
  Collection<IndexDefinition<?, ?, ?>> getIndexDefinitions(
      AccountIndexDefinition accounts, GroupIndexDefinition groups) {
    Collection<IndexDefinition<?, ?, ?>> result =
        ImmutableList.<IndexDefinition<?, ?, ?>>of(accounts, groups);
    Set<String> expected =
        FluentIterable.from(ALL_SCHEMA_DEFS).transform(SchemaDefinitions::getName).toSet();
    Set<String> actual = FluentIterable.from(result).transform(IndexDefinition::getName).toSet();
    if (!expected.equals(actual)) {
      throw new ProvisionException(
          "need index definitions for all schemas: " + expected + " != " + actual);
    }
    return result;
  }
}
