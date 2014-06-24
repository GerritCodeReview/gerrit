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
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.ChangeSchemas;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.server.index.IndexConfig;
import com.google.gerrit.server.index.IndexModule;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

public class ElasticsearchIndexModule extends LifecycleModule {
  private final int threads;

  public ElasticsearchIndexModule() {
    this(0);
  }

  public ElasticsearchIndexModule(int threads) {
    this.threads = threads;
  }

  @Override
  protected void configure() {
    bind(IndexConfig.class).toInstance(IndexConfig.createDefault());
    install(new IndexModule(threads));
    bind(ChangeIndex.class).to(ElasticsearchChangeIndex.class);
    listener().to(ElasticsearchChangeIndex.class);
  }

  @Provides
  @Singleton
  public ElasticsearchChangeIndex getChangeIndex(@GerritServerConfig Config cfg,
      Provider<ReviewDb> db,
      ChangeData.Factory changeDataFactory,
      IndexCollection indexes,
      FillArgs fillArgs) {
    return new ElasticsearchChangeIndex(cfg, db, changeDataFactory, fillArgs,
        indexes, ChangeSchemas.getLatest());
  }
}
