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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import java.time.Duration;
import org.eclipse.jgit.lib.Config;

public class PeriodicProjectListCacheWarmer implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class LifeCycle implements LifecycleListener {
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
      long interval = -1L;
      String intervalString = config.getString("cache", ProjectCacheImpl.CACHE_LIST, "interval");
      if (!"-1".equals(intervalString)) {
        long maxAge =
            config.getTimeUnit("cache", ProjectCacheImpl.CACHE_LIST, "maxAge", -1L, MILLISECONDS);
        interval =
            config.getTimeUnit(
                "cache",
                ProjectCacheImpl.CACHE_LIST,
                "interval",
                getHalfDuration(maxAge),
                MILLISECONDS);
      }

      if (interval == -1L) {
        logger.atWarning().log("project_list cache warmer is disabled");
        return;
      }

      String startTime = config.getString("cache", ProjectCacheImpl.CACHE_LIST, "startTime");
      if (startTime == null) {
        startTime = "00:00";
      }

      runner.run();
      queue.scheduleAtFixedRate(runner, ScheduleConfig.Schedule.createOrFail(interval, startTime));
    }

    @Override
    public void stop() {
      // handled by WorkQueue.stop() already
    }

    private long getHalfDuration(long duration) {
      if (duration < 0) {
        return duration;
      }
      return Duration.ofMillis(duration).dividedBy(2L).toMillis();
    }
  }

  protected final ProjectCache cache;

  @Inject
  PeriodicProjectListCacheWarmer(ProjectCache cache) {
    this.cache = cache;
  }

  @Override
  public void run() {
    logger.atFine().log("Loading project_list cache");
    cache.refreshProjectList();
    logger.atFine().log("Finished loading project_list cache");
  }
}
