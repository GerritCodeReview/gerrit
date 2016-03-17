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
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.OnlineReindexer;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.change.AllChangesIndexer;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.change.ChangeSchemas;
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

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

@Singleton
public class LuceneVersionManager implements LifecycleListener {
  private static final Logger log = LoggerFactory
      .getLogger(LuceneVersionManager.class);

  static final String CHANGES_PREFIX = "changes_";

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

  static Path getDir(SitePaths sitePaths, String prefix, Schema<?> schema) {
    return sitePaths.index_dir.resolve(String.format("%s%04d",
        prefix, schema.getVersion()));
  }

  static FileBasedConfig loadGerritIndexConfig(SitePaths sitePaths)
      throws ConfigInvalidException, IOException {
    FileBasedConfig cfg = new FileBasedConfig(
        sitePaths.index_dir.resolve("gerrit_index.config").toFile(),
        FS.detect());
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
  private final ChangeIndexCollection indexes;
  private final AllChangesIndexer allChangesIndexer;
  private final boolean onlineUpgrade;
  private OnlineReindexer<Change.Id, ChangeData, ChangeIndex> reindexer;

  @Inject
  LuceneVersionManager(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      LuceneChangeIndex.Factory indexFactory,
      ChangeIndexCollection indexes,
      AllChangesIndexer allChangesIndexer) {
    this.sitePaths = sitePaths;
    this.indexFactory = indexFactory;
    this.indexes = indexes;
    this.allChangesIndexer = allChangesIndexer;
    this.onlineUpgrade = cfg.getBoolean("index", null, "onlineUpgrade", true);
  }

  @Override
  public void start() {
    String runReindex =
      "No index versions ready; run java -jar " +
      sitePaths.gerrit_war.toAbsolutePath() +
      " reindex";

    FileBasedConfig cfg;
    try {
      cfg = loadGerritIndexConfig(sitePaths);
    } catch (ConfigInvalidException | IOException e) {
      throw fail(e);
    }

    if (!Files.exists(sitePaths.index_dir)) {
      throw new ProvisionException(runReindex);
    } else if (!Files.exists(sitePaths.index_dir)) {
      log.warn("Not a directory: %s", sitePaths.index_dir.toAbsolutePath());
      throw new ProvisionException(runReindex);
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
      if (write.isEmpty() && onlineUpgrade) {
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
      throw new ProvisionException(runReindex);
    }

    markNotReady(cfg, versions.values(), write);
    LuceneChangeIndex searchIndex = indexFactory.create(search.schema);
    indexes.setSearchIndex(searchIndex);
    for (Version v : write) {
      if (v.schema != null) {
        if (v.version != search.version) {
          indexes.addWriteIndex(indexFactory.create(v.schema));
        } else {
          indexes.addWriteIndex(searchIndex);
        }
      }
    }

    int latest = write.get(0).version;
    if (onlineUpgrade && latest != search.version) {
      reindexer = new OnlineReindexer<>(indexes, allChangesIndexer, latest);
      reindexer.start();
    }
  }

  /**
   * Start the online reindexer if the current index is not already the latest.
   *
   * @return true if started, otherwise false.
   * @throws ReindexerAlreadyRunningException
   */
  public synchronized boolean startReindexer()
      throws ReindexerAlreadyRunningException {
    validateReindexerNotRunning();
    if (!isCurrentIndexVersionLatest()) {
      reindexer.start();
      return true;
    }
    return false;
  }

  /**
   * Activate the latest index if the current index is not already the latest.
   *
   * @return true if index was activate, otherwise false.
   * @throws ReindexerAlreadyRunningException
   */
  public synchronized boolean activateLatestIndex()
      throws ReindexerAlreadyRunningException {
    validateReindexerNotRunning();
    if (!isCurrentIndexVersionLatest()) {
      reindexer.activateIndex();
      return true;
    }
    return false;
  }

  private boolean isCurrentIndexVersionLatest() {
    return reindexer == null
        || reindexer.getVersion() == indexes.getSearchIndex().getSchema()
            .getVersion();
  }

  private void validateReindexerNotRunning()
      throws ReindexerAlreadyRunningException {
    if (reindexer != null && reindexer.isRunning()) {
      throw new ReindexerAlreadyRunningException();
    }
  }

  private TreeMap<Integer, Version> scanVersions(Config cfg) {
    TreeMap<Integer, Version> versions = Maps.newTreeMap();
    for (Schema<ChangeData> schema : ChangeSchemas.ALL.values()) {
      Path p = getDir(sitePaths, CHANGES_PREFIX, schema);
      boolean isDir = Files.isDirectory(p);
      if (Files.exists(p) && !isDir) {
        log.warn("Not a directory: %s", p.toAbsolutePath());
      }
      int v = schema.getVersion();
      versions.put(v, new Version(schema, v, isDir, getReady(cfg, v)));
    }

    try (DirectoryStream<Path> paths =
        Files.newDirectoryStream(sitePaths.index_dir)) {
      for (Path p : paths) {
        String n = p.getFileName().toString();
        if (!n.startsWith(CHANGES_PREFIX)) {
          continue;
        }
        String versionStr = n.substring(CHANGES_PREFIX.length());
        Integer v = Ints.tryParse(versionStr);
        if (v == null || versionStr.length() != 4) {
          log.warn("Unrecognized version in index directory: {}",
              p.toAbsolutePath());
          continue;
        }
        if (!versions.containsKey(v)) {
          versions.put(v, new Version(null, v, true, getReady(cfg, v)));
        }
      }
    } catch (IOException e) {
      log.error("Error scanning index directory: " + sitePaths.index_dir, e);
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
