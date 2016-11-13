// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

/** Ticks periodically to force refresh events for {@link ProjectCacheImpl}. */
@Singleton
public class ProjectCacheClock {
  private volatile long generation;

  @Inject
  public ProjectCacheClock(@GerritServerConfig Config serverConfig) {
    this(checkFrequency(serverConfig));
  }

  public ProjectCacheClock(long checkFrequencyMillis) {
    if (checkFrequencyMillis == Long.MAX_VALUE) {
      // Start with generation 1 (to avoid magic 0 below).
      // Do not begin background thread, disabling the clock.
      generation = 1;
    } else if (10 < checkFrequencyMillis) {
      // Start with generation 1 (to avoid magic 0 below).
      generation = 1;
      ScheduledExecutorService executor =
          Executors.newScheduledThreadPool(
              1,
              new ThreadFactoryBuilder()
                  .setNameFormat("ProjectCacheClock-%d")
                  .setDaemon(true)
                  .setPriority(Thread.MIN_PRIORITY)
                  .build());
      executor.scheduleAtFixedRate(
          new Runnable() {
            @Override
            public void run() {
              // This is not exactly thread-safe, but is OK for our use.
              // The only thread that writes the volatile is this task.
              generation = generation + 1;
            }
          },
          checkFrequencyMillis,
          checkFrequencyMillis,
          TimeUnit.MILLISECONDS);
    } else {
      // Magic generation 0 triggers ProjectState to always
      // check on each needsRefresh() request we make to it.
      generation = 0;
    }
  }

  long read() {
    return generation;
  }

  private static long checkFrequency(Config serverConfig) {
    String freq = serverConfig.getString("cache", "projects", "checkFrequency");
    if (freq != null && ("disabled".equalsIgnoreCase(freq) || "off".equalsIgnoreCase(freq))) {
      return Long.MAX_VALUE;
    }
    return TimeUnit.MILLISECONDS.convert(
        ConfigUtil.getTimeUnit(
            serverConfig, "cache", "projects", "checkFrequency", 5, TimeUnit.MINUTES),
        TimeUnit.MINUTES);
  }
}
