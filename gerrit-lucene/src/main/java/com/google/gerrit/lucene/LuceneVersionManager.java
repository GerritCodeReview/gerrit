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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.ChangeSchemas;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

@Singleton
class LuceneVersionManager implements LifecycleListener {
  private static final Logger log = LoggerFactory
      .getLogger(LuceneVersionManager.class);

  private static final String CHANGES_PREFIX = "changes_";

  private static class Version {
    private final Schema<ChangeData> schema;
    private final int version;
    private final boolean exists;
    private final boolean ready;

    private Version(Schema<ChangeData> schema, int version, boolean exists,
        boolean ready) {
      checkArgument(schema == null || schema.getVersion() == version);
      this.schema = schema;
      this.version = version;
      this.exists = exists;
      this.ready = ready;
    }
  }

  static File getDir(SitePaths sitePaths, Schema<ChangeData> schema) {
    return new File(sitePaths.index_dir, String.format("%s%04d",
        CHANGES_PREFIX, schema.getVersion()));
  }

  static FileBasedConfig loadGerritIndexConfig(SitePaths sitePaths)
      throws ConfigInvalidException, IOException {
    FileBasedConfig cfg = new FileBasedConfig(
        new File(sitePaths.index_dir, "gerrit_index.config"), FS.detect());
    cfg.load();
    return cfg;
  }

  static void setReady(Config cfg, int version, boolean ready) {
    cfg.setBoolean("index", Integer.toString(version), "ready", ready);
  }

  private static boolean getReady(Config cfg, int version) {
    return cfg.getBoolean("index", Integer.toString(version), "ready", false);
  }

  private final SitePaths sitePaths;
  private final LuceneChangeIndex.Factory indexFactory;
  private final IndexCollection indexes;
  private final OnlineReindexer.Factory reindexerFactory;

  @Inject
  LuceneVersionManager(
      SitePaths sitePaths,
      LuceneChangeIndex.Factory indexFactory,
      IndexCollection indexes,
      OnlineReindexer.Factory reindexerFactory) {
    this.sitePaths = sitePaths;
    this.indexFactory = indexFactory;
    this.indexes = indexes;
    this.reindexerFactory = reindexerFactory;
  }

  @Override
  public void start() {
    FileBasedConfig cfg;
    try {
      cfg = loadGerritIndexConfig(sitePaths);
    } catch (ConfigInvalidException e) {
      throw fail(e);
    } catch (IOException e) {
      throw fail(e);
    }

    if (!sitePaths.index_dir.exists()) {
      throw new ProvisionException("No index versions ready; run Reindex");
    } else if (!sitePaths.index_dir.isDirectory()) {
      log.warn("Not a directory: %s", sitePaths.index_dir.getAbsolutePath());
      throw new ProvisionException("No index versions ready; run Reindex");
    }

    TreeMap<Integer, Version> versions = scanVersions(cfg);
    // Search from the most recent ready version.
    // Write to the most recent ready version and the most recent version.
    Version search = null;
    List<Version> write = Lists.newArrayListWithCapacity(2);
    for (Version v : versions.descendingMap().values()) {
      if (v.schema == null) {
        continue;
      }
      if (write.isEmpty()) {
        write.add(v);
      }
      if (v.ready) {
        search = v;
        if (!write.contains(v)) {
          write.add(v);
        }
        break;
      }
    }
    if (search == null) {
      throw new ProvisionException("No index versions ready; run Reindex");
    }

    markNotReady(cfg, versions.values(), write);
    LuceneChangeIndex searchIndex = indexFactory.create(search.schema, null);
    indexes.setSearchIndex(searchIndex);
    for (Version v : write) {
      if (v.schema != null) {
        if (v.version != search.version) {
          indexes.addWriteIndex(indexFactory.create(v.schema, null));
        } else {
          indexes.addWriteIndex(searchIndex);
        }
      }
    }

    int latest = write.get(0).version;
    if (latest != search.version) {
      reindexerFactory.create(latest).start();
    }
  }

  private TreeMap<Integer, Version> scanVersions(Config cfg) {
    TreeMap<Integer, Version> versions = Maps.newTreeMap();
    for (Schema<ChangeData> schema : ChangeSchemas.ALL.values()) {
      File f = getDir(sitePaths, schema);
      boolean exists = f.exists() && f.isDirectory();
      if (f.exists() && !f.isDirectory()) {
        log.warn("Not a directory: %s", f.getAbsolutePath());
      }
      int v = schema.getVersion();
      versions.put(v, new Version(schema, v, exists, getReady(cfg, v)));
    }

    for (File f : sitePaths.index_dir.listFiles()) {
      if (!f.getName().startsWith(CHANGES_PREFIX)) {
        continue;
      }
      String versionStr = f.getName().substring(CHANGES_PREFIX.length());
      Integer v = Ints.tryParse(versionStr);
      if (v == null || versionStr.length() != 4) {
        log.warn("Unrecognized version in index directory: {}",
            f.getAbsolutePath());
        continue;
      }
      if (!versions.containsKey(v)) {
        versions.put(v, new Version(null, v, true, getReady(cfg, v)));
      }
    }
    return versions;
  }

  private void markNotReady(FileBasedConfig cfg, Iterable<Version> versions,
      Collection<Version> inUse) {
    boolean dirty = false;
    for (Version v : versions) {
      if (!inUse.contains(v) && v.exists) {
        setReady(cfg, v.version, false);
        dirty = true;
      }
    }
    if (dirty) {
      try {
        cfg.save();
      } catch (IOException e) {
        throw fail(e);
      }
    }
  }

  private ProvisionException fail(Throwable t) {
    ProvisionException e = new ProvisionException("Error scanning indexes");
    e.initCause(t);
    throw e;
  }

  @Override
  public void stop() {
    // Do nothing; indexes are closed on demand by IndexCollection.
  }
}
