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

import com.google.common.primitives.Ints;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.AbstractVersionManager;
import com.google.gerrit.server.index.GerritIndexStatus;
import com.google.gerrit.server.index.Index;
import com.google.gerrit.server.index.IndexDefinition;
import com.google.gerrit.server.index.Schema;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.TreeMap;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class LuceneVersionManager extends AbstractVersionManager implements LifecycleListener {
  private static final Logger log = LoggerFactory.getLogger(LuceneVersionManager.class);

  static Path getDir(SitePaths sitePaths, String prefix, Schema<?> schema) {
    return sitePaths.index_dir.resolve(String.format("%s%04d", prefix, schema.getVersion()));
  }

  @Inject
  LuceneVersionManager(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      Collection<IndexDefinition<?, ?, ?>> defs) {
    super(cfg, sitePaths, defs);
  }

  @Override
  protected <K, V, I extends Index<K, V>>
      TreeMap<Integer, AbstractVersionManager.Version<V>> scanVersions(
          IndexDefinition<K, V, I> def, GerritIndexStatus cfg) {
    TreeMap<Integer, AbstractVersionManager.Version<V>> versions = new TreeMap<>();
    for (Schema<V> schema : def.getSchemas().values()) {
      // This part is Lucene-specific.
      Path p = getDir(sitePaths, def.getName() + "_", schema);
      boolean isDir = Files.isDirectory(p);
      if (Files.exists(p) && !isDir) {
        log.warn("Not a directory: {}", p.toAbsolutePath());
      }
      int v = schema.getVersion();
      versions.put(v, new Version<>(schema, v, isDir, cfg.getReady(def.getName(), v)));
    }

    String prefix = def.getName() + "_";
    try (DirectoryStream<Path> paths = Files.newDirectoryStream(sitePaths.index_dir)) {
      for (Path p : paths) {
        String n = p.getFileName().toString();
        if (!n.startsWith(prefix)) {
          continue;
        }
        String versionStr = n.substring(prefix.length());
        Integer v = Ints.tryParse(versionStr);
        if (v == null || versionStr.length() != 4) {
          log.warn("Unrecognized version in index directory: {}", p.toAbsolutePath());
          continue;
        }
        if (!versions.containsKey(v)) {
          versions.put(v, new Version<V>(null, v, true, cfg.getReady(def.getName(), v)));
        }
      }
    } catch (IOException e) {
      log.error("Error scanning index directory: " + sitePaths.index_dir, e);
    }
    return versions;
  }
}
