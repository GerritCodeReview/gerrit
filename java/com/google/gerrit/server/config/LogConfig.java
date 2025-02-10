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

package com.google.gerrit.server.config;

import com.google.common.flogger.FluentLogger;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

@Singleton
public class LogConfig {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String CONFIG_LOG_SECTION = "log";

  private final boolean jsonLogging;
  private final boolean textLogging;
  private final boolean compress;
  private final boolean rotate;
  private final Duration timeToKeep;

  @Inject
  public LogConfig(@GerritServerConfig Config cfg) {
    jsonLogging = cfg.getBoolean(CONFIG_LOG_SECTION, "jsonLogging", false);
    textLogging = cfg.getBoolean(CONFIG_LOG_SECTION, "textLogging", true) || !jsonLogging;
    compress = cfg.getBoolean(CONFIG_LOG_SECTION, "compress", true);
    rotate = cfg.getBoolean(CONFIG_LOG_SECTION, "rotate", true);
    timeToKeep = getTimeToKeep(cfg);
  }

  public boolean isJsonLogging() {
    return jsonLogging;
  }

  public boolean isTextLogging() {
    return textLogging;
  }

  public boolean shouldCompress() {
    return compress;
  }

  public boolean shouldRotate() {
    return rotate;
  }

  public Duration getTimeToKeep() {
    return timeToKeep;
  }

  private Duration getTimeToKeep(Config config) {
    try {
      return Duration.ofDays(
          ConfigUtil.getTimeUnit(config, "log", null, "timeToKeep", -1, TimeUnit.DAYS));
    } catch (IllegalArgumentException e) {
      logger.atWarning().withCause(e).log(
          "Illegal duration value for log deletion. Disabling log deletion.");
      return Duration.ofDays(-1L);
    }
  }
}
