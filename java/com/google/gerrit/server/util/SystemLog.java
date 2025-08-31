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
import com.google.gerrit.common.Die;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.LogConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AsyncAppender;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.TimeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.eclipse.jgit.lib.Config;

@Singleton
public class SystemLog {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String LOG4J_CONFIGURATION = "log4j.configuration";

  private final SitePaths site;
  private final int asyncLoggingBufferSize;
  private final boolean rotateLogs;

  @Inject
  public SystemLog(SitePaths site, @GerritServerConfig Config config, LogConfig logConfig) {
    this.site = site;
    this.asyncLoggingBufferSize = config.getInt("core", "asyncLoggingBufferSize", 64);
    this.rotateLogs = logConfig.shouldRotate();
  }

  public static boolean shouldConfigure() {
    return Strings.isNullOrEmpty(System.getProperty(LOG4J_CONFIGURATION));
  }

  public static Appender createAppender(
      Path logdir, String name, Layout<?> layout, boolean rotate) {
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    Configuration config = ctx.getConfiguration();
    String filePath = resolve(logdir).resolve(name).toString();

    Appender appender;
    if (rotate) {
      appender =
          RollingFileAppender.newBuilder()
              .withFileName(filePath)
              .withFilePattern(filePath + ".%d{yyyy-MM-dd}")
              .withLayout(layout)
              .withName(name)
              .withAppend(true)
              .withImmediateFlush(true)
              .withPolicy(TimeBasedTriggeringPolicy.newBuilder().withInterval(1).build())
              .setConfiguration(config)
              .build();
    } else {
      appender =
          FileAppender.newBuilder()
              .withFileName(filePath)
              .withLayout(layout)
              .withName(name)
              .withAppend(true)
              .withImmediateFlush(true)
              .setConfiguration(config)
              .build();
    }

    if (appender == null) {
      throw new Die("Log4j2 builder returned null for appender: " + name + " at " + filePath);
    }

    appender.start();
    config.addAppender(appender);
    return appender;
  }

  public AsyncAppender createAsyncAppender(String name, Layout<?> layout) {
    return createAsyncAppender(name, layout, rotateLogs);
  }

  private AsyncAppender createAsyncAppender(String name, Layout<?> layout, boolean rotate) {
    return createAsyncAppender(name, layout, rotate, false);
  }

  public AsyncAppender createAsyncAppender(
      String name, Layout<?> layout, boolean rotate, boolean forPlugin) {
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    Configuration config = ctx.getConfiguration();

    Appender appender = null;
    if (forPlugin || shouldConfigure()) {
      appender = createAppender(site.logs_dir, name, layout, rotate);
    } else {
      LoggerConfig existingLoggerConfig = config.getLoggerConfig(name);
      if (existingLoggerConfig != null) {
        appender = existingLoggerConfig.getAppenders().get(name);
        if (appender == null) {
          logger.atWarning().log(
              "No appender with the name: %s was found. %s logging is disabled", name, name);
        }
      }
    }

    AppenderRef ref =
        AppenderRef.createAppenderRef(appender != null ? appender.getName() : null, null, null);
    AsyncAppender async =
        AsyncAppender.newBuilder()
            .setName(name)
            .setAppenderRefs(new AppenderRef[] {ref})
            .setBufferSize(asyncLoggingBufferSize)
            .setBlocking(false)
            .setConfiguration(config)
            .build();

    async.start();
    config.addAppender(async);

    return async;
  }

  private static Path resolve(Path p) {
    try {
      return p.toRealPath().normalize();
    } catch (IOException e) {
      return p.toAbsolutePath().normalize();
    }
  }
}
