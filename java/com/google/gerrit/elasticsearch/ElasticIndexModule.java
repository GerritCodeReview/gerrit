// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.elasticsearch;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.project.ProjectIndex;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.index.IndexModule;
import com.google.gerrit.server.index.OnlineUpgrader;
import com.google.gerrit.server.index.SingleVersionModule;
import com.google.gerrit.server.index.VersionManager;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import java.util.Map;
import org.eclipse.jgit.lib.Config;

public class ElasticIndexModule extends AbstractModule {
  public static ElasticIndexModule singleVersionWithExplicitVersions(
      Map<String, Integer> versions, int threads, boolean slave) {
    return new ElasticIndexModule(versions, threads, false, slave);
  }

  public static ElasticIndexModule latestVersionWithOnlineUpgrade(boolean slave) {
    return new ElasticIndexModule(null, 0, true, slave);
  }

  public static ElasticIndexModule latestVersionWithoutOnlineUpgrade(boolean slave) {
    return new ElasticIndexModule(null, 0, false, slave);
  }

  private final Map<String, Integer> singleVersions;
  private final int threads;
  private final boolean onlineUpgrade;
  private final boolean slave;

  private ElasticIndexModule(
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
      bind(AccountIndex.Factory.class).toInstance(ElasticIndexModule::createDummyIndexFactory);
      bind(ChangeIndex.Factory.class).toInstance(ElasticIndexModule::createDummyIndexFactory);
      bind(ProjectIndex.Factory.class).toInstance(ElasticIndexModule::createDummyIndexFactory);
    } else {
      install(
          new FactoryModuleBuilder()
              .implement(AccountIndex.class, ElasticAccountIndex.class)
              .build(AccountIndex.Factory.class));
      install(
          new FactoryModuleBuilder()
              .implement(ChangeIndex.class, ElasticChangeIndex.class)
              .build(ChangeIndex.Factory.class));
      install(
          new FactoryModuleBuilder()
              .implement(ProjectIndex.class, ElasticProjectIndex.class)
              .build(ProjectIndex.Factory.class));
    }
    install(
        new FactoryModuleBuilder()
            .implement(GroupIndex.class, ElasticGroupIndex.class)
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
    return IndexConfig.fromConfig(cfg).separateChangeSubIndexes(true).build();
  }

  private class MultiVersionModule extends LifecycleModule {
    @Override
    public void configure() {
      bind(VersionManager.class).to(ElasticVersionManager.class);
      listener().to(ElasticVersionManager.class);
      if (onlineUpgrade) {
        listener().to(OnlineUpgrader.class);
      }
    }
  }
}
