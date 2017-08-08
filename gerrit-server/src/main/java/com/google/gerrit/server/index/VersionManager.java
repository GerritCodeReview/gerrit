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
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicSet;
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

public abstract class VersionManager implements LifecycleListener {
  public static boolean getOnlineUpgrade(Config cfg) {
    return cfg.getBoolean("index", null, "onlineUpgrade", true);
  }

  public static class Version<V, A> {
    public final Schema<V, A> schema;
    public final int version;
    public final boolean ready;

    public Version(Schema<V, A> schema, int version, boolean ready) {
      checkArgument(schema == null || schema.getVersion() == version);
      this.schema = schema;
      this.version = version;
      this.ready = ready;
    }
  }

  protected final boolean onlineUpgrade;
  protected final String runReindexMsg;
  protected final SitePaths sitePaths;

  private final DynamicSet<OnlineUpgradeListener> listeners;

  // The following fields must be accessed synchronized on this.
  protected final Map<String, IndexDefinition<?, ?, ?, ?>> defs;
  protected final Map<String, OnlineReindexer<?, ?, ?, ?>> reindexers;

  protected VersionManager(
      SitePaths sitePaths,
      DynamicSet<OnlineUpgradeListener> listeners,
      Collection<IndexDefinition<?, ?, ?, ?>> defs,
      boolean onlineUpgrade) {
    this.sitePaths = sitePaths;
    this.listeners = listeners;
    this.defs = Maps.newHashMapWithExpectedSize(defs.size());
    for (IndexDefinition<?, ?, ?, ?> def : defs) {
      this.defs.put(def.getName(), def);
    }

    this.reindexers = Maps.newHashMapWithExpectedSize(defs.size());
    this.onlineUpgrade = onlineUpgrade;
    this.runReindexMsg =
        "No index versions for index '%s' ready; run java -jar "
            + sitePaths.gerrit_war.toAbsolutePath()
            + " reindex";
  }

  @Override
  public void start() {
    GerritIndexStatus cfg = createIndexStatus();
    for (IndexDefinition<?, ?, ?, ?> def : defs.values()) {
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
    OnlineReindexer<?, ?, ?, ?> reindexer = reindexers.get(name);
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
    OnlineReindexer<?, ?, ?, ?> reindexer = reindexers.get(name);
    validateReindexerNotRunning(reindexer);
    if (!isLatestIndexVersion(name, reindexer)) {
      reindexer.activateIndex();
      return true;
    }
    return false;
  }

  protected <K, V, A, I extends Index<K, V, A>> void initIndex(
      IndexDefinition<K, V, A, I> def, GerritIndexStatus cfg) {
    TreeMap<Integer, Version<V, A>> versions = scanVersions(def, cfg);
    // Search from the most recent ready version.
    // Write to the most recent ready version and the most recent version.
    Version<V, A> search = null;
    List<Version<V, A>> write = Lists.newArrayListWithCapacity(2);
    for (Version<V, A> v : versions.descendingMap().values()) {
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
      throw new ProvisionException(String.format(runReindexMsg, def.getName()));
    }

    IndexFactory<K, V, A, I> factory = def.getIndexFactory();
    I searchIndex = factory.create(search.schema);
    IndexCollection<K, V, A, I> indexes = def.getIndexCollection();
    indexes.setSearchIndex(searchIndex);
    for (Version<V, A> v : write) {
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
        OnlineReindexer<K, V, A, I> reindexer =
            new OnlineReindexer<>(def, search.version, latest, listeners);
        reindexers.put(def.getName(), reindexer);
      }
    }
  }

  synchronized void startOnlineUpgrade() {
    checkState(onlineUpgrade, "online upgrade not enabled");
    for (IndexDefinition<?, ?, ?, ?> def : defs.values()) {
      String name = def.getName();
      IndexCollection<?, ?, ?, ?> indexes = def.getIndexCollection();
      Index<?, ?, ?> search = indexes.getSearchIndex();
      checkState(
          search != null, "no search index ready for %s; should have failed at startup", name);
      int searchVersion = search.getSchema().getVersion();

      List<Index<?, ?, ?>> write = ImmutableList.copyOf(indexes.getWriteIndexes());
      checkState(
          !write.isEmpty(),
          "no write indexes set for %s; should have been initialized at startup",
          name);
      int latestWriteVersion = write.get(0).getSchema().getVersion();

      if (latestWriteVersion != searchVersion) {
        OnlineReindexer<?, ?, ?, ?> reindexer = reindexers.get(name);
        checkState(
            reindexer != null,
            "no reindexer found for %s; should have been initialized at startup",
            name);
        reindexer.start();
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

  protected abstract <V, A> boolean isDirty(Collection<Version<V, A>> inUse, Version<V, A> v);

  protected abstract <K, V, A, I extends Index<K, V, A>>
      TreeMap<Integer, Version<V, A>> scanVersions(
          IndexDefinition<K, V, A, I> def, GerritIndexStatus cfg);

  private boolean isLatestIndexVersion(String name, OnlineReindexer<?, ?, ?, ?> reindexer) {
    int readVersion = defs.get(name).getIndexCollection().getSearchIndex().getSchema().getVersion();
    return reindexer == null || reindexer.getVersion() == readVersion;
  }

  private static void validateReindexerNotRunning(OnlineReindexer<?, ?, ?, ?> reindexer)
      throws ReindexerAlreadyRunningException {
    if (reindexer != null && reindexer.isRunning()) {
      throw new ReindexerAlreadyRunningException();
    }
  }

  private <V, A> void markNotReady(
      String name, Iterable<Version<V, A>> versions, Collection<Version<V, A>> inUse) {
    GerritIndexStatus cfg = createIndexStatus();
    boolean dirty = false;
    for (Version<V, A> v : versions) {
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
