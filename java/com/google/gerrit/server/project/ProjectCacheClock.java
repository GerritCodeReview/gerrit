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
import com.google.gerrit.config.ConfigUtil;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.jgit.lib.Config;

/** Ticks periodically to force refresh events for {@link ProjectCacheImpl}. */
@Singleton
public class ProjectCacheClock implements LifecycleListener {
  private final Config serverConfig;

  private final AtomicLong generation = new AtomicLong();

  private ScheduledExecutorService executor;

  @Inject
  public ProjectCacheClock(@GerritServerConfig Config serverConfig) {
    this.serverConfig = serverConfig;
  }

  @Override
  public void start() {
    long checkFrequencyMillis = checkFrequency(serverConfig);

    if (checkFrequencyMillis == Long.MAX_VALUE) {
      // Start with generation 1 (to avoid magic 0 below).
      // Do not begin background thread, disabling the clock.
      generation.set(1);
    } else if (10 < checkFrequencyMillis) {
      // Start with generation 1 (to avoid magic 0 below).
      generation.set(1);
      executor =
          Executors.newScheduledThreadPool(
              1,
              new ThreadFactoryBuilder()
                  .setNameFormat("ProjectCacheClock-%d")
                  .setDaemon(true)
                  .setPriority(Thread.MIN_PRIORITY)
                  .build());
      @SuppressWarnings("unused") // Runnable already handles errors
      Future<?> possiblyIgnoredError =
          executor.scheduleAtFixedRate(
              () -> {
                generation.incrementAndGet();
              },
              checkFrequencyMillis,
              checkFrequencyMillis,
              TimeUnit.MILLISECONDS);
    } else {
      // Magic generation 0 triggers ProjectState to always
      // check on each needsRefresh() request we make to it.
      generation.set(0);
    }
  }

  @Override
  public void stop() {
    if (executor != null) {
      executor.shutdown();
    }
  }

  long read() {
    return generation.get();
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
