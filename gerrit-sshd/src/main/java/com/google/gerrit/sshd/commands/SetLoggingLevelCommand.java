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

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.util.Enumeration;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(name = "set-logging-level", description = "Change the level of loggers",
  runsAt = MASTER_OR_SLAVE)
public class SetLoggingLevelCommand extends SshCommand {
  private static final String LOG_CONFIGURATION = "log4j.properties";

  private static enum LEVEL {
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
  private LEVEL level;

  @Option(name = "--logger", aliases = "-l", metaVar = "LOGGER", usage = "loggers to change")
  private String regex;

  @Override
  protected void run() {
    if (regex == null) {
      regex = "^*.*$";
    }
    switch (level) {
      case RESET:
        reset();
        break;
      default:
        setLoggingLevel(Level.toLevel(level.name()));
        break;
    }
  }

  private void reset() {
    LogManager.resetConfiguration();
    PropertyConfigurator.configure(getClass().getClassLoader().getResource(
        LOG_CONFIGURATION));
  }

  @SuppressWarnings("unchecked")
  private void setLoggingLevel(Level level) {
    for (Enumeration<Logger> logger = LogManager.getCurrentLoggers();
        logger.hasMoreElements();) {
      Logger log = logger.nextElement();
      if (log.getName().matches(regex)) {
        log.setLevel(level);
      }
    }
  }
}
