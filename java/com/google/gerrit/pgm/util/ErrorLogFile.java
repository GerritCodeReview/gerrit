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

import com.google.gerrit.common.FileUtil;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.LogConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.util.SystemLog;
import com.google.gerrit.util.logging.LogTimestampFormatter;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

public class ErrorLogFile {
  static final String LOG_NAME = "error_log";
  static final String JSON_SUFFIX = ".json";

  /** Configure console logging to only show errors */
  public static void errorOnlyConsole() {
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    LoggerConfig rootConfig = ctx.getConfiguration().getRootLogger();

    // Remove existing appenders
    rootConfig.getAppenders().values().forEach(a -> {
      rootConfig.removeAppender(a.getName());
      a.stop();
    });

    PatternLayout layout = PatternLayout.newBuilder()
        .withPattern("%-5p %c %x: %m%n")
        .build();

    ConsoleAppender consoleAppender = ConsoleAppender.newBuilder()
        .setName("stderr")
        .setTarget(ConsoleAppender.Target.SYSTEM_ERR)
        .setLayout(layout)
        .setFollow(true)
        .build();
    consoleAppender.start();

    rootConfig.addAppender(consoleAppender, Level.ERROR, null);
    ctx.updateLoggers();
  }

  /** Initialize logging system with file and optional console logging */
  public static LifecycleListener start(Path sitePath, LogConfig config, boolean consoleLog)
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

  private static void initLogSystem(Path logdir, LogConfig config, boolean consoleLog) {
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    LoggerConfig rootConfig = ctx.getConfiguration().getRootLogger();

    // Remove existing appenders
    rootConfig.getAppenders().values().forEach(a -> {
      rootConfig.removeAppender(a.getName());
      a.stop();
    });

    PatternLayout errorLogLayout = PatternLayout.newBuilder()
        .withPattern(
            "[%d{" + LogTimestampFormatter.TIMESTAMP_FORMAT + "}] [%t] %-5p %c %x: %m%n")
        .build();

    // Console appender
    if (consoleLog) {
      ConsoleAppender consoleAppender = ConsoleAppender.newBuilder()
          .setName("stderr")
          .setTarget(ConsoleAppender.Target.SYSTEM_ERR)
          .setLayout(errorLogLayout)
          .setFollow(true)
          .build();
      consoleAppender.start();
      rootConfig.addAppender(consoleAppender, Level.INFO, null);
    }

    boolean rotate = config.shouldRotate();

    // Text file appender
    if (config.isTextLogging() || !consoleLog) {
      Appender textAppender =
          SystemLog.createAppender(logdir, LOG_NAME, errorLogLayout, rotate);
      rootConfig.addAppender(textAppender, Level.INFO, null);
    }

    // JSON file appender
    if (config.isJsonLogging()) {
      Appender jsonAppender =
          SystemLog.createAppender(
              logdir, LOG_NAME + JSON_SUFFIX, new ErrorLogJsonLayout(), rotate);
      rootConfig.addAppender(jsonAppender, Level.INFO, null);
    }

    ctx.updateLoggers();
  }
}
