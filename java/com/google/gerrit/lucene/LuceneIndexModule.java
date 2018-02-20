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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.Schema;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.IndexModule;
import com.google.gerrit.server.index.OnlineUpgrader;
import com.google.gerrit.server.index.SingleVersionModule;
import com.google.gerrit.server.index.VersionManager;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.gerrit.server.index.project.ProjectIndex;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import java.util.Map;
import org.apache.lucene.search.BooleanQuery;
import org.eclipse.jgit.lib.Config;

public class LuceneIndexModule extends AbstractModule {
  public static LuceneIndexModule singleVersionAllLatest(int threads, boolean slave) {
    return new LuceneIndexModule(ImmutableMap.of(), threads, false, slave);
  }

  public static LuceneIndexModule singleVersionWithExplicitVersions(
      Map<String, Integer> versions, int threads, boolean slave) {
    return new LuceneIndexModule(versions, threads, false, slave);
  }

  public static LuceneIndexModule latestVersionWithOnlineUpgrade(boolean slave) {
    return new LuceneIndexModule(null, 0, true, slave);
  }

  public static LuceneIndexModule latestVersionWithoutOnlineUpgrade(boolean slave) {
    return new LuceneIndexModule(null, 0, false, slave);
  }

  static boolean isInMemoryTest(Config cfg) {
    return cfg.getBoolean("index", "lucene", "testInmemory", false);
  }

  private final Map<String, Integer> singleVersions;
  private final int threads;
  private final boolean onlineUpgrade;
  private final boolean slave;

  private LuceneIndexModule(
      Map<String, Integer> singleVersions, int threads, boolean onlineUpgrade, boolean slave) {
    if (singleVersions != null) {
      checkArgument(!onlineUpgrade, "online upgrade is incompatible with single version map");
    }
    this.singleVersions = singleVersions;
    this.threads = threads;
    this.onlineUpgrade = onlineUpgrade;
    this.slave = slave;
  }

  @Override
  protected void configure() {
    if (slave) {
      bind(AccountIndex.Factory.class).toInstance(LuceneIndexModule::createDummyIndexFactory);
      bind(ChangeIndex.Factory.class).toInstance(LuceneIndexModule::createDummyIndexFactory);
      bind(ProjectIndex.Factory.class).toInstance(LuceneIndexModule::createDummyIndexFactory);
    } else {
      install(
          new FactoryModuleBuilder()
              .implement(AccountIndex.class, LuceneAccountIndex.class)
              .build(AccountIndex.Factory.class));
      install(
          new FactoryModuleBuilder()
              .implement(ChangeIndex.class, LuceneChangeIndex.class)
              .build(ChangeIndex.Factory.class));
      install(
          new FactoryModuleBuilder()
              .implement(ProjectIndex.class, LuceneProjectIndex.class)
              .build(ProjectIndex.Factory.class));
    }
    install(
        new FactoryModuleBuilder()
            .implement(GroupIndex.class, LuceneGroupIndex.class)
            .build(GroupIndex.Factory.class));

    install(new IndexModule(threads, slave));
    if (singleVersions == null) {
      install(new MultiVersionModule());
    } else {
      install(new SingleVersionModule(singleVersions));
    }
  }

  @SuppressWarnings("unused")
  private static <T> T createDummyIndexFactory(Schema<?> schema) {
    throw new UnsupportedOperationException();
  }

  @Provides
  @Singleton
  IndexConfig getIndexConfig(@GerritServerConfig Config cfg) {
    BooleanQuery.setMaxClauseCount(
        cfg.getInt("index", "maxTerms", BooleanQuery.getMaxClauseCount()));
    return IndexConfig.fromConfig(cfg).separateChangeSubIndexes(true).build();
  }

  private class MultiVersionModule extends LifecycleModule {
    @Override
    public void configure() {
      bind(VersionManager.class).to(LuceneVersionManager.class);
      listener().to(LuceneVersionManager.class);
      if (onlineUpgrade) {
        listener().to(OnlineUpgrader.class);
      }
    }
  }
}
