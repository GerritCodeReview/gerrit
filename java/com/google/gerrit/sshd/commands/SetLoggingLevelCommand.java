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
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.kohsuke.args4j.Argument;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(
  name = "set-level",
  description = "Change the level of loggers",
  runsAt = MASTER_OR_SLAVE
)
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
    RESET,
  }

  @Argument(index = 0, required = true, metaVar = "LEVEL", usage = "logging level to set to")
  private LevelOption level;

  @Argument(index = 1, required = false, metaVar = "NAME", usage = "used to match loggers")
  private String name;

  @SuppressWarnings("unchecked")
  @Override
  protected void run() throws MalformedURLException {
    if (level == LevelOption.RESET) {
      reset();
    } else {
      Logger logger = LogManager.getLogger();
      if (name == null) {
        setAllLogLevel(Level.toLevel(level.name()));
      } else {
        if (name.endsWith(".")) {
          if (logger.getName().contains(name.replaceAll("\\.$", ""))) {
            setLogLevel(name, Level.toLevel(level.name()));
          }
        } else if (name.endsWith(".*")) {
          if (logger.getName().contains(name.replaceAll("\\.\\*$", ""))) {
            setLogLevel(name, Level.toLevel(level.name()));
          }
        } else {
          if (logger.getName().contains(name)) {
            setLogLevel(name, Level.toLevel(level.name()));
          }
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static void reset() throws MalformedURLException {
    final LoggerContext context = (LoggerContext) LogManager.getContext(false);
    context.reconfigure();
  }

  private static void setLogLevel(String name, Level level) {
    if (name.endsWith(".")) {
      Configurator.setAllLevels(name.replaceAll("\\.$", ""), level);
    } else if (name.endsWith(".*")) {
      Configurator.setAllLevels(name.replaceAll("\\.\\*$", ""), level);
    } else {
      Map<String, Level> map = new HashMap<>();
      map.put(name, level);
      Configurator.setLevel(map);
    }
  }

  private static void setAllLogLevel(Level level) {
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    Configuration config = ctx.getConfiguration();
    for (LoggerConfig loggerConfig : config.getLoggers().values()) {
      loggerConfig.setLevel(level);
    }
    ctx.updateLoggers();
  }
}
