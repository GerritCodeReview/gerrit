// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.config.ScheduleConfig.Schedule;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.notedb.DeleteZombieCommentsRefs;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;

@Singleton
public class DraftCommentsCleanupRunner implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String SECTION = "draftCommentsCleanup";

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      listener().to(Lifecycle.class);
      bind(DraftCommentsCleanupRunner.class);
    }
  }

  static class Lifecycle implements LifecycleListener {

    private final WorkQueue queue;
    private final DraftCommentsCleanupRunner runner;
    private final Config cfg;

    @Inject
    Lifecycle(WorkQueue queue, DraftCommentsCleanupRunner runner, @GerritServerConfig Config cfg) {
      this.queue = queue;
      this.runner = runner;
      this.cfg = cfg;
    }

    @Override
    public void start() {
      Optional<Schedule> schedule = ScheduleConfig.createSchedule(cfg, SECTION);
      schedule.ifPresent(s -> queue.scheduleAtFixedRate(runner, s));
    }

    @Override
    public void stop() {}
  }

  private DeleteZombieCommentsRefs.Factory factory;

  @Inject
  DraftCommentsCleanupRunner(DeleteZombieCommentsRefs.Factory factory) {
    this.factory = factory;
  }

  @Override
  public void run() {
    try {
      logger.atInfo().log("Starting draft comments cleanup");
      factory.create(100).execute();
      logger.atInfo().log("Finished draft comments cleanup");
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Draft comments cleanup error");
    }
  }
}
