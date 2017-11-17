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
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.util.SystemLog;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import org.apache.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.eclipse.jgit.lib.Config;

public class ErrorLogFile {
  static final String LOG_NAME = "error_log";
  static final String JSON_SUFFIX = ".json";

  public static void errorOnlyConsole() {
    LogManager.resetConfiguration();

    Layout<? extends Serializable> layout =
        PatternLayout.newBuilder()
            .withPattern("%-5p %c %x: %m%n")
            .withPatternSelector(null)
            .withConfiguration(null)
            .withRegexReplacement(null)
            .withCharset(null)
            .withAlwaysWriteExceptions(false)
            .withNoConsoleNoAnsi(false)
            .withHeader(null)
            .withFooter(null)
            .build();
    final ConsoleAppender dst =
        ConsoleAppender.newBuilder()
            .withLayout(layout)
            .setTarget(ConsoleAppender.Target.SYSTEM_ERR)
            .build();
    dst.start();

    LoggerContext ctx = LoggerContext.getContext(false);
    Logger root = ctx.getRootLogger();
    for (final Appender appender : root.getAppenders().values()) {
      root.removeAppender(appender);
    }
    root.addAppender(dst);
  }

  public static LifecycleListener start(Path sitePath, Config config) throws IOException {
    Path logdir =
        FileUtil.mkdirsOrDie(new SitePaths(sitePath).logs_dir, "Cannot create log directory");
    if (SystemLog.shouldConfigure()) {
      initLogSystem(logdir, config);
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

  private static void initLogSystem(Path logdir, Config config) {
    LoggerContext ctx = LoggerContext.getContext(false);
    Logger root = ctx.getRootLogger();
    for (final Appender appender : root.getAppenders().values()) {
      root.removeAppender(appender);
    }

    boolean json = config.getBoolean("log", "jsonLogging", false);
    boolean text = config.getBoolean("log", "textLogging", true) || !json;
    boolean rotate = config.getBoolean("log", "rotate", true);

    if (text) {
      Layout<? extends Serializable> layout =
          PatternLayout.newBuilder()
              .withPattern("[%d] [%t] %-5p %c %x: %m%n")
              .withPatternSelector(null)
              .withConfiguration(null)
              .withRegexReplacement(null)
              .withCharset(null)
              .withAlwaysWriteExceptions(false)
              .withNoConsoleNoAnsi(false)
              .withHeader(null)
              .withFooter(null)
              .build();

      root.addAppender(SystemLog.createAppender(logdir, LOG_NAME, layout, rotate));
    }

    if (json) {
      root.addAppender(
          SystemLog.createAppender(
              logdir, LOG_NAME + JSON_SUFFIX, new JSONEventLayoutV2(), rotate));
    }
  }
}
