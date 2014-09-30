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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Lists;
import com.google.gerrit.server.index.ChangeBatchIndexer;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class OnlineReindexer {
  private static final Logger log = LoggerFactory
      .getLogger(OnlineReindexer.class);

  public interface Factory {
    OnlineReindexer create(int version);
  }

  private final IndexCollection indexes;
  private final ChangeBatchIndexer batchIndexer;
  private final ProjectCache projectCache;
  private final int version;

  @Inject
  OnlineReindexer(
      IndexCollection indexes,
      ChangeBatchIndexer batchIndexer,
      ProjectCache projectCache,
      @Assisted int version) {
    this.indexes = indexes;
    this.batchIndexer = batchIndexer;
    this.projectCache = projectCache;
    this.version = version;
  }

  public void start() {
    Thread t = new Thread() {
      @Override
      public void run() {
        reindex();
      }
    };
    t.setName(String.format("Reindex v%d-v%d",
        version(indexes.getSearchIndex()), version));
    t.start();
  }

  private static int version(ChangeIndex i) {
    return i.getSchema().getVersion();
  }

  private void reindex() {
    ChangeIndex index = checkNotNull(indexes.getWriteIndex(version),
        "not an active write schema version: %s", version);
    log.info("Starting online reindex from schema version {} to {}",
        version(indexes.getSearchIndex()), version(index));
    ChangeBatchIndexer.Result result = batchIndexer.indexAll(
        index, projectCache.all(), -1, -1, null, null);
    if (!result.success()) {
      log.error("Online reindex of schema version {} failed", version(index));
      return;
    }

    indexes.setSearchIndex(index);
    log.info("Reindex complete, using schema version {}", version(index));
    try {
      index.markReady(true);
    } catch (IOException e) {
      log.warn("Error activating new schema version {}", version(index));
    }

    List<ChangeIndex> toRemove = Lists.newArrayListWithExpectedSize(1);
    for (ChangeIndex i : indexes.getWriteIndexes()) {
      if (version(i) != version(index)) {
        toRemove.add(i);
      }
    }
    for (ChangeIndex i : toRemove) {
      try {
        i.markReady(false);
        indexes.removeWriteIndex(version(i));
      } catch (IOException e) {
        log.warn("Error deactivating old schema version {}", version(i));
      }
    }
  }
}
