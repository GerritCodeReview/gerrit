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

package com.google.gerrit.server.util;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.config.LogConfig;
import com.google.gerrit.server.config.SitePaths;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.appender.AsyncAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.LoggerConfig;

public class SystemLog {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String LOG4J_CONFIGURATION = "log4j.configuration";

  private final SitePaths site;
  private final int asyncLoggingBufferSize;
  private final boolean rotateLogs;

  public SystemLog(SitePaths site, int asyncLoggingBufferSize, LogConfig logConfig) {
    this.site = site;
    this.asyncLoggingBufferSize = asyncLoggingBufferSize;
    this.rotateLogs = logConfig.shouldRotate();
  }

  /** Preserve plugin semantics */
  public static boolean shouldConfigure() {
    return Strings.isNullOrEmpty(System.getProperty(LOG4J_CONFIGURATION));
  }

  /** Basic File/RollingFile appender */
  public static Appender createAppender(Path logdir, String name, Layout<?> layout, boolean rotate) {
    LoggerContext ctx = LoggerContext.getContext(false);
    Configuration config = ctx.getConfiguration();
    Path logFile = logdir.resolve(name + ".log");

    Appender appender;
    if (rotate) {
      appender =
          RollingFileAppender.newBuilder()
              .withFileName(logFile.toString())
              .withFilePattern(logFile.toString() + ".%i")
              .withPolicy(SizeBasedTriggeringPolicy.createPolicy("100MB"))
              .withStrategy(DefaultRolloverStrategy.newBuilder().withMax("10").build())
              .setName(name)
              .setLayout(layout)
              .setConfiguration(config)
              .build();
    } else {
      appender =
          FileAppender.newBuilder()
              .withFileName(logFile.toString())
              .setName(name)
              .setLayout(layout)
              .setConfiguration(config)
              .build();
    }

    appender.start();
    config.addAppender(appender);
    return appender;
  }

  /** Async appender factory with plugin semantics */
  public Appender createAsyncAppender(String name, Layout<?> layout) {
    return createAsyncAppender(name, layout, rotateLogs, false);
  }

  public Appender createAsyncAppender(String name, Layout<?> layout, boolean rotate) {
    return createAsyncAppender(name, layout, rotate, false);
  }

public Appender createAsyncAppender(String name, Layout<?> layout, boolean rotate, boolean forPlugin) {
    LoggerContext ctx = LoggerContext.getContext(false);
    Configuration config = ctx.getConfiguration();

    Appender fileAppender;
    if (forPlugin || shouldConfigure()) {
        fileAppender = createAppender(site.logs_dir, name, layout, rotate);
    } else {
        LoggerConfig loggerConfig = config.getLoggerConfig(name);
        fileAppender = loggerConfig.getAppenders().get(name);
        if (fileAppender == null) {
            // instead of returning null, create a new appender
            logger.atWarning().log(
                "No appender with the name: %s was found. Creating a new appender.", name);
            fileAppender = createAppender(site.logs_dir, name, layout, rotate);
        }
    }

    AppenderRef ref = AppenderRef.createAppenderRef(fileAppender.getName(), null, null);

    AsyncAppender asyncAppender = AsyncAppender.newBuilder()
        .setName(name + "_async")
        .setConfiguration(config)
        .setAppenderRefs(new AppenderRef[]{ref})
        .setBlocking(false)
        .setBufferSize(asyncLoggingBufferSize)
        .build();

    asyncAppender.start();
    config.addAppender(asyncAppender);
    return asyncAppender;
  }
}
