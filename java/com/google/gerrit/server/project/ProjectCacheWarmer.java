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

package com.google.gerrit.server.project;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.logging.LoggingContextAwareExecutorService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

@Singleton
public class ProjectCacheWarmer implements LifecycleListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Config config;
  private final ProjectCache cache;

  @Inject
  ProjectCacheWarmer(@GerritServerConfig Config config, ProjectCache cache) {
    this.config = config;
    this.cache = cache;
  }

  @Override
  public void start() {
    int cpus = Runtime.getRuntime().availableProcessors();
    if (config.getBoolean("cache", "projects", "loadOnStartup", false)) {
      ExecutorService pool =
          new LoggingContextAwareExecutorService(
              new ScheduledThreadPoolExecutor(
                  config.getInt("cache", "projects", "loadThreads", cpus),
                  new ThreadFactoryBuilder().setNameFormat("ProjectCacheLoader-%d").build()));
      Thread scheduler =
          new Thread(
              () -> {
                for (Project.NameKey name : cache.all()) {
                  pool.execute(
                      () ->
                          cache
                              .get(name)
                              .orElseThrow(
                                  () ->
                                      new IllegalStateException(
                                          "race while traversing projects. got "
                                              + name
                                              + " when loading all projects, but can't load it now")));
                }
                pool.shutdown();
                try {
                  pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
                  logger.atInfo().log("Finished loading project cache");
                } catch (InterruptedException e) {
                  logger.atWarning().log("Interrupted while waiting for project cache to load");
                }
              });
      scheduler.setName("ProjectCacheWarmer");
      scheduler.setDaemon(true);

      logger.atInfo().log("Loading project cache");
      scheduler.start();
    }
  }

  @Override
  public void stop() {}
}
