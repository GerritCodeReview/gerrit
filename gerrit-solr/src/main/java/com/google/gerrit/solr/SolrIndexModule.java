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

package com.google.gerrit.solr;

import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.ChangeSchemas;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.server.index.IndexModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import java.io.IOException;

public class SolrIndexModule extends LifecycleModule {
  private final boolean checkVersion;
  private final int threads;
  private final String base;

  public SolrIndexModule() {
    this(true, 0, null);
  }

  public SolrIndexModule(boolean checkVersion, int threads, String base) {
    this.checkVersion = checkVersion;
    this.threads = threads;
    this.base = base;
  }

  @Override
  protected void configure() {
    install(new IndexModule(threads));
    bind(ChangeIndex.class).to(SolrChangeIndex.class);
    listener().to(SolrChangeIndex.class);
    if (checkVersion) {
      listener().to(IndexVersionCheck.class);
    }
  }

  @Provides
  @Singleton
  public SolrChangeIndex getChangeIndex(@GerritServerConfig Config cfg,
      SitePaths sitePaths,
      IndexCollection indexes,
      FillArgs fillArgs) throws IOException {
    return new SolrChangeIndex(cfg, fillArgs, sitePaths, indexes,
        ChangeSchemas.getLatest(), base);
  }
}
