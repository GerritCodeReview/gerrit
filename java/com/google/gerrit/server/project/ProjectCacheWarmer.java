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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.ProjectLoadExecutor;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ProjectCacheWarmer implements LifecycleListener {
  private static final Logger log = LoggerFactory.getLogger(ProjectCacheWarmer.class);

  private final ExecutorService executor;
  private final ProjectCache cache;

  @Inject
  ProjectCacheWarmer(@ProjectLoadExecutor @Nullable ExecutorService executor, ProjectCache cache) {
    this.executor = executor;
    this.cache = cache;
  }

  @Override
  public void start() {
    if (executor == null) {
      return;
    }
    Thread scheduler =
        new Thread(
            () -> {
              for (Project.NameKey name : cache.all()) {
                executor.execute(() -> cache.get(name));
              }
              executor.shutdown();
              try {
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
                log.info("Finished loading project cache");
              } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for project cache to load");
              }
            });
    scheduler.setName("ProjectCacheWarmer");
    scheduler.setDaemon(true);

    log.info("Loading project cache");
    scheduler.start();
  }

  @Override
  public void stop() {}
}
