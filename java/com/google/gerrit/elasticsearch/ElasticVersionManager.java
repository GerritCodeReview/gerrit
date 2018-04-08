// Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.base.MoreObjects;
import com.google.common.primitives.Ints;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.index.Schema;
import com.google.gerrit.server.index.GerritIndexStatus;
import com.google.gerrit.server.index.OnlineUpgradeListener;
import com.google.gerrit.server.index.VersionManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.TreeMap;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElasticVersionManager extends VersionManager {
  private static final Logger log = LoggerFactory.getLogger(ElasticVersionManager.class);

  private final String prefix;
  private final ElasticIndexVersionDiscovery versionDiscovery;

  @Inject
  ElasticVersionManager(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      DynamicSet<OnlineUpgradeListener> listeners,
      Collection<IndexDefinition<?, ?, ?>> defs,
      ElasticIndexVersionDiscovery versionDiscovery) {
    super(sitePaths, listeners, defs, VersionManager.getOnlineUpgrade(cfg));
    this.versionDiscovery = versionDiscovery;
    prefix = MoreObjects.firstNonNull(cfg.getString("index", null, "prefix"), "gerrit");
  }

  @Override
  protected <K, V, I extends Index<K, V>> TreeMap<Integer, Version<V>> scanVersions(
      IndexDefinition<K, V, I> def, GerritIndexStatus cfg) {
    TreeMap<Integer, Version<V>> versions = new TreeMap<>();
    try {
      for (String version : versionDiscovery.discover(prefix, def.getName())) {
        Integer v = Ints.tryParse(version);
        if (v == null || version.length() != 4) {
          log.warn("Unrecognized version in index {}: {}", def.getName(), version);
          continue;
        }
        versions.put(v, new Version<V>(null, v, true, cfg.getReady(def.getName(), v)));
      }
    } catch (IOException e) {
      log.error("Error scanning index: " + def.getName(), e);
    }

    for (Schema<V> schema : def.getSchemas().values()) {
      int v = schema.getVersion();
      boolean exists = versions.containsKey(v);
      versions.put(v, new Version<>(schema, v, exists, cfg.getReady(def.getName(), v)));
    }
    return versions;
  }
}
