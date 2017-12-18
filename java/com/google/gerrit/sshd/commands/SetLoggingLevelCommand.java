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
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
//import org.apache.log4j.LogManager;
import ch.qos.logback.classic.Logger;
import org.kohsuke.args4j.Argument;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.Level;
//import org.apache.log4j.PropertyConfigurator;
import ch.qos.logback.core.util.Loader;
import org.kohsuke.args4j.Argument;
import ch.qos.logback.classic.joran.JoranConfigurator;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(
  name = "set-level",
  description = "Change the level of loggers",
  runsAt = MASTER_OR_SLAVE
)
public class SetLoggingLevelCommand extends SshCommand {
  private static final String LOG_CONFIGURATION = "logback.xml";
  private static final String JAVA_OPTIONS_LOG_CONFIG = "logback.configurationFile";

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
      /*for (Enumeration<Logger> logger = LogManager.getCurrentLoggers();
          logger.hasMoreElements();
          ) {
        Logger log = logger.nextElement();
        if (name == null || log.getName().contains(name)) {
          log.setLevel(Level.toLevel(level.name()));
        }
      }*/
      LoggerContext loggerContext = (LoggerContext) context;
      List<Logger> loggerList = loggerContext.getLoggerList();
      for (Logger l : loggerList) {
        if (name == null || l.getName().contains(name)) {
          l.setLevel(l.getEffectiveLevel().toString());
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static void reset() throws MalformedURLException {
    /*for (Enumeration<Logger> logger = LogManager.getCurrentLoggers(); logger.hasMoreElements(); ) {
      logger.nextElement().setLevel(null);
    }*/
    LoggerContext loggerContext = (LoggerContext) context;
    List<Logger> loggerList = loggerContext.getLoggerList();
    for (Logger l : loggerList) {
      l.setLevel(null);
    }

    String path = System.getProperty(JAVA_OPTIONS_LOG_CONFIG);
    if (Strings.isNullOrEmpty(path)) {
      JoranConfigurator configurator = new JoranConfigurator();
      configurator.setContext(lc);
      configurator.doConfigure(Loader.getResource(LOG_CONFIGURATION));
    } else {
      JoranConfigurator configurator = new JoranConfigurator();
      configurator.setContext(lc);
      configurator.doConfigure(new URL(path));
    }
  }
}
