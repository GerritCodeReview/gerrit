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

import com.google.common.base.Strings;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import java.net.MalformedURLException;
import java.net.URI;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.kohsuke.args4j.Argument;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(
    name = "set-level",
    description = "Change the level of loggers",
    runsAt = MASTER_OR_SLAVE)
public class SetLoggingLevelCommand extends SshCommand {
  private static final String LOG_CONFIGURATION = "log4j2.xml";
  private static final String JAVA_OPTIONS_LOG_CONFIG = "log4j.configurationFile";

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

  @Override
  protected void run() throws MalformedURLException {
    enableGracefulStop();
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    Configuration cfg = ctx.getConfiguration();

    if (level == LevelOption.RESET) {
      reset(ctx, cfg);
    } else {
      Level newLevel = Level.valueOf(level.name());
      if (name == null) {
        // Apply to all existing loggers
        cfg.getLoggers().values().forEach(lc -> lc.setLevel(newLevel));
      } else {
        LoggerConfig loggerConfig = cfg.getLoggerConfig(name);
        if (!loggerConfig.getName().equals(name)) {
          // Create a new LoggerConfig for the requested name
          loggerConfig = new LoggerConfig(name, newLevel, true);
          cfg.addLogger(name, loggerConfig);
        } else {
          loggerConfig.setLevel(newLevel);
        }
      }
      ctx.updateLoggers();
    }
  }

  private static void reset(LoggerContext ctx, Configuration cfg) throws MalformedURLException {
    // Remove all dynamic loggers (not the root logger)
    for (String loggerName : cfg.getLoggers().keySet().toArray(new String[0])) {
      if (!loggerName.isEmpty()) {
        cfg.removeLogger(loggerName);
      }
    }

    // Reload default configuration
    String path = System.getProperty(JAVA_OPTIONS_LOG_CONFIG);
    if (Strings.isNullOrEmpty(path)) {
      Configurator.initialize("default", LOG_CONFIGURATION);
    } else {
      Configurator.initialize("custom", null, URI.create(path).toURL().toString());
    }

    ctx.updateLoggers();
  }
}
