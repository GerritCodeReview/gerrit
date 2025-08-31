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
    Path filePath = resolve(logdir).resolve(name);
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    Configuration config = ctx.getConfiguration();

    if (layout == null) {
      throw new Die("Log4j2 layout is null for appender: " + name);
    }

    Appender appender;
    try {
      if (rotate) {
        appender =
            RollingFileAppender.newBuilder()
                .withFileName(filePath.toString())
                .withFilePattern(filePath.toString() + ".%d{yyyy-MM-dd}")
                .withName(name)
                .withLayout(layout)
                .withImmediateFlush(true)
                .withAppend(true)
                .setConfiguration(config)
                .withPolicy(TimeBasedTriggeringPolicy.newBuilder().withInterval(1).build())
                .build();
      } else {
        appender =
            FileAppender.newBuilder()
                .setName(name)
                .withFileName(filePath.toString())
                .withLayout(layout)
                .withImmediateFlush(true)
                .withAppend(true)
                .setConfiguration(config)
                .build();
      }
    } catch (Exception e) {
      throw new Die(
          "Exception while building Log4j2 appender: "
              + name
              + " at "
              + filePath
              + ": "
              + e.getMessage(),
          e);
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

    AppenderRef[] refs;
    if (forPlugin || shouldConfigure()) {
      var unused = createAppender(site.logs_dir, name, layout, rotate);
      refs = new AppenderRef[] {AppenderRef.createAppenderRef(name, null, null)};
    } else {
      Appender appender = config.getAppender(name);
      if (appender != null) {
        refs = new AppenderRef[] {AppenderRef.createAppenderRef(name, null, null)};
      } else {
        logger.atWarning().log(
            "No appender with the name: %s was found. %s logging is disabled", name, name);
        refs = new AppenderRef[0];
      }
    }

    AsyncAppender asyncAppender =
        AsyncAppender.newBuilder()
            .setName(name)
            .setBlocking(true)
            .setBufferSize(asyncLoggingBufferSize)
            .setConfiguration(config)
            .setAppenderRefs(refs)
            .build();

    asyncAppender.start();
    config.addAppender(asyncAppender);
    return asyncAppender;
  }

  private static Path resolve(Path p) {
    try {
      return p.toRealPath().normalize();
    } catch (IOException e) {
      return p.toAbsolutePath().normalize();
    }
  }
}
