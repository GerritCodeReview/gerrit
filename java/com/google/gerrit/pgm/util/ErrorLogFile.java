// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.pgm.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.common.FileUtil;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.util.SystemLog;
import com.google.gerrit.util.logging.LogTimestampFormatter;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.ThresholdFilter;
import org.apache.logging.log4j.core.layout.JsonLayout;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.eclipse.jgit.lib.Config;

public class ErrorLogFile {
  static final String LOG_NAME = "error_log";
  static final String JSON_SUFFIX = ".json";

  public static void errorOnlyConsole() {
    final LoggerContext context = (LoggerContext) LogManager.getContext(false);
    context.reconfigure();

    Layout<? extends Serializable> layout =
        PatternLayout.newBuilder().withPattern("%-5p %c %x: %m%n").build();
    final ConsoleAppender dst =
        ConsoleAppender.newBuilder()
            .withLayout(layout)
            .withName("Console")
            .setTarget(ConsoleAppender.Target.SYSTEM_ERR)
            .setFilter(ThresholdFilter.createFilter(Level.ERROR, null, null))
            .build();
    dst.start();

    LoggerContext ctx = LoggerContext.getContext(false);
    Configuration config = ctx.getConfiguration();
    LoggerConfig root = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
    root.removeAppender(LOG_NAME);
    root.addAppender(dst, null, null);
    ctx.updateLoggers();
  }

  public static LifecycleListener start(Path sitePath, Config config, boolean consoleLog)
      throws IOException {
    Path logdir =
        FileUtil.mkdirsOrDie(new SitePaths(sitePath).logs_dir, "Cannot create log directory");
    if (SystemLog.shouldConfigure()) {
      initLogSystem(logdir, config, consoleLog);
    }

    return new LifecycleListener() {
      @Override
      public void start() {}

      @Override
      public void stop() {
        LogManager.shutdown();
      }
    };
  }

<<<<<<< PATCH SET (e78401 Migrate to log4j2)
  private static void initLogSystem(Path logdir, Config config) {
    LoggerContext ctx = LoggerContext.getContext(false);
    Configuration configs = ctx.getConfiguration();
    LoggerConfig root = configs.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
    root.removeAppender(LOG_NAME);
=======
  private static void initLogSystem(Path logdir, Config config, boolean consoleLog) {
    Logger root = LogManager.getRootLogger();
    root.removeAllAppenders();
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')

    PatternLayout errorLogLayout =
        new PatternLayout(
            "[%d{" + LogTimestampFormatter.TIMESTAMP_FORMAT + "}] [%t] %-5p %c %x: %m%n");

    if (consoleLog) {
      ConsoleAppender dst = new ConsoleAppender();
      dst.setLayout(errorLogLayout);
      dst.setTarget("System.err");
      dst.setThreshold(Level.INFO);
      dst.activateOptions();

      root.addAppender(dst);
    }

    boolean json = config.getBoolean("log", "jsonLogging", false);
    boolean text = config.getBoolean("log", "textLogging", true) || !(json || consoleLog);
    boolean rotate = config.getBoolean("log", "rotate", true);
    boolean jsonCompact = config.getBoolean("log", "jsonCompact", false);

    if (text) {
<<<<<<< PATCH SET (e78401 Migrate to log4j2)
      Layout<? extends Serializable> layout =
          PatternLayout.newBuilder().withPattern("[%d{" + LogTimestampFormatter.TIMESTAMP_FORMAT + "}] [%t] %-5p %c %x: %m%n").build();
      root.addAppender(SystemLog.createAppender(logdir, LOG_NAME, layout, rotate), null, null);
=======
      root.addAppender(SystemLog.createAppender(logdir, LOG_NAME, errorLogLayout, rotate));
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')
    }

    if (json) {
<<<<<<< PATCH SET (e78401 Migrate to log4j2)
      Boolean enableReverseDnsLookup =
          config.getBoolean("gerrit", null, "enableReverseDnsLookup", false);

      final JsonLayout layout =
          JsonLayout.newBuilder()
              .setLocationInfo(true)
              .setProperties(true)
              .setPropertiesAsList(false)
              .setComplete(false)
              .setCompact(jsonCompact)
              .setEventEol(true)
              .setCharset(UTF_8)
              .setIncludeStacktrace(true)
              .build();
=======
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')
      root.addAppender(
<<<<<<< PATCH SET (e78401 Migrate to log4j2)
          SystemLog.createAppender(logdir, LOG_NAME + JSON_SUFFIX, layout, rotate), null, null);
=======
          SystemLog.createAppender(
              logdir, LOG_NAME + JSON_SUFFIX, new ErrorLogJsonLayout(), rotate));
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')
    }

    ctx.updateLoggers();
  }
}
