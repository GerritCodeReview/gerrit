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
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gerrit.server.index.IndexConfig;
import com.google.gerrit.server.index.IndexModule;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;

import org.eclipse.jgit.lib.Config;

public class ElasticIndexModule extends LifecycleModule {
  private final int threads;

  public ElasticIndexModule() {
    this(0);
  }

  public ElasticIndexModule(int threads) {
    this.threads = threads;
  }

  @Override
  protected void configure() {
    install(
        new FactoryModuleBuilder()
            .implement(ChangeIndex.class, ElasticChangeIndex.class)
            .build(ChangeIndex.Factory.class));
    install(
        new FactoryModuleBuilder()
            .implement(AccountIndex.class, ElasticAccountIndex.class)
            .build(AccountIndex.Factory.class));

    install(new IndexModule(threads));
    bind(ChangeIndex.class).to(ElasticChangeIndex.class);
    bind(AccountIndex.class).to(ElasticAccountIndex.class);
    listener().to(ElasticChangeIndex.class);
    listener().to(ElasticAccountIndex.class);
  }

  @Provides
  @Singleton
  public ElasticChangeIndex getChangeIndex(@GerritServerConfig Config cfg,
      Provider<ReviewDb> db,
      ChangeData.Factory changeDataFactory,
      ChangeIndexCollection indexes,
      FillArgs fillArgs) {
    return new ElasticChangeIndex(cfg, db, changeDataFactory, fillArgs,
        indexes, null/*ChangeSchemas.getLatest()*/); //TODO(dpursehouse)
  }

  @Provides
  @Singleton
  IndexConfig getIndexConfig(@GerritServerConfig Config cfg) {
    return IndexConfig.fromConfig(cfg);
  }
}
