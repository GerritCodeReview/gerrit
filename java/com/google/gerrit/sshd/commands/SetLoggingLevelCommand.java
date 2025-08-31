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
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;
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
  private static Level ORIGINAL_ROOT_LEVEL = null;

  @Argument(index = 0, required = true, metaVar = "LEVEL", usage = "logging level to set to")
  private LevelOption level;

  @Argument(index = 1, required = false, metaVar = "NAME", usage = "used to match loggers")
  private String name;

  @Override
  protected void run() {
    enableGracefulStop();

    copyOriginalLevels();

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

    if (name == null) {
      Configurator.setAllLevels("", newLevel);
    } else {
      LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
      ctx.getLoggerRegistry()
          .getLoggers()
          .forEach(
              logger -> {
                String loggerName = logger.getName();
                if (loggerName.contains(name)) {
                  Configurator.setLevel(loggerName, newLevel);
                }
              });
    }
  }

  private static void copyOriginalLevels() {
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);

    if (ORIGINAL_ROOT_LEVEL == null) {
      ORIGINAL_ROOT_LEVEL = ctx.getConfiguration().getRootLogger().getLevel();
    }

    ctx.getLoggerRegistry()
        .getLoggers()
        .forEach(logger -> ORIGINAL_LEVELS.putIfAbsent(logger.getName(), logger.getLevel()));
  }

  private static void reset() {
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    org.apache.logging.log4j.core.config.Configuration config = ctx.getConfiguration();

    ctx.getLoggerRegistry()
        .getLoggers()
        .forEach(
            logger -> {
              Level original = ORIGINAL_LEVELS.get(logger.getName());
              if (original != null) {
                Configurator.setLevel(logger.getName(), original);
              }
            });

    if (ORIGINAL_ROOT_LEVEL != null) {
      config.getRootLogger().setLevel(ORIGINAL_ROOT_LEVEL);
    }

    ctx.updateLoggers();
  }
}
