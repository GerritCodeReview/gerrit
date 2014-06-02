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
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GcConfig;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Runnable to enable scheduling gc to run periodically */
public class GarbageCollectionRunner implements Runnable {
  public static final String LOG_NAME = "gc_log";
  private static final Logger gcLog = LoggerFactory.getLogger(LOG_NAME);

  public static Module module() {
    return new LifecycleModule() {

      @Override
      protected void configure() {
        listener().to(Lifecycle.class);
      }
    };
  }

  static class Lifecycle implements LifecycleListener {
    private final WorkQueue queue;
    private final GarbageCollectionRunner gcRunner;
    private final GcConfig gcConfig;
    private ScheduledFuture<?> scheduledTask;

    @Inject
    Lifecycle(final WorkQueue queue, final GarbageCollectionRunner gcRunner,
        GcConfig config) {
      this.queue = queue;
      this.gcRunner = gcRunner;
      this.gcConfig = config;
      this.scheduledTask = null;
    }

    @Override
    public void start() {
      ScheduleConfig scheduleConfig = gcConfig.getScheduleConfig();
      long interval = scheduleConfig.getInterval();
      long delay = scheduleConfig.getInitialDelay();
      if (delay == -1L && interval == -1L) {
        gcLog.info("Ignoring invalid or missing gc schedule configuration");
      } else {
        scheduledTask =
            queue.getDefaultQueue().scheduleWithFixedDelay(gcRunner, delay,
                interval, TimeUnit.MILLISECONDS);
      }
    }

    @Override
    public void stop() {
      if (scheduledTask != null) {
        scheduledTask.cancel(true);
      }
    }
  }

  private final GarbageCollection.Factory garbageCollectionFactory;
  private final ProjectCache projectCache;

  @Inject
  GarbageCollectionRunner(
      final GarbageCollection.Factory garbageCollectionFactory,
      final ProjectCache projectCache) {
    this.garbageCollectionFactory = garbageCollectionFactory;
    this.projectCache = projectCache;
  }

  @Override
  public void run() {
    gcLog.info("Triggering gc to run on all repositories");
    garbageCollectionFactory.create().run(
        Lists.newArrayList(projectCache.all()));
  }

  @Override
  public String toString() {
    return "GC runner";
  }
}
