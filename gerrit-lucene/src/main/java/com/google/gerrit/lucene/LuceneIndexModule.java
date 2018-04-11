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
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.AbstractIndexModule;
import com.google.gerrit.server.index.AbstractVersionManager;
import com.google.gerrit.server.index.IndexConfig;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.group.GroupIndex;
import java.util.Map;
import org.apache.lucene.search.BooleanQuery;
import org.eclipse.jgit.lib.Config;

public class LuceneIndexModule extends AbstractIndexModule {
  public static LuceneIndexModule singleVersionAllLatest(int threads) {
    return new LuceneIndexModule(ImmutableMap.<String, Integer>of(), threads);
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

  private LuceneIndexModule(Map<String, Integer> singleVersions, int threads) {
    super(singleVersions, threads);
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
  protected Class<? extends AbstractVersionManager> getVersionManager() {
    return LuceneVersionManager.class;
  }

  @Override
  protected IndexConfig getIndexConfig(@GerritServerConfig Config cfg) {
    BooleanQuery.setMaxClauseCount(
        cfg.getInt("index", "maxTerms", BooleanQuery.getMaxClauseCount()));
    return super.getIndexConfig(cfg);
  }
}
