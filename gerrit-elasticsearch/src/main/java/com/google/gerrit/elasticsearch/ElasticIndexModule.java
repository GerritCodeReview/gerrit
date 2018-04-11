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

import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.AbstractVersionManager;
import com.google.gerrit.server.index.IndexConfig;
import com.google.gerrit.server.index.IndexModule;
import com.google.gerrit.server.index.SingleVersionModule;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import java.util.Map;
import org.eclipse.jgit.lib.Config;

public class ElasticIndexModule extends LifecycleModule {
  private final int threads;
  private final Map<String, Integer> singleVersions;

  public static ElasticIndexModule singleVersionWithExplicitVersions(
      Map<String, Integer> versions, int threads) {
    return new ElasticIndexModule(versions, threads);
  }

  public static ElasticIndexModule latestVersionWithOnlineUpgrade() {
    return new ElasticIndexModule(null, 0);
  }

  private ElasticIndexModule(Map<String, Integer> singleVersions, int threads) {
    this.singleVersions = singleVersions;
    this.threads = threads;
  }

  @Override
  protected void configure() {
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
            .implement(GroupIndex.class, ElasticGroupIndex.class)
            .build(GroupIndex.Factory.class));

    install(new IndexModule(threads));
    if (singleVersions == null) {
      install(new MultiVersionModule());
    } else {
      install(new SingleVersionModule(singleVersions));
    }
  }

  @Provides
  @Singleton
  IndexConfig getIndexConfig(@GerritServerConfig Config cfg) {
    return IndexConfig.fromConfig(cfg);
  }

  private static class MultiVersionModule extends LifecycleModule {
    @Override
    public void configure() {
      bind(AbstractVersionManager.class).to(ElasticVersionManager.class);
      listener().to(ElasticVersionManager.class);
    }
  }
}
