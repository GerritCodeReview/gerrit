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

package com.google.gerrit.server.index;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.IndexDefinition.IndexFactory;
import com.google.inject.ProvisionException;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

public abstract class AbstractVersionManager implements LifecycleListener {
  public static class Version<V> {
    public final Schema<V> schema;
    public final int version;
    public final boolean exists;
    public final boolean ready;

    public Version(Schema<V> schema, int version, boolean exists, boolean ready) {
      checkArgument(schema == null || schema.getVersion() == version);
      this.schema = schema;
      this.version = version;
      this.exists = exists;
      this.ready = ready;
    }
  }

  protected final boolean onlineUpgrade;
  protected final String runReindexMsg;
  protected final SitePaths sitePaths;
  protected final Map<String, IndexDefinition<?, ?, ?>> defs;
  protected final Map<String, OnlineReindexer<?, ?, ?>> reindexers;

  protected AbstractVersionManager(
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
    GerritIndexStatus cfg = createIndexStatus();
    for (IndexDefinition<?, ?, ?> def : defs.values()) {
      initIndex(def, cfg);
    }
  }

  @Override
  public void stop() {
    // Do nothing; indexes are closed on demand by IndexCollection.
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

  /**
   * Tells if an index with this name is currently known or not.
   *
   * @param name index name
   * @return true if index is known and can be used, otherwise false.
   */
  public boolean isKnownIndex(String name) {
    return defs.get(name) != null;
  }

  protected <K, V, I extends Index<K, V>> void initIndex(
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
      if (v.version != search.version) {
        indexes.addWriteIndex(factory.create(v.schema));
      } else {
        indexes.addWriteIndex(searchIndex);
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

  protected GerritIndexStatus createIndexStatus() {
    try {
      return new GerritIndexStatus(sitePaths);
    } catch (ConfigInvalidException | IOException e) {
      throw fail(e);
    }
  }

  protected abstract <K, V, I extends Index<K, V>> TreeMap<Integer, Version<V>> scanVersions(
      IndexDefinition<K, V, I> def, GerritIndexStatus cfg);

  private <V> boolean isDirty(Collection<Version<V>> inUse, Version<V> v) {
    return !inUse.contains(v) && v.exists;
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

  private <V> void markNotReady(
      String name, Iterable<Version<V>> versions, Collection<Version<V>> inUse) {
    GerritIndexStatus cfg = createIndexStatus();
    boolean dirty = false;
    for (Version<V> v : versions) {
      if (isDirty(inUse, v)) {
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
    return e;
  }
}
