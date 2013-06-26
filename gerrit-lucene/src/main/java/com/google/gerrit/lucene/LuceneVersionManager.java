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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.ChangeSchemas;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

class LuceneVersionManager implements LifecycleListener {
  private static final Logger log = LoggerFactory
      .getLogger(LuceneVersionManager.class);

  private static final String CHANGES_PREFIX = "changes_";

  private static class VersionSet {
    private Integer search;
    private TreeSet<Integer> write = Sets.newTreeSet();

    private void setSearchVersion(String str) {
      search = parse(str, "search version");
    }

    private void addWriteVersion(String str) {
      int v = parse(str, "write version");
      if (write.contains(v)) {
        throw new IllegalStateException("duplicate write version: " + str);
      }
      write.add(v);
    }

    private static int parse(String str, String name) {
      Integer n = Ints.tryParse(str);
      if (n == null) {
        throw new IllegalStateException(
            String.format("invalid %s %s", name, str));
      }
      return n;
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

  private final SitePaths sitePaths;
  private final IndexCollection indexes;
  private final LuceneChangeIndex.Factory indexFactory;

  @Inject
  LuceneVersionManager(
      SitePaths sitePaths,
      LuceneChangeIndex.Factory indexFactory,
      IndexCollection indexes) {
    this.sitePaths = sitePaths;
    this.indexFactory = indexFactory;
    this.indexes = indexes;
  }

  @Override
  public void start() {
    try {
      FileBasedConfig cfg = loadGerritIndexConfig(sitePaths);
      if (!loadFromConfig(cfg)) {
        loadFromDirectory();
      }
    } catch (ConfigInvalidException e) {
      throw new IllegalStateException(e);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private boolean loadFromConfig(FileBasedConfig cfg) throws IOException {
    String searchStr = cfg.getString("index", null, "searchVersion");
    String[] writeStrs = cfg.getStringList("index", null, "writeVersion");
    if ((searchStr == null && writeStrs.length > 0)
        || (searchStr != null && writeStrs.length == 0)) {
      throw new IllegalStateException(
          "must specify either both searchVersion and writeVersion, or neither");
    } else if (searchStr == null) {
      return false;
    }
    VersionSet versions = new VersionSet();
    versions.setSearchVersion(searchStr);
    for (String writeStr : writeStrs) {
      versions.addWriteVersion(writeStr);
    }
    loadVersions(versions);
    markNotReady(cfg, versions.write);
    return true;
  }

  private void markNotReady(FileBasedConfig cfg, Set<Integer> ready)
      throws IOException {
    // We are about to start writing to some versions but not others. Mark the
    // others as not ready.
    VersionSet versions = scanDirectory(false);
    for (int version : versions.write) {
      cfg.setBoolean("index", Integer.toString(version), "ready", false);
    }
    if (!versions.write.isEmpty()) {
      cfg.save();
    }
  }

  private VersionSet scanDirectory(boolean warn) {
    VersionSet versions = new VersionSet();
    for (File f : sitePaths.index_dir.listFiles()) {
      String p = f.getAbsolutePath();
      if (!f.isDirectory()) {
        log.warn("Not a directory: %s", p);
        continue;
      }
      if (!f.getName().startsWith(CHANGES_PREFIX)) {
        log.warn("Unrecognized version in index directory: %s", p);
        continue;
      }
      String versionStr = f.getName().substring(CHANGES_PREFIX.length());
      if (versionStr.length() != 4) {
        log.warn("Unrecognized version in index directory: %s", p);
        continue;
      }
      versions.addWriteVersion(versionStr);
    }
    return versions;
  }

  private void loadFromDirectory() {
    loadVersions(scanDirectory(true));
  }

  private void loadVersions(VersionSet versions) {
    checkState(!versions.write.isEmpty(), "no index versions specified/found");
    if (versions.search == null) {
      versions.search = versions.write.descendingIterator().next();
    } else {
      checkState(versions.write.contains(versions.search),
          "must write to search schema version %s", versions.search);
    }
    Schema<ChangeData> searchSchema = getSchema(versions.search);
    LuceneChangeIndex searchIndex = indexFactory.create(searchSchema);
    indexes.setSearchIndex(searchIndex);
    for (int version : versions.write) {
      if (version == versions.search) {
        indexes.addWriteIndex(searchIndex);
      } else {
        indexes.addWriteIndex(indexFactory.create(getSchema(version)));
      }
    }
  }

  private static Schema<ChangeData> getSchema(int version) {
    Schema<ChangeData> schema = ChangeSchemas.get(version);
    checkState(schema != null, "unrecognized schema version %s", version);
    return schema;
  }

  @Override
  public void stop() {
  }
}
