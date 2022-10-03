// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

public class PeriodicProjectListCacheWarmer implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  protected final ProjectCache cache;

  @Inject
  PeriodicProjectListCacheWarmer(ProjectCache cache) {
    this.cache = cache;
  }

  protected static class LifeCycle implements LifecycleListener {
    protected final Config config;
    protected final WorkQueue queue;
    protected final PeriodicProjectListCacheWarmer runner;

    @Inject
    LifeCycle(
        @GerritServerConfig Config config, WorkQueue queue, PeriodicProjectListCacheWarmer runner) {
      this.config = config;
      this.queue = queue;
      this.runner = runner;
    }

    @Override
    public void start() {
      if (config.getBoolean("cache", ProjectCacheImpl.CACHE_LIST, "loadOnStartup", false)) {
        runner.run();
      }

      boolean isEnabled =
          config.getBoolean("cache", ProjectCacheImpl.CACHE_LIST, "loadPeriodically", false);
      if (!isEnabled) {
        logger.atWarning().log("cache.project_list.loadPeriodically is disabled");
        return;
      }

      ScheduleConfig.Schedule schedule =
          ScheduleConfig.builder(config, "cache")
              .setSubsection(ProjectCacheImpl.CACHE_LIST)
              .buildSchedule()
              .orElseGet(
                  () ->
                      ScheduleConfig.Schedule.createOrFail(TimeUnit.MINUTES.toMillis(1), "00:00"));
      queue.scheduleAtFixedRate(runner, schedule);
    }

    @Override
    public void stop() {}
  }

  @Override
  public void run() {
    logger.atFine().log("Loading project_list cache");
    cache.all();
    logger.atFine().log("Finished loading project_list cache");
  }
}
