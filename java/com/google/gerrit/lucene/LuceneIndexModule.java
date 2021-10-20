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

import static com.google.gerrit.lucene.AbstractLuceneIndex.IS_AUTO_FLUSH_DISABLED;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.project.ProjectIndex;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.AbstractIndexModule;
import com.google.gerrit.server.index.VersionManager;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.inject.name.Names;
import java.util.Map;
import org.apache.lucene.search.BooleanQuery;
import org.eclipse.jgit.lib.Config;

public class LuceneIndexModule extends AbstractIndexModule {
  public static LuceneIndexModule singleVersionAllLatest(int threads, boolean slave) {
    return new LuceneIndexModule(ImmutableMap.of(), threads, slave);
  }

  public static LuceneIndexModule singleVersionWithExplicitVersions(
      Map<String, Integer> versions, int threads, boolean slave) {
    return new LuceneIndexModule(versions, threads, slave);
  }

  public static LuceneIndexModule latestVersion(boolean slave) {
    return new LuceneIndexModule(null, 0, slave);
  }

  static boolean isInMemoryTest(Config cfg) {
    return cfg.getBoolean("index", "lucene", "testInmemory", false);
  }

  private LuceneIndexModule(Map<String, Integer> singleVersions, int threads, boolean slave) {
    super(singleVersions, threads, slave);
  }

  @Override
  protected void configure() {
    super.configure();
    bind(Boolean.class).annotatedWith(Names.named(IS_AUTO_FLUSH_DISABLED)).toInstance(false);
  }

  @Override
  protected Class<? extends AccountIndex> getAccountIndex() {
    return LuceneAccountIndex.class;
  }

  @Override
  protected Class<? extends ChangeIndex> getChangeIndex() {
    return LuceneChangeIndex.class;
  }

  @Override
  protected Class<? extends GroupIndex> getGroupIndex() {
    return LuceneGroupIndex.class;
  }

  @Override
  protected Class<? extends ProjectIndex> getProjectIndex() {
    return LuceneProjectIndex.class;
  }

  @Override
  protected Class<? extends VersionManager> getVersionManager() {
    return LuceneVersionManager.class;
  }

  @Override
  protected IndexConfig getIndexConfig(@GerritServerConfig Config cfg) {
    BooleanQuery.setMaxClauseCount(
        cfg.getInt("index", "maxTerms", BooleanQuery.getMaxClauseCount()));
    return super.getIndexConfig(cfg);
  }
}
