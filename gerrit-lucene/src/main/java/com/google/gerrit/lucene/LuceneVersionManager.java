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
import com.google.gerrit.index.GerritIndexStatus;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.Index;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.server.index.IndexDefinition;
import com.google.gerrit.server.index.IndexDefinition.IndexFactory;
import com.google.gerrit.server.index.OnlineReindexer;
import com.google.gerrit.server.index.Schema;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class LuceneVersionManager implements LifecycleListener {
  private static final Logger log = LoggerFactory.getLogger(LuceneVersionManager.class);

  static final String CHANGES_PREFIX = "changes_";

  private static class Version<V> {
    private final Schema<V> schema;
    private final int version;
    private final boolean exists;
    private final boolean ready;

    private Version(Schema<V> schema, int version, boolean exists, boolean ready) {
      checkArgument(schema == null || schema.getVersion() == version);
      this.schema = schema;
      this.version = version;
      this.exists = exists;
      this.ready = ready;
    }
  }

  static Path getDir(SitePaths sitePaths, String prefix, Schema<?> schema) {
    return sitePaths.index_dir.resolve(String.format("%s%04d", prefix, schema.getVersion()));
  }

  private final SitePaths sitePaths;
  private final Map<String, IndexDefinition<?, ?, ?>> defs;
  private final Map<String, OnlineReindexer<?, ?, ?>> reindexers;
  private final boolean onlineUpgrade;
  private final String runReindexMsg;

  @Inject
  LuceneVersionManager(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      Collection<IndexDefinition<?, ?, ?>> defs) {
    this.sitePaths = sitePaths;
    this.defs = Maps.newHashMapWithExpectedSize(defs.size());
    for (IndexDefinition<?, ?, ?> def : defs) {
      this.defs.put(def.getName(), def);
    }

    reindexers = Maps.newHashMapWithExpectedSize(defs.size());
    onlineUpgrade = cfg.getBoolean("index", null, "onlineUpgrade", true);
    runReindexMsg =
        "No index versions ready; run java -jar "
            + sitePaths.gerrit_war.toAbsolutePath()
            + " reindex";
  }

  @Override
  public void start() {
    GerritIndexStatus cfg;
    try {
      cfg = new GerritIndexStatus(sitePaths);
    } catch (ConfigInvalidException | IOException e) {
      throw fail(e);
    }

    if (!Files.exists(sitePaths.index_dir)) {
      throw new ProvisionException(runReindexMsg);
    } else if (!Files.exists(sitePaths.index_dir)) {
      log.warn("Not a directory: %s", sitePaths.index_dir.toAbsolutePath());
      throw new ProvisionException(runReindexMsg);
    }

    for (IndexDefinition<?, ?, ?> def : defs.values()) {
      initIndex(def, cfg);
    }
  }

  private <K, V, I extends Index<K, V>> void initIndex(
      IndexDefinition<K, V, I> def, GerritIndexStatus cfg) {
    TreeMap<Integer, Version<V>> versions = scanVersions(def, cfg);
    // Search from the most recent ready version.
    // Write to the most recent ready version and the most recent version.
    Version<V> search = null;
    List<Version<V>> write = Lists.newArrayListWithCapacity(2);
    for (Version<V> v : versions.descendingMap().values()) {
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
      throw new ProvisionException(runReindexMsg);
    }

    IndexFactory<K, V, I> factory = def.getIndexFactory();
    I searchIndex = factory.create(search.schema);
    IndexCollection<K, V, I> indexes = def.getIndexCollection();
    indexes.setSearchIndex(searchIndex);
    for (Version<V> v : write) {
      if (v.schema != null) {
        if (v.version != search.version) {
          indexes.addWriteIndex(factory.create(v.schema));
        } else {
          indexes.addWriteIndex(searchIndex);
        }
      }
    }

    markNotReady(cfg, def.getName(), versions.values(), write);

    int latest = write.get(0).version;
    OnlineReindexer<K, V, I> reindexer = new OnlineReindexer<>(def, latest);
    synchronized (this) {
      if (!reindexers.containsKey(def.getName())) {
        reindexers.put(def.getName(), reindexer);
        if (onlineUpgrade && latest != search.version) {
          reindexer.start();
        }
      }
    }
  }

  /**
   * Start the online reindexer if the current index is not already the latest.
   *
   * @param force start re-index
   * @return true if started, otherwise false.
   * @throws ReindexerAlreadyRunningException
   */
  public synchronized boolean startReindexer(String name, boolean force)
      throws ReindexerAlreadyRunningException {
    OnlineReindexer<?, ?, ?> reindexer = reindexers.get(name);
    validateReindexerNotRunning(reindexer);
    if (force || !isCurrentIndexVersionLatest(name, reindexer)) {
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
  public synchronized boolean activateLatestIndex(String name)
      throws ReindexerAlreadyRunningException {
    OnlineReindexer<?, ?, ?> reindexer = reindexers.get(name);
    validateReindexerNotRunning(reindexer);
    if (!isCurrentIndexVersionLatest(name, reindexer)) {
      reindexer.activateIndex();
      return true;
    }
    return false;
  }

  private boolean isCurrentIndexVersionLatest(String name, OnlineReindexer<?, ?, ?> reindexer) {
    int readVersion = defs.get(name).getIndexCollection().getSearchIndex().getSchema().getVersion();
    return reindexer == null || reindexer.getVersion() == readVersion;
  }

  private static void validateReindexerNotRunning(OnlineReindexer<?, ?, ?> reindexer)
      throws ReindexerAlreadyRunningException {
    if (reindexer != null && reindexer.isRunning()) {
      throw new ReindexerAlreadyRunningException();
    }
  }

  private <K, V, I extends Index<K, V>> TreeMap<Integer, Version<V>> scanVersions(
      IndexDefinition<K, V, I> def, GerritIndexStatus cfg) {
    TreeMap<Integer, Version<V>> versions = new TreeMap<>();
    for (Schema<V> schema : def.getSchemas().values()) {
      // This part is Lucene-specific.
      Path p = getDir(sitePaths, def.getName(), schema);
      boolean isDir = Files.isDirectory(p);
      if (Files.exists(p) && !isDir) {
        log.warn("Not a directory: %s", p.toAbsolutePath());
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

  private <V> void markNotReady(
      GerritIndexStatus cfg,
      String name,
      Iterable<Version<V>> versions,
      Collection<Version<V>> inUse) {
    boolean dirty = false;
    for (Version<V> v : versions) {
      if (!inUse.contains(v) && v.exists) {
        cfg.setReady(name, v.version, false);
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
