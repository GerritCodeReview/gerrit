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

package com.google.gerrit.sshd.commands;

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.kohsuke.args4j.Argument;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(
    name = "set-level",
    description = "Change the level of loggers",
    runsAt = MASTER_OR_SLAVE)
public class SetLoggingLevelCommand extends SshCommand {

  private enum LevelOption {
    ALL,
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL,
    OFF,
    RESET
  }

  private static final Map<String, Level> ORIGINAL_LEVELS = new HashMap<>();
  private static final Set<String> ORIGINAL_LOGGER_NAMES = new HashSet<>();
  private static Level ORIGINAL_ROOT_LEVEL = null;
  private static boolean initialized = false;

  @Argument(index = 0, required = true, metaVar = "LEVEL", usage = "logging level to set to")
  private LevelOption level;

  @Argument(index = 1, required = false, metaVar = "NAME", usage = "used to match loggers")
  private String name;

  @Override
  protected void run() {
    enableGracefulStop();

    if (!initialized) {
      copyOriginalLevels();
      initialized = true;
    }

    if (level == LevelOption.RESET) {
      reset();
      return;
    }

    final Level newLevel;
    try {
      newLevel = Level.valueOf(level.name());
    } catch (IllegalArgumentException e) {
      stderr.println("Unknown logging level: " + level);
      return;
    }

    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    org.apache.logging.log4j.core.config.Configuration config = ctx.getConfiguration();

    for (Map.Entry<String, LoggerConfig> entry : config.getLoggers().entrySet()) {
      String loggerName = entry.getKey();
      if (loggerName.isEmpty()) continue;
      if (name == null || loggerName.contains(name)) {
        entry.getValue().setLevel(newLevel);
      }
    }

    ctx.getLoggerRegistry()
        .getLoggers()
        .forEach(
            logger -> {
              String loggerName = logger.getName();
              if (loggerName.isEmpty()) return;
              if (name == null || loggerName.contains(name)) {
                LoggerConfig lc = config.getLoggerConfig(loggerName);
                if (!lc.getName().equals(loggerName)) {
                  LoggerConfig newLc = new LoggerConfig(loggerName, newLevel, true);
                  config.addLogger(loggerName, newLc);
                } else {
                  lc.setLevel(newLevel);
                }
              }
            });

    ctx.updateLoggers();
  }

  private static void copyOriginalLevels() {
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    org.apache.logging.log4j.core.config.Configuration config = ctx.getConfiguration();

    ORIGINAL_ROOT_LEVEL = config.getRootLogger().getLevel();

    for (Map.Entry<String, LoggerConfig> e : config.getLoggers().entrySet()) {
      String loggerName = e.getKey();
      ORIGINAL_LOGGER_NAMES.add(loggerName);
      ORIGINAL_LEVELS.put(loggerName, e.getValue().getLevel());
    }
  }

  private static void reset() {
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    org.apache.logging.log4j.core.config.Configuration config = ctx.getConfiguration();

    for (LoggerConfig lc : config.getLoggers().values()) {
      lc.setLevel(null);
    }
    if (ORIGINAL_ROOT_LEVEL != null) {
      config.getRootLogger().setLevel(null);
    }

    for (Map.Entry<String, Level> e : ORIGINAL_LEVELS.entrySet()) {
      LoggerConfig lc = config.getLoggers().get(e.getKey());
      if (lc != null) {
        lc.setLevel(e.getValue());
      }
    }
    if (ORIGINAL_ROOT_LEVEL != null) {
      config.getRootLogger().setLevel(ORIGINAL_ROOT_LEVEL);
    }

    ctx.updateLoggers();
  }
}
