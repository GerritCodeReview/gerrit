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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.lucene;

import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.index.ChangeIndexerImpl;
import com.google.gerrit.server.query.change.IndexRewrite;
import com.google.gerrit.server.query.change.IndexRewriteImpl;
import com.google.inject.Injector;
import com.google.inject.Key;

import org.eclipse.jgit.lib.Config;

public class LuceneIndexModule extends LifecycleModule {
  public static boolean isEnabled(Injector injector) {
    return injector.getInstance(Key.get(Config.class, GerritServerConfig.class))
        .getBoolean("index", null, "enabled", false);
  }

  private final boolean checkVersion;

  public LuceneIndexModule() {
    this(true);
  }

  public LuceneIndexModule(boolean checkVersion) {
    this.checkVersion = checkVersion;
  }

  @Override
  protected void configure() {
    bind(ChangeIndex.Manager.class).to(LuceneChangeIndexManager.class);
    bind(ChangeIndexer.class).to(ChangeIndexerImpl.class);
    bind(IndexRewrite.class).to(IndexRewriteImpl.class);
    listener().to(LuceneChangeIndexManager.class);
    if (checkVersion) {
      listener().to(IndexVersionCheck.class);
    }
  }
}
