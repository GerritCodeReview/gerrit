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

import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.Loader;
import org.kohsuke.args4j.Argument;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(name = "set-level", description = "Change the level of loggers",
  runsAt = MASTER_OR_SLAVE)
public class SetLoggingLevelCommand extends SshCommand {
  private static final String LOG_CONFIGURATION = "log4j.properties";
  private static final String JAVA_OPTIONS_LOG_CONFIG = "log4j.configuration";

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

  @Argument(index = 1, required = false, metaVar = "LOGGER", usage = "loggers to change")
  private String logger = "^.*";

  @Override
  protected void run() throws MalformedURLException {
    switch (level) {
      case RESET:
        reset();
        break;
      default:
        if (logger.startsWith("^")) {
          setLevelUsingRegex(Level.toLevel(level.name()), logger);
        } else {
          LogManager.getLogger(logger).setLevel(Level.toLevel(level.name()));
        }
    }
  }

  @SuppressWarnings("unchecked")
  private static void reset() throws MalformedURLException {
    for (Enumeration<Logger> logger = LogManager.getCurrentLoggers();
        logger.hasMoreElements();) {
      logger.nextElement().setLevel(null);
    }

    if (Strings.isNullOrEmpty(System.getProperty(JAVA_OPTIONS_LOG_CONFIG))) {
      PropertyConfigurator.configure(Loader.getResource(LOG_CONFIGURATION));
    } else {
      PropertyConfigurator.configure(
          new URL(System.getProperty(JAVA_OPTIONS_LOG_CONFIG)));
    }
  }

  @SuppressWarnings("unchecked")
  private static void setLevelUsingRegex(Level level, String regex) {
    if (regex.startsWith("^")) {
      regex = regex.substring(1);
      if (regex.endsWith("$") && !regex.endsWith("\\$")) {
        regex = regex.substring(0, regex.length() - 1);
      }
    }
    RunAutomaton a = new RunAutomaton(new RegExp(regex).toAutomaton());
    for (Enumeration<Logger> logger = LogManager.getCurrentLoggers();
        logger.hasMoreElements();) {
      Logger log = logger.nextElement();
      if (a.run(log.getName())) {
        log.setLevel(level);
      }
    }
  }
}
