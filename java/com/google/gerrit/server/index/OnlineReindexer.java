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

package com.google.gerrit.server.index;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexCollection;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.index.SiteIndexer;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background thread for running an index schema upgrade by reindexing all documents in an index
 * using the new version. Intended to be run while Gerrit is serving traffic to prepare for a
 * near-zero downtime upgrade.
 */
public class OnlineReindexer<K, V, I extends Index<K, V>> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String name;
  private final IndexCollection<K, V, I> indexes;
  private final SiteIndexer<K, V, I> batchIndexer;
  private final int oldVersion;
  private final int newVersion;
  private final PluginSetContext<OnlineUpgradeListener> listeners;
  private I index;
  private final AtomicBoolean running = new AtomicBoolean();

  public OnlineReindexer(
      IndexDefinition<K, V, I> def,
      int oldVersion,
      int newVersion,
      PluginSetContext<OnlineUpgradeListener> listeners) {
    this.name = def.getName();
    this.indexes = def.getIndexCollection();
    this.batchIndexer = def.getSiteIndexer();
    this.oldVersion = oldVersion;
    this.newVersion = newVersion;
    this.listeners = listeners;
  }

  /** Starts the background process. */
  public void start() {
    if (running.compareAndSet(false, true)) {
      Thread t =
          new Thread(
              () -> {
                boolean ok = false;
                try {
                  reindex();
                  ok = true;
                } catch (RuntimeException e) {
                  logger.atSevere().withCause(e).log(
                      "Online reindex of %s schema version %s failed", name, version(index));
                } finally {
                  running.set(false);
                  if (!ok) {
                    listeners.runEach(listener -> listener.onFailure(name, oldVersion, newVersion));
                  }
                }
              });
      t.setName(
          String.format("Reindex %s v%d-v%d", name, version(indexes.getSearchIndex()), newVersion));
      t.start();
    }
  }

  /** Returns {@code true} if the background indexer is currently running. */
  public boolean isRunning() {
    return running.get();
  }

  /** Returns the index version that this indexer is creating documents for. */
  public int getVersion() {
    return newVersion;
  }

  private static int version(Index<?, ?> i) {
    return i.getSchema().getVersion();
  }

  private void reindex() {
    listeners.runEach(listener -> listener.onStart(name, oldVersion, newVersion));
    index =
        requireNonNull(
            indexes.getWriteIndex(newVersion),
            () -> String.format("not an active write schema version: %s %s", name, newVersion));
    logger.atInfo().log(
        "Starting online reindex of %s from schema version %s to %s",
        name, version(indexes.getSearchIndex()), version(index));

    if (oldVersion != newVersion) {
      index.deleteAll();
    }
    SiteIndexer.Result result = batchIndexer.indexAll(index);
    if (!result.success()) {
      logger.atSevere().log(
          "Online reindex of %s schema version %s failed. Successfully"
              + " indexed %s, failed to index %s",
          name, version(index), result.doneCount(), result.failedCount());
      return;
    }
    logger.atInfo().log("Reindex %s to version %s complete", name, version(index));
    activateIndex();
    listeners.runEach(listener -> listener.onSuccess(name, oldVersion, newVersion));
  }

  /**
   * Switches the search index from the old version to the new version. This method should be called
   * when the new version is fully ready.
   */
  public void activateIndex() {
    indexes.setSearchIndex(index);
    logger.atInfo().log("Using %s schema version %s", name, version(index));
    index.markReady(true);

    List<I> toRemove = Lists.newArrayListWithExpectedSize(1);
    for (I i : indexes.getWriteIndexes()) {
      if (version(i) != version(index)) {
        toRemove.add(i);
      }
    }
    for (I i : toRemove) {
      i.markReady(false);
      indexes.removeWriteIndex(version(i));
    }
  }
}
