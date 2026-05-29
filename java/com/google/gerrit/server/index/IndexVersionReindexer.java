// Copyright (C) 2024 The Android Open Source Project
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

import static com.google.gerrit.server.git.QueueProvider.QueueType.BATCH;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.index.SiteIndexer;
import com.google.inject.Inject;
import java.util.concurrent.Future;

public class IndexVersionReindexer {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private ListeningExecutorService executor;

  @Inject
  IndexVersionReindexer(@IndexExecutor(BATCH) ListeningExecutorService executor) {
    this.executor = executor;
  }

  public <K, V, I extends Index<K, V>> Future<SiteIndexer.Result> reindex(
      IndexDefinition<K, V, I> def, int version, boolean reuse, boolean notifyListeners) {
    I index = def.getIndexCollection().getWriteIndex(version);
    SiteIndexer<K, V, I> siteIndexer = def.getSiteIndexer(reuse);
    return executor.submit(
        () -> {
          String name = def.getName();
          logger.atInfo().log("Starting reindex of %s version %d", name, version);
          SiteIndexer.Result result = siteIndexer.indexAll(index, notifyListeners);
          if (result.success()) {
            logger.atInfo().log("Reindex %s version %s complete", name, version);
          } else {
            logger.atInfo().log(
                "Reindex %s version %s failed. Successfully indexed %s, failed to index %s",
                name, version, result.doneCount(), result.failedCount());
          }
          return result;
        });
  }
}
