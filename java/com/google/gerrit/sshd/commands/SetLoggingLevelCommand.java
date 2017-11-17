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
<<<<<<< PATCH SET (e78401 Migrate to log4j2)
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
=======
import java.net.URL;
import java.util.Collections;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.Loader;
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')
import org.kohsuke.args4j.Argument;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(
    name = "set-level",
    description = "Change the level of loggers",
    runsAt = MASTER_OR_SLAVE)
public class SetLoggingLevelCommand extends SshCommand {
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
<<<<<<< PATCH SET (e78401 Migrate to log4j2)
  protected void run() throws MalformedURLException, URISyntaxException {
=======
  protected void run() throws MalformedURLException {
    enableGracefulStop();
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')
    if (level == LevelOption.RESET) {
      reset();
    } else {
<<<<<<< PATCH SET (e78401 Migrate to log4j2)
      LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
      for (final Logger loggerConfig : ctx.getLoggers()) {
        if (name == null || loggerConfig.getName().contains(name)) {
          setLogLevel(loggerConfig.getName(), Level.toLevel(level.name()));
=======
      for (Logger logger : getCurrentLoggers()) {
        if (name == null || logger.getName().contains(name)) {
          logger.setLevel(Level.toLevel(level.name()));
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')
        }
      }

      ctx.updateLoggers();
    }
  }

<<<<<<< PATCH SET (e78401 Migrate to log4j2)
  @SuppressWarnings({"unchecked", "ReferenceEquality", "StringEquality"})
  private static void reset() throws MalformedURLException, URISyntaxException {
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    Configuration config = ctx.getConfiguration();

    for (final Logger loggerConfig : ctx.getLoggers()) {
      if (loggerConfig.getName() != LogManager.ROOT_LOGGER_NAME) {
        Configurator.setLevel(loggerConfig.getName(), null);
      }
=======
  private static void reset() throws MalformedURLException {
    for (Logger logger : getCurrentLoggers()) {
      logger.setLevel(null);
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')
    }

    String path = System.getProperty(JAVA_OPTIONS_LOG_CONFIG);
    if (Strings.isNullOrEmpty(path)) {
      getDefaultLoggers();
    } else {
      ctx.reconfigure();
    }

    ctx.updateLoggers();
  }

  private static void setLogLevel(String name, Level level) {
    Map<String, Level> map = new HashMap<>();
    map.put(name, level);
    Configurator.setLevel(map);
  }

  private static void getDefaultLoggers() {
    // Sets configs from log4j2.properties
    Configurator.setLevel("", Level.INFO);
    Configurator.setLevel("org.apache.mina", Level.WARN);
    Configurator.setLevel("org.apache.sshd.common", Level.WARN);
    Configurator.setLevel("org.apache.sshd.server", Level.WARN);
    Configurator.setLevel("org.apache.sshd.common.keyprovider.FileKeyPairProvider", Level.INFO);
    Configurator.setLevel("com.google.gerrit.sshd.GerritServerSession", Level.WARN);
    Configurator.setLevel("eu.medsea.mimeutil", Level.WARN);
    Configurator.setLevel("org.apache.xml", Level.WARN);
    Configurator.setLevel("org.openid4java", Level.WARN);
    Configurator.setLevel("org.openid4java.consumer.ConsumerManager", Level.FATAL);
    Configurator.setLevel("org.openid4java.discovery.Discovery", Level.ERROR);
    Configurator.setLevel("org.openid4java.server.RealmVerifier", Level.ERROR);
    Configurator.setLevel("org.openid4java.message.AuthSuccess", Level.ERROR);
    Configurator.setLevel("com.mchange.v2.c3p0", Level.WARN);
    Configurator.setLevel("com.mchange.v2.resourcepool", Level.WARN);
    Configurator.setLevel("com.mchange.v2.sql", Level.WARN);
    Configurator.setLevel("org.apache.http", Level.WARN);
  }

  @SuppressWarnings({"unchecked", "JdkObsolete"})
  private static Iterable<Logger> getCurrentLoggers() {
    return Collections.list(LogManager.getCurrentLoggers());
  }
}
