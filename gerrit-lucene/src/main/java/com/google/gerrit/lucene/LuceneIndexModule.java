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

import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.ChangeSchemas;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.IndexModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import java.io.IOException;

public class LuceneIndexModule extends LifecycleModule {
  private final boolean checkVersion;
  private final int threads;
  private final boolean readOnly;

  public LuceneIndexModule() {
    this(true, 0, false);
  }

  public LuceneIndexModule(boolean checkVersion, int threads,
      boolean readOnly) {
    this.checkVersion = checkVersion;
    this.threads = threads;
    this.readOnly = readOnly;
  }

  @Override
  protected void configure() {
    install(new IndexModule(threads));
    bind(ChangeIndex.class).to(LuceneChangeIndex.class);
    listener().to(LuceneChangeIndex.class);
    if (checkVersion) {
      listener().to(IndexVersionCheck.class);
    }
  }

  @Provides
  @Singleton
  public LuceneChangeIndex getChangeIndex(@GerritServerConfig Config cfg,
      SitePaths sitePaths,
      @IndexExecutor ListeningScheduledExecutorService executor,
      FillArgs fillArgs) throws IOException {
    return new LuceneChangeIndex(cfg, sitePaths, executor, fillArgs,
        ChangeSchemas.getLatestRelease(), readOnly);
  }
}
