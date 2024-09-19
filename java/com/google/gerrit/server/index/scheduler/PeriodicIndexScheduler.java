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
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.config.ScheduleConfig.Schedule;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.group.PeriodicGroupIndexer;
import com.google.inject.Inject;
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
  private final PeriodicGroupIndexer groupIndexer;

  @Inject
  PeriodicIndexScheduler(
      @GerritServerConfig Config cfg, WorkQueue queue, PeriodicGroupIndexer groupIndexer) {
    this.cfg = cfg;
    this.queue = queue;
    this.groupIndexer = groupIndexer;
  }

  @Override
  public void start() {
    boolean runOnStartup = cfg.getBoolean("index", "scheduledIndexer", "runOnStartup", true);
    if (runOnStartup) {
      groupIndexer.run();
    }

    boolean isEnabled = cfg.getBoolean("index", "scheduledIndexer", "enabled", true);
    if (!isEnabled) {
      logger.atWarning().log("index.scheduledIndexer is disabled");
      return;
    }

    Schedule schedule =
        ScheduleConfig.builder(cfg, "index")
            .setSubsection("scheduledIndexer")
            .buildSchedule()
            .orElseGet(() -> Schedule.createOrFail(TimeUnit.MINUTES.toMillis(5), "00:00"));
    queue.scheduleAtFixedRate(groupIndexer, schedule);
  }

  @Override
  public void stop() {
    // handled by WorkQueue.stop() already
  }
}
