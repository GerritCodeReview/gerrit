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
import com.google.gerrit.server.config.ChangeCleanupConfig;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class ChangeCleanupLifecycle implements LifecycleListener {
  private static final Logger log = LoggerFactory
      .getLogger(ChangeCleanupLifecycle.class);

  private final WorkQueue queue;
  private final ChangeCleanupRunner runner;
  private final ChangeCleanupConfig cfg;

  @Inject
  ChangeCleanupLifecycle(WorkQueue queue,
      ChangeCleanupRunner runner,
      ChangeCleanupConfig cfg) {
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
      log.warn(String.format(
          "Ignoring invalid changeCleanup schedule configuration: %s",
          scheduleConfig));
    } else {
      queue.getDefaultQueue().scheduleAtFixedRate(runner, delay,
          interval, TimeUnit.MILLISECONDS);
    }
  }

  @Override
  public void stop() {
    // handled by WorkQueue.stop() already
  }
}
