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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexCollection;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.index.SiteIndexer;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OnlineReindexer<K, V, I extends Index<K, V>> {
  private static final Logger log = LoggerFactory.getLogger(OnlineReindexer.class);

  private final String name;
  private final IndexCollection<K, V, I> indexes;
  private final SiteIndexer<K, V, I> batchIndexer;
  private final int oldVersion;
  private final int newVersion;
  private final DynamicSet<OnlineUpgradeListener> listeners;
  private I index;
  private final AtomicBoolean running = new AtomicBoolean();

  public OnlineReindexer(
      IndexDefinition<K, V, I> def,
      int oldVersion,
      int newVersion,
      DynamicSet<OnlineUpgradeListener> listeners) {
    this.name = def.getName();
    this.indexes = def.getIndexCollection();
    this.batchIndexer = def.getSiteIndexer();
    this.oldVersion = oldVersion;
    this.newVersion = newVersion;
    this.listeners = listeners;
  }

  public void start() {
    if (running.compareAndSet(false, true)) {
      Thread t =
          new Thread() {
            @Override
            public void run() {
              boolean ok = false;
              try {
                reindex();
                ok = true;
              } finally {
                running.set(false);
                if (!ok) {
                  for (OnlineUpgradeListener listener : listeners) {
                    listener.onFailure(name, oldVersion, newVersion);
                  }
                }
              }
            }
          };
      t.setName(
          String.format("Reindex %s v%d-v%d", name, version(indexes.getSearchIndex()), newVersion));
      t.start();
    }
  }

  public boolean isRunning() {
    return running.get();
  }

  public int getVersion() {
    return newVersion;
  }

  private static int version(Index<?, ?> i) {
    return i.getSchema().getVersion();
  }

  private void reindex() {
    for (OnlineUpgradeListener listener : listeners) {
      listener.onStart(name, oldVersion, newVersion);
    }
    index =
        checkNotNull(
            indexes.getWriteIndex(newVersion),
            "not an active write schema version: %s %s",
            name,
            newVersion);
    log.info(
        "Starting online reindex of {} from schema version {} to {}",
        name,
        version(indexes.getSearchIndex()),
        version(index));
    SiteIndexer.Result result = batchIndexer.indexAll(index);
    if (!result.success()) {
      log.error(
          "Online reindex of {} schema version {} failed. Successfully"
              + " indexed {}, failed to index {}",
          name,
          version(index),
          result.doneCount(),
          result.failedCount());
      return;
    }
    log.info("Reindex {} to version {} complete", name, version(index));
    activateIndex();
    for (OnlineUpgradeListener listener : listeners) {
      listener.onSuccess(name, oldVersion, newVersion);
    }
  }

  public void activateIndex() {
    indexes.setSearchIndex(index);
    log.info("Using {} schema version {}", name, version(index));
    try {
      index.markReady(true);
    } catch (IOException e) {
      log.warn("Error activating new {} schema version {}", name, version(index));
    }

    List<I> toRemove = Lists.newArrayListWithExpectedSize(1);
    for (I i : indexes.getWriteIndexes()) {
      if (version(i) != version(index)) {
        toRemove.add(i);
      }
    }
    for (I i : toRemove) {
      try {
        i.markReady(false);
        indexes.removeWriteIndex(version(i));
      } catch (IOException e) {
        log.warn("Error deactivating old {} schema version {}", name, version(i));
      }
    }
  }
}
