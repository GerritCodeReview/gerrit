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

package com.google.gerrit.server.index.scheduler;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.account.PeriodicAccountIndexer;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.group.PeriodicGroupIndexer;
import com.google.gerrit.server.project.PeriodicProjectIndexer;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import java.util.HashMap;
import java.util.Map;

public class PeriodicIndexScheduler implements LifecycleListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      listener().to(PeriodicIndexScheduler.class);
      bind(new TypeLiteral<Map<String, PeriodicIndexerConfig>>() {})
          .toProvider(PeriodicIndexerConfigProvider.class)
          .in(Scopes.SINGLETON);
    }
  }

  private final Map<String, PeriodicIndexerConfig> indexerConfigs;
  private final WorkQueue queue;
  private final Map<String, Runnable> indexers = new HashMap<>();

  @Inject
  PeriodicIndexScheduler(
      Map<String, PeriodicIndexerConfig> indexerConfigs,
      WorkQueue queue,
      PeriodicAccountIndexer accountIndexer,
      PeriodicGroupIndexer groupIndexer,
      PeriodicProjectIndexer projectIndexer) {
    this.indexerConfigs = indexerConfigs;
    this.queue = queue;
    indexers.put("accounts", accountIndexer);
    indexers.put("groups", groupIndexer);
    indexers.put("projects", projectIndexer);
  }

  @Override
  public void start() {
    for (Map.Entry<String, Runnable> e : indexers.entrySet()) {
      String indexName = e.getKey();
      if (indexerConfigs.containsKey(indexName)) {
        Runnable indexer = e.getValue();
        PeriodicIndexerConfig config = indexerConfigs.get(indexName);

        if (config.runOnStartup()) {
          indexer.run();
        }

        if (!config.enabled()) {
          logger.atWarning().log("periodic reindexing for %s is disabled", indexName);
          continue;
        }

        queue.scheduleAtFixedRate(indexer, config.schedule());
      }
    }
  }

  @Override
  public void stop() {
    // handled by WorkQueue.stop() already
  }
}
