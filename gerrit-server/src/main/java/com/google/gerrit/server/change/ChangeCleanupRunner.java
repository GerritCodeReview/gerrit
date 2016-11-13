// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.gerrit.server.config.ScheduleConfig.MISSING_CONFIG;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.ChangeCleanupConfig;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runnable to enable scheduling change cleanups to run periodically */
public class ChangeCleanupRunner implements Runnable {
  private static final Logger log = LoggerFactory.getLogger(ChangeCleanupRunner.class);

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      listener().to(Lifecycle.class);
    }
  }

  static class Lifecycle implements LifecycleListener {
    private final WorkQueue queue;
    private final ChangeCleanupRunner runner;
    private final ChangeCleanupConfig cfg;

    @Inject
    Lifecycle(WorkQueue queue, ChangeCleanupRunner runner, ChangeCleanupConfig cfg) {
      this.queue = queue;
      this.runner = runner;
      this.cfg = cfg;
    }

    @Override
    public void start() {
      ScheduleConfig scheduleConfig = cfg.getScheduleConfig();
      long interval = scheduleConfig.getInterval();
      long delay = scheduleConfig.getInitialDelay();
      if (delay == MISSING_CONFIG && interval == MISSING_CONFIG) {
        log.info("Ignoring missing changeCleanup schedule configuration");
      } else if (delay < 0 || interval <= 0) {
        log.warn(
            String.format(
                "Ignoring invalid changeCleanup schedule configuration: %s", scheduleConfig));
      } else {
        queue.getDefaultQueue().scheduleAtFixedRate(runner, delay, interval, TimeUnit.MILLISECONDS);
      }
    }

    @Override
    public void stop() {
      // handled by WorkQueue.stop() already
    }
  }

  private final OneOffRequestContext oneOffRequestContext;
  private final AbandonUtil abandonUtil;

  @Inject
  ChangeCleanupRunner(OneOffRequestContext oneOffRequestContext, AbandonUtil abandonUtil) {
    this.oneOffRequestContext = oneOffRequestContext;
    this.abandonUtil = abandonUtil;
  }

  @Override
  public void run() {
    log.info("Running change cleanups.");
    try (ManualRequestContext ctx = oneOffRequestContext.open()) {
      abandonUtil.abandonInactiveOpenChanges();
    } catch (OrmException e) {
      log.error("Failed to cleanup changes.", e);
    }
  }

  @Override
  public String toString() {
    return "change cleanup runner";
  }
}
