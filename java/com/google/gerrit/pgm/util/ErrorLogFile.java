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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.TimeZone;
import org.apache.commons.lang.time.FastDateFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.layout.JsonLayout;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.eclipse.jgit.lib.Config;

public class ErrorLogFile {
  static final String LOG_NAME = "error_log";
  static final String JSON_SUFFIX = ".json";

  private static final FastDateFormat ISO_DATETIME_TIME_ZONE_FORMAT_WITH_MILLIS =
      FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", TimeZone.getTimeZone("UTC"));

  public static void errorOnlyConsole() {
    final LoggerContext ctxx = (LoggerContext) LogManager.getContext(false);
    ctxx.reconfigure();

    Layout<? extends Serializable> layout =
        PatternLayout.newBuilder().withPattern("%-5p %c %x: %m%n").build();
    final ConsoleAppender dst =
        ConsoleAppender.newBuilder()
            .withLayout(layout)
            .withName("Console")
            .setTarget(ConsoleAppender.Target.SYSTEM_ERR)
            .build();
    dst.start();

    LoggerContext ctx = LoggerContext.getContext(false);
    Logger root = ctx.getRootLogger();
    //  for (final Appender appender : root.getAppenders().values()) {
    //    root.removeAppender(appender);
    //  }
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
    //final Logger root = (Logger) LogManager.getRootLogger();

    boolean json = config.getBoolean("log", "jsonLogging", false);
    boolean text = config.getBoolean("log", "textLogging", true) || !json;
    boolean rotate = config.getBoolean("log", "rotate", true);

    if (text) {
      Layout<? extends Serializable> layout =
          PatternLayout.newBuilder().withPattern("[%d] [%t] %-5p %c %x: %m%n").build();

      root.addAppender(SystemLog.createAppender(logdir, LOG_NAME, layout, rotate));
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
              .setCharset(StandardCharsets.UTF_8)
              .setIncludeStacktrace(true)
              //.setAdditionalFields(new KeyValuePair[] {
              //    new KeyValuePair("@timestamp", dateFormat(LogEvent.getTimeMillis()))})
              .build();

      root.addAppender(SystemLog.createAppender(logdir, LOG_NAME + JSON_SUFFIX, layout, rotate));
    }
    //ctx.updateLoggers();
  }

  public static String dateFormat(long timestamp) {
    return ISO_DATETIME_TIME_ZONE_FORMAT_WITH_MILLIS.format(timestamp);
  }
}
