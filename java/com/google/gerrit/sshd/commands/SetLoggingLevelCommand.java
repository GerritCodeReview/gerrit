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
  private static final String LOG_CONFIGURATION = "log4j2.xml";
  private static final String JAVA_OPTIONS_LOG_CONFIG = "log4j.configuration";

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
        setLogLeve(Level.toLevel(level.name()));
      } else if (logger.getName().contains(name)) {
        Map<String, Level> map = new HashMap<>();
        map.put(name, Level.toLevel(level.name()));
        Configurator.setLevel(map);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static void reset() throws MalformedURLException {
    setLogLeve(Level.INFO);
  }

  @SuppressWarnings("unchecked")
  private static void setLogLeve(Level level) {
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    Configuration config = ctx.getConfiguration();
    for (final LoggerConfig loggerConfig : config.getLoggers().values()) {
      loggerConfig.setLevel(level);
    }
    ctx.updateLoggers();
  }
}
