// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.GcConfig;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.config.ScheduleConfig.Schedule;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runnable to enable scheduling gc to run periodically */
public class GarbageCollectionRunner implements Runnable {
  private static final Logger gcLog = LoggerFactory.getLogger(GarbageCollection.LOG_NAME);
  private static final Logger log = LoggerFactory.getLogger(GarbageCollectionRunner.class);

  static class Lifecycle implements LifecycleListener {
    private final WorkQueue queue;
    private final GarbageCollectionRunner gcRunner;
    private final GcConfig gcConfig;

    @Inject
    Lifecycle(WorkQueue queue, GarbageCollectionRunner gcRunner, GcConfig config) {
      this.queue = queue;
      this.gcRunner = gcRunner;
      this.gcConfig = config;
    }

    @Override
    public void start() {
      ScheduleConfig scheduleConfig = gcConfig.getScheduleConfig();
      Optional<Schedule> schedule = scheduleConfig.schedule();
      if (!schedule.isPresent()) {
        log.info("Ignoring missing gc schedule configuration");
      } else if (schedule.get().initialDelay() < 0 || schedule.get().interval() <= 0) {
        log.warn(String.format("Ignoring invalid gc schedule configuration: %s", scheduleConfig));
      } else {
        @SuppressWarnings("unused")
        Future<?> possiblyIgnoredError =
            queue
                .getDefaultQueue()
                .scheduleAtFixedRate(
                    gcRunner,
                    schedule.get().initialDelay(),
                    schedule.get().interval(),
                    TimeUnit.MILLISECONDS);
      }
    }

    @Override
    public void stop() {
      // handled by WorkQueue.stop() already
    }
  }

  private final GarbageCollection.Factory garbageCollectionFactory;
  private final ProjectCache projectCache;

  @Inject
  GarbageCollectionRunner(
      GarbageCollection.Factory garbageCollectionFactory, ProjectCache projectCache) {
    this.garbageCollectionFactory = garbageCollectionFactory;
    this.projectCache = projectCache;
  }

  @Override
  public void run() {
    gcLog.info("Triggering gc on all repositories");
    garbageCollectionFactory.create().run(Lists.newArrayList(projectCache.all()));
  }

  @Override
  public String toString() {
    return "GC runner";
  }
}
