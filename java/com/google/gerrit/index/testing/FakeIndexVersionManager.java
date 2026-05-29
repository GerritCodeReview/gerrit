// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.index.testing;

import com.google.common.collect.Iterables;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.index.Schema;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.GerritIndexStatus;
import com.google.gerrit.server.index.OnlineUpgradeListener;
import com.google.gerrit.server.index.VersionManager;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.TreeMap;
import org.eclipse.jgit.lib.Config;

/** Fake version manager for {@link AbstractFakeIndex}. */
@Singleton
public class FakeIndexVersionManager extends VersionManager {

  @Inject
  FakeIndexVersionManager(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      PluginSetContext<OnlineUpgradeListener> listeners,
      Collection<IndexDefinition<?, ?, ?>> defs) {
    super(
        sitePaths,
        listeners,
        defs,
        VersionManager.shouldPerformOnlineUpgrade(cfg),
        cfg.getBoolean("index", "reuseExistingDocuments", false));
  }

  @Override
  protected <K, V, I extends Index<K, V>> TreeMap<Integer, Version<V>> scanVersions(
      IndexDefinition<K, V, I> def, GerritIndexStatus cfg) {
    TreeMap<Integer, Version<V>> versions = new TreeMap<>();
    for (Schema<V> schema : def.getSchemas().values()) {
      int v = schema.getVersion();
      boolean exists = versions.containsKey(v);
      versions.put(v, new Version<>(schema, v, exists, cfg.getReady(def.getName(), v)));
    }
    return versions;
  }

  @Override
  protected <K, V, I extends Index<K, V>> void initIndex(
      IndexDefinition<K, V, I> def, GerritIndexStatus cfg) {
    // Set latest versions ready.
    if (def.getSchemas().isEmpty()) {
      super.initIndex(def, cfg);
      return;
    }
    Schema<V> schema = Iterables.getLast(def.getSchemas().values());
    try {
      cfg.setReady(def.getName(), schema.getVersion(), true);
      cfg.save();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    super.initIndex(def, cfg);
  }
}
