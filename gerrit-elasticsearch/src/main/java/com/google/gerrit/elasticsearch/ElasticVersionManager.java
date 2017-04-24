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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.Index;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.server.index.IndexDefinition;
import com.google.gerrit.server.index.IndexDefinition.IndexFactory;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.index.OnlineReindexer;
import com.google.gerrit.server.index.ReindexerAlreadyRunningException;
import com.google.gerrit.server.index.Schema;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElasticVersionManager implements LifecycleListener {
  private static final Logger log = LoggerFactory.getLogger(ElasticVersionManager.class);

  private static class Version<V> {
    private final Schema<V> schema;
    private final int version;
    private final boolean ready;

    private Version(Schema<V> schema, int version, boolean ready) {
      checkArgument(schema == null || schema.getVersion() == version);
      this.schema = schema;
      this.version = version;
      this.ready = ready;
    }
  }

  private final Map<String, IndexDefinition<?, ?, ?>> defs;
  private final Map<String, OnlineReindexer<?, ?, ?>> reindexers;
  private final ElasticIndexVersionDiscovery versionDiscovery;
  private final SitePaths sitePaths;
  private final boolean onlineUpgrade;
  private final String runReindexMsg;
  private final String prefix;

  @Inject
  ElasticVersionManager(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      Collection<IndexDefinition<?, ?, ?>> defs,
      ElasticIndexVersionDiscovery versionDiscovery) {
    this.sitePaths = sitePaths;
    this.versionDiscovery = versionDiscovery;
    this.defs = Maps.newHashMapWithExpectedSize(defs.size());
    for (IndexDefinition<?, ?, ?> def : defs) {
      this.defs.put(def.getName(), def);
    }

    prefix = MoreObjects.firstNonNull(cfg.getString("index", null, "prefix"), "gerrit");
    reindexers = Maps.newHashMapWithExpectedSize(defs.size());
    onlineUpgrade = cfg.getBoolean("index", null, "onlineUpgrade", true);
    runReindexMsg =
        "No index versions ready; run java -jar "
            + sitePaths.gerrit_war.toAbsolutePath()
            + " reindex";
  }

  @Override
  public void start() {
    try {
      for (IndexDefinition<?, ?, ?> def : defs.values()) {
        initIndex(def);
      }
    } catch (IOException e) {
      ProvisionException ex = new ProvisionException("Error scanning indexes");
      ex.initCause(e);
      throw ex;
    }
  }

  private <K, V, I extends Index<K, V>> void initIndex(IndexDefinition<K, V, I> def)
      throws IOException {
    TreeMap<Integer, Version<V>> versions = scanVersions(def);
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

    markNotReady(def.getName(), versions.values(), write);

    synchronized (this) {
      if (!reindexers.containsKey(def.getName())) {
        int latest = write.get(0).version;
        OnlineReindexer<K, V, I> reindexer = new OnlineReindexer<>(def, latest);
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
   * @param name index name
   * @param force start re-index
   * @return true if started, otherwise false.
   * @throws ReindexerAlreadyRunningException
   */
  public synchronized boolean startReindexer(String name, boolean force)
      throws ReindexerAlreadyRunningException {
    OnlineReindexer<?, ?, ?> reindexer = reindexers.get(name);
    validateReindexerNotRunning(reindexer);
    if (force || !isLatestIndexVersion(name, reindexer)) {
      reindexer.start();
      return true;
    }
    return false;
  }

  /**
   * Activate the latest index if the current index is not already the latest.
   *
   * @param name index name
   * @return true if index was activated, otherwise false.
   * @throws ReindexerAlreadyRunningException
   */
  public synchronized boolean activateLatestIndex(String name)
      throws ReindexerAlreadyRunningException {
    OnlineReindexer<?, ?, ?> reindexer = reindexers.get(name);
    validateReindexerNotRunning(reindexer);
    if (!isLatestIndexVersion(name, reindexer)) {
      reindexer.activateIndex();
      return true;
    }
    return false;
  }

  private boolean isLatestIndexVersion(String name, OnlineReindexer<?, ?, ?> reindexer) {
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
      IndexDefinition<K, V, I> def) throws IOException {
    TreeMap<Integer, Version<V>> versions = new TreeMap<>();
    for (Schema<V> schema : def.getSchemas().values()) {
      int v = schema.getVersion();
      versions.put(
          v,
          new Version<>(
              schema, v, IndexUtils.getReady(sitePaths, def.getName(), schema.getVersion())));
    }

    try {
      for (String version : versionDiscovery.discover(prefix, def.getName())) {
        Integer v = Ints.tryParse(version);
        if (v == null || version.length() != 4) {
          log.warn("Unrecognized version in index {}: {}", def.getName(), version);
          continue;
        }
        if (!versions.containsKey(v)) {
          versions.put(
              v, new Version<V>(null, v, IndexUtils.getReady(sitePaths, def.getName(), v)));
        }
      }
    } catch (IOException e) {
      log.error("Error scanning index: " + def.getName(), e);
    }
    return versions;
  }

  private <V> void markNotReady(
      String name, Iterable<Version<V>> versions, Collection<Version<V>> inUse) throws IOException {
    for (Version<V> v : versions) {
      if (!inUse.contains(v)) {
        IndexUtils.getReady(sitePaths, name, v.version);
      }
    }
  }

  @Override
  public void stop() {
    // Do nothing; indexes are closed on demand by IndexCollection.
  }
}
