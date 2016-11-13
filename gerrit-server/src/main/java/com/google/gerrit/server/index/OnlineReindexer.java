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
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OnlineReindexer<K, V, I extends Index<K, V>> {
  private static final Logger log = LoggerFactory.getLogger(OnlineReindexer.class);

  private final IndexCollection<K, V, I> indexes;
  private final SiteIndexer<K, V, I> batchIndexer;
  private final int version;
  private I index;
  private final AtomicBoolean running = new AtomicBoolean();

  public OnlineReindexer(IndexDefinition<K, V, I> def, int version) {
    this.indexes = def.getIndexCollection();
    this.batchIndexer = def.getSiteIndexer();
    this.version = version;
  }

  public void start() {
    if (running.compareAndSet(false, true)) {
      Thread t =
          new Thread() {
            @Override
            public void run() {
              try {
                reindex();
              } finally {
                running.set(false);
              }
            }
          };
      t.setName(String.format("Reindex v%d-v%d", version(indexes.getSearchIndex()), version));
      t.start();
    }
  }

  public boolean isRunning() {
    return running.get();
  }

  public int getVersion() {
    return version;
  }

  private static int version(Index<?, ?> i) {
    return i.getSchema().getVersion();
  }

  private void reindex() {
    index =
        checkNotNull(
            indexes.getWriteIndex(version), "not an active write schema version: %s", version);
    log.info(
        "Starting online reindex from schema version {} to {}",
        version(indexes.getSearchIndex()),
        version(index));
    SiteIndexer.Result result = batchIndexer.indexAll(index);
    if (!result.success()) {
      log.error(
          "Online reindex of schema version {} failed. Successfully"
              + " indexed {} changes, failed to index {} changes",
          version(index),
          result.doneCount(),
          result.failedCount());
      return;
    }
    log.info("Reindex to version {} complete", version(index));
    activateIndex();
  }

  public void activateIndex() {
    indexes.setSearchIndex(index);
    log.info("Using schema version {}", version(index));
    try {
      index.markReady(true);
    } catch (IOException e) {
      log.warn("Error activating new schema version {}", version(index));
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
        log.warn("Error deactivating old schema version {}", version(i));
      }
    }
  }
}
