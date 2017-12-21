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
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.JsonLayout;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.eclipse.jgit.lib.Config;

public class ErrorLogFile {
  static final String LOG_NAME = "error_log";
  static final String JSON_SUFFIX = ".json";
  static final String PATTERN_LAYOUT = "[%d] [%t] %-5p %c %x: %m%n";

  public static void errorOnlyConsole() {
    final LoggerContext context = (LoggerContext) LogManager.getContext(false);
    context.reconfigure();

    Layout<? extends Serializable> layout =
        PatternLayout.newBuilder().withPattern(PATTERN_LAYOUT).build();
    final ConsoleAppender dst =
        ConsoleAppender.newBuilder()
            .withLayout(layout)
            .withName("Console")
            .setTarget(ConsoleAppender.Target.SYSTEM_ERR)
            .build();
    dst.start();

    LoggerContext ctx = LoggerContext.getContext(false);
    Configuration config = ctx.getConfiguration();
    LoggerConfig root = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
    root.removeAppender(LOG_NAME);
    root.addAppender(dst, null, null);
    ctx.updateLoggers();
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
    Configuration configs = ctx.getConfiguration();
    LoggerConfig root = configs.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
    root.removeAppender(LOG_NAME);

    boolean json = config.getBoolean("log", "jsonLogging", false);
    boolean text = config.getBoolean("log", "textLogging", true) || !json;
    boolean rotate = config.getBoolean("log", "rotate", true);

    if (text) {
      Layout<? extends Serializable> layout =
          PatternLayout.newBuilder().withPattern(PATTERN_LAYOUT).build();

      root.addAppender(SystemLog.createAppender(logdir, LOG_NAME, layout, rotate), null, null);
    }

    if (json) {
      final JsonLayout layout =
          JsonLayout.newBuilder()
              .setLocationInfo(true)
              .setProperties(true)
              .setPropertiesAsList(false)
              .setComplete(false)
              .setCompact(false)
              .setEventEol(true)
              .setCharset(UTF_8)
              .setIncludeStacktrace(true)
              .build();

      root.addAppender(
          SystemLog.createAppender(logdir, LOG_NAME + JSON_SUFFIX, layout, rotate), null, null);
    }

    ctx.updateLoggers();
  }
}
