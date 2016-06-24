// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.lucene;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.Index;
import com.google.gerrit.server.index.IndexConfig;
import com.google.gerrit.server.index.IndexDefinition;
import com.google.gerrit.server.index.IndexModule;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import org.apache.lucene.search.BooleanQuery;
import org.eclipse.jgit.lib.Config;

import java.util.Collection;
import java.util.Map;

public class LuceneIndexModule extends LifecycleModule {
  private static final String SINGLE_VERSIONS =
      "LuceneIndexModule/SingleVersions";

  public static LuceneIndexModule singleVersionAllLatest(int threads) {
    return new LuceneIndexModule(ImmutableMap.<String, Integer> of(), threads);
  }

  public static LuceneIndexModule singleVersionWithExplicitVersions(
      Map<String, Integer> versions, int threads) {
    return new LuceneIndexModule(versions, threads);
  }

  public static LuceneIndexModule latestVersionWithOnlineUpgrade() {
    return new LuceneIndexModule(null, 0);
  }

  static boolean isInMemoryTest(Config cfg) {
    return cfg.getBoolean("index", "lucene", "testInmemory", false);
  }

  private final int threads;
  private final Map<String, Integer> singleVersions;

  private LuceneIndexModule(Map<String, Integer> singleVersions, int threads) {
    this.singleVersions = singleVersions;
    this.threads = threads;
  }

  @Override
  protected void configure() {
    install(
        new FactoryModuleBuilder()
            .implement(ChangeIndex.class, LuceneChangeIndex.class)
            .build(ChangeIndex.Factory.class));
    install(
        new FactoryModuleBuilder()
            .implement(AccountIndex.class, LuceneAccountIndex.class)
            .build(AccountIndex.Factory.class));

    install(new IndexModule(threads));
    if (singleVersions == null) {
      install(new MultiVersionModule());
    } else {
      install(new SingleVersionModule());
    }
  }

  @Provides
  @Singleton
  IndexConfig getIndexConfig(@GerritServerConfig Config cfg) {
    BooleanQuery.setMaxClauseCount(cfg.getInt("index", "maxTerms",
        BooleanQuery.getMaxClauseCount()));
    return IndexConfig.fromConfig(cfg);
  }

  private static class MultiVersionModule extends LifecycleModule {
    @Override
    public void configure() {
      listener().to(LuceneVersionManager.class);
    }
  }

  private class SingleVersionModule extends LifecycleModule {
    @Override
    public void configure() {
      listener().to(SingleVersionListener.class);
      bind(new TypeLiteral<Map<String, Integer>>() {})
          .annotatedWith(Names.named(SINGLE_VERSIONS))
          .toInstance(singleVersions);
    }
  }

  @Singleton
  static class SingleVersionListener implements LifecycleListener {
    private final Collection<IndexDefinition<?, ?, ?>> defs;
    private final Map<String, Integer> singleVersions;

    @Inject
    SingleVersionListener(
        Collection<IndexDefinition<?, ?, ?>> defs,
        @Named(SINGLE_VERSIONS) Map<String, Integer> singleVersions) {
      this.defs = defs;
      this.singleVersions = singleVersions;
    }

    @Override
    public void start() {
      for (IndexDefinition<?, ?, ?> def : defs) {
        start(def);
      }
    }

    private <K, V, I extends Index<K, V>> void start(
        IndexDefinition<K, V, I> def) {
      Schema<V> schema;
      Integer v = singleVersions.get(def.getName());
      if (v == null) {
        schema = def.getLatest();
      } else {
        schema = def.getSchemas().get(v);
        if (schema == null) {
          throw new ProvisionException(String.format(
                "Unrecognized %s schema version: %s", def.getName(), v));
        }
      }
      I index = def.getIndexFactory().create(schema);
      def.getIndexCollection().setSearchIndex(index);
      def.getIndexCollection().addWriteIndex(index);
    }

    @Override
    public void stop() {
      // Do nothing; indexes are closed by IndexCollection.
    }
  }
}
