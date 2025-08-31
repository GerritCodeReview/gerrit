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
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

public class ErrorLogFile {
  static final String LOG_NAME = "error_log";
  static final String JSON_SUFFIX = ".json";

  private static final Object lock = new Object();
  private static boolean consoleReconfiguring = false;
  private static boolean logSystemReconfiguring = false;

  public static void errorOnlyConsole() {
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    reinstallConsoleOnly(ctx);
    ctx.addPropertyChangeListener(
        evt -> {
          if ("config".equals(evt.getPropertyName())) {
            reinstallConsoleOnly(ctx);
          }
        });
  }

  private static void reinstallConsoleOnly(LoggerContext ctx) {
    synchronized (lock) {
      if (consoleReconfiguring) {
        return;
      }
      consoleReconfiguring = true;
      try {
        LoggerConfig rootConfig = ctx.getConfiguration().getRootLogger();

        Appender existing = rootConfig.getAppenders().get("stderr");
        if (existing != null && existing.isStarted()) {
          return;
        }

        rootConfig
            .getAppenders()
            .values()
            .forEach(
                a -> {
                  rootConfig.removeAppender(a.getName());
                  a.stop();
                });

        PatternLayout layout = PatternLayout.newBuilder().withPattern("%-5p %c %x: %m%n").build();

        ConsoleAppender consoleAppender =
            ConsoleAppender.newBuilder()
                .setName("stderr")
                .setTarget(ConsoleAppender.Target.SYSTEM_ERR)
                .setLayout(layout)
                .setFollow(true)
                .build();
        consoleAppender.start();

        rootConfig.addAppender(consoleAppender, null, null);
        ctx.updateLoggers();
      } finally {
        consoleReconfiguring = false;
      }
    }
  }

  /**
   * Start error log system (console + file appenders).
   *
   * @param sitePath path to Gerrit site
   * @param config logging configuration
   * @param consoleLog whether to also log to stderr
   */
  public static LifecycleListener start(Path sitePath, LogConfig config, boolean consoleLog)
      throws IOException {
    Path logdir =
        FileUtil.mkdirsOrDie(new SitePaths(sitePath).logs_dir, "Cannot create log directory");

    if (SystemLog.shouldConfigure()) {
      LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
      reinstallLogSystem(ctx, logdir, config, consoleLog);

      ctx.addPropertyChangeListener(
          evt -> {
            if ("config".equals(evt.getPropertyName())) {
              reinstallLogSystem(ctx, logdir, config, consoleLog);
            }
          });
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

  private static void reinstallLogSystem(
      LoggerContext ctx, Path logdir, LogConfig config, boolean consoleLog) {
    synchronized (lock) {
      if (logSystemReconfiguring) {
        return;
      }
      logSystemReconfiguring = true;
      try {
        LoggerConfig rootConfig = ctx.getConfiguration().getRootLogger();

        Appender existingText = rootConfig.getAppenders().get(LOG_NAME);
        Appender existingJson = rootConfig.getAppenders().get(LOG_NAME + JSON_SUFFIX);
        if ((existingText != null && existingText.isStarted())
            || (existingJson != null && existingJson.isStarted())) {
          return;
        }

        rootConfig
            .getAppenders()
            .values()
            .forEach(
                a -> {
                  rootConfig.removeAppender(a.getName());
                  a.stop();
                });

        PatternLayout errorLogLayout =
            PatternLayout.newBuilder()
                .withPattern(
                    "[%d{" + LogTimestampFormatter.TIMESTAMP_FORMAT + "}] [%t] %-5p %c %x: %m%n")
                .build();

        if (consoleLog) {
          ConsoleAppender consoleAppender =
              ConsoleAppender.newBuilder()
                  .setName("stderr")
                  .setTarget(ConsoleAppender.Target.SYSTEM_ERR)
                  .setLayout(errorLogLayout)
                  .setFollow(true)
                  .build();
          consoleAppender.start();
          rootConfig.addAppender(consoleAppender, null, null);
        }

        boolean rotate = config.shouldRotate();

        if (config.isTextLogging() || !consoleLog) {
          Appender textAppender =
              SystemLog.createAppender(logdir, LOG_NAME, errorLogLayout, rotate);
          rootConfig.addAppender(textAppender, null, null);
        }

        if (config.isJsonLogging()) {
          Appender jsonAppender =
              SystemLog.createAppender(
                  logdir, LOG_NAME + JSON_SUFFIX, new ErrorLogJsonLayout(), rotate);
          rootConfig.addAppender(jsonAppender, null, null);
        }

        ctx.updateLoggers();
      } finally {
        logSystemReconfiguring = false;
      }
    }
  }
}
