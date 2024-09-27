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
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritIsReplica;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.config.ScheduleConfig.Schedule;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.group.PeriodicGroupIndexer;
import com.google.gerrit.server.project.PeriodicProjectIndexer;
import com.google.inject.Inject;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

public class PeriodicIndexScheduler implements LifecycleListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      listener().to(PeriodicIndexScheduler.class);
    }
  }

  private final Config cfg;
  private final WorkQueue queue;
  private final Map<String, Runnable> indexers = new HashMap<>();
  private final boolean isReplica;
  private final Collection<IndexDefinition<?, ?, ?>> defs;

  @Inject
  PeriodicIndexScheduler(
      @GerritServerConfig Config cfg,
      WorkQueue queue,
      PeriodicGroupIndexer groupIndexer,
      PeriodicProjectIndexer projectIndexer,
      @GerritIsReplica boolean isReplica,
      Collection<IndexDefinition<?, ?, ?>> defs) {
    this.cfg = cfg;
    this.queue = queue;
    indexers.put("groups", groupIndexer);
    indexers.put("projects", projectIndexer);
    this.isReplica = isReplica;
    this.defs = defs;
  }

  @Override
  public void start() {
    for (IndexDefinition<?, ?, ?> def : defs) {
      start(def.getName());
    }
  }

  private void start(String indexName) {
    Runnable indexer = indexers.get(indexName);
    if (indexer == null) {
      logger.atFine().log("Periodic reindexig for %s not supported", indexName);
      return;
    }
    Subsection s = determineConfigSubsection(indexName);
    boolean runOnStartup =
        cfg.getBoolean(s.section, s.subsection, "runOnStartup", defaultRunOnStartup(indexName));
    if (runOnStartup) {
      indexer.run();
    }

    boolean isEnabled =
        cfg.getBoolean(s.section, s.subsection, "enabled", defaultIsEnabled(indexName));
    if (!isEnabled) {
      logger.atWarning().log("index.scheduledIndexer for %s is disabled", indexName);
      return;
    }

    Schedule schedule =
        ScheduleConfig.builder(cfg, s.section)
            .setSubsection(s.subsection)
            .buildSchedule()
            .orElseGet(() -> Schedule.createOrFail(TimeUnit.MINUTES.toMillis(5), "00:00"));
    queue.scheduleAtFixedRate(indexer, schedule);
  }

  private Subsection determineConfigSubsection(String indexName) {
    if (!"groups".equals(indexName)) {
      return new Subsection("scheduledIndexer", indexName);
    }
    Set<String> scheduledIndexerConfig = cfg.getSubsections("scheduledIndexer");
    if (scheduledIndexerConfig.contains("groups")) {
      return new Subsection("scheduledIndexer", "groups");
    }
    return new Subsection("index", "scheduledIndexer");
  }

  private boolean defaultRunOnStartup(String indexName) {
    return isGroupsIndexOnReplica(indexName);
  }

  private boolean defaultIsEnabled(String indexName) {
    return isGroupsIndexOnReplica(indexName);
  }

  private boolean isGroupsIndexOnReplica(String indexName) {
    return "groups".equals(indexName) && isReplica;
  }

  private static class Subsection {
    final String section;
    final String subsection;

    Subsection(String section, String subsection) {
      this.section = section;
      this.subsection = subsection;
    }
  }

  @Override
  public void stop() {
    // handled by WorkQueue.stop() already
  }
}
