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

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.common.primitives.Ints;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.index.Schema;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.GerritIndexStatus;
import com.google.gerrit.server.index.OnlineUpgradeListener;
import com.google.gerrit.server.index.VersionManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;
import org.eclipse.jgit.lib.Config;

@Singleton
public class ElasticIndexVersionManager extends VersionManager {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String prefix;
  private final ElasticIndexVersionDiscovery versionDiscovery;

  @Inject
  ElasticIndexVersionManager(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      DynamicSet<OnlineUpgradeListener> listeners,
      Collection<IndexDefinition<?, ?, ?>> defs,
      ElasticIndexVersionDiscovery versionDiscovery) {
    super(sitePaths, listeners, defs, VersionManager.getOnlineUpgrade(cfg));
    this.versionDiscovery = versionDiscovery;
    prefix = Strings.nullToEmpty(cfg.getString("elasticsearch", null, "prefix"));
  }

  @Override
  protected <K, V, I extends Index<K, V>> TreeMap<Integer, Version<V>> scanVersions(
      IndexDefinition<K, V, I> def, GerritIndexStatus cfg) {
    TreeMap<Integer, Version<V>> versions = new TreeMap<>();
    try {
      List<String> discovered = versionDiscovery.discover(prefix, def.getName());
      logger.atFine().log("Discovered versions for %s: %s", def.getName(), discovered);
      for (String version : discovered) {
        Integer v = Ints.tryParse(version);
        if (v == null || version.length() != 4) {
          logger.atWarning().log("Unrecognized version in index %s: %s", def.getName(), version);
          continue;
        }
        versions.put(v, new Version<>(null, v, true, cfg.getReady(def.getName(), v)));
      }
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Error scanning index: %s", def.getName());
    }

    for (Schema<V> schema : def.getSchemas().values()) {
      int v = schema.getVersion();
      boolean exists = versions.containsKey(v);
      versions.put(v, new Version<>(schema, v, exists, cfg.getReady(def.getName(), v)));
    }
    return versions;
  }
}
