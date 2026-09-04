// Copyright (C) 2026 The Android Open Source Project
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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.ScheduleConfig.Schedule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

@Singleton
public class WorkQueueSnapshotConfig {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static final String SECTION = "workQueueSnapshot";
  static final String KEY_INTERVAL = "interval";
  static final String KEY_START_TIME = "startTime";
  static final String DEFAULT_START_TIME = "00:00";

  private final Optional<Schedule> schedule;

  @Inject
  WorkQueueSnapshotConfig(@GerritServerConfig Config cfg) {
    this.schedule = createSchedule(cfg);
  }

  public Optional<Schedule> getSchedule() {
    return schedule;
  }

  private static Optional<Schedule> createSchedule(Config cfg) {
    if (cfg.getString(SECTION, null, KEY_INTERVAL) == null) {
      return Optional.empty();
    }

    long intervalMs;
    try {
      intervalMs =
          ConfigUtil.getTimeUnit(cfg, SECTION, null, KEY_INTERVAL, -1L, TimeUnit.MILLISECONDS);
    } catch (IllegalArgumentException e) {
      logger.atSevere().withCause(e).log("Invalid [%s.%s]", SECTION, KEY_INTERVAL);
      return Optional.empty();
    }
    if (intervalMs <= 0) {
      logger.atSevere().log("[%s.%s] must be greater than 0", SECTION, KEY_INTERVAL);
      return Optional.empty();
    }

    String startTime = cfg.getString(SECTION, null, KEY_START_TIME);
    if (startTime == null) {
      startTime = DEFAULT_START_TIME;
    }
    try {
      return Optional.of(Schedule.createOrFail(intervalMs, startTime));
    } catch (IllegalArgumentException e) {
      logger.atSevere().withCause(e).log(
          "Invalid schedule for [%s] (interval=%d ms, startTime=%s)",
          SECTION, intervalMs, startTime);
      return Optional.empty();
    }
  }
}
