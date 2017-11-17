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
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.Deflater;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AsyncAppender;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.CronTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.NullConfiguration;
import org.eclipse.jgit.lib.Config;

@Singleton
public class SystemLog {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String LOG4J_CONFIGURATION = "log4j.configurationFile";
  private static final String CRON_EXPRESSION = "0 0 0 * * ?";

  private final SitePaths site;
  private final int asyncLoggingBufferSize;
  private final boolean rotateLogs;

  @Inject
  public SystemLog(SitePaths site, @GerritServerConfig Config config) {
    this.site = site;
    this.asyncLoggingBufferSize = config.getInt("core", "asyncLoggingBufferSize", 64);
    this.rotateLogs = config.getBoolean("log", "rotate", true);
  }

  public static boolean shouldConfigure() {
    return Strings.isNullOrEmpty(System.getProperty(LOG4J_CONFIGURATION));
  }

  public static Appender createAppender(Path logdir, String name, Layout layout, boolean rotate) {
    Appender dst =
        rotate ? setDailyFileAppender(logdir, name, layout) : setFileAppender(logdir, name, layout);
    dst.start();

    return dst;
  }

  public AsyncAppender createAsyncAppender(String name, Layout layout) {
    return createAsyncAppender(name, layout, rotateLogs);
  }

  private AsyncAppender createAsyncAppender(String name, Layout layout, boolean rotate) {
    return createAsyncAppender(name, layout, rotate, false);
  }

  public AsyncAppender createAsyncAppender(
      String name, Layout layout, boolean rotate, boolean forPlugin) {
    LoggerContext context = LoggerContext.getContext(false);
    Configuration config = context.getConfiguration();

    if (forPlugin || shouldConfigure()) {
      config.addAppender(createAppender(site.logs_dir, name, layout, rotate));
    } else {
      Appender appender = config.getAppender(name);
      if (appender != null) {
        config.addAppender(appender);
      } else {
        logger.atWarning().log(
            "No appender with the name: %s was found. %s logging is disabled", name, name);
      }
    }

    AppenderRef ref = AppenderRef.createAppenderRef(name, null, null);
    AppenderRef[] refs = new AppenderRef[] {ref};

    AsyncAppender async;
    try {
      async =
          AsyncAppender.newBuilder()
              .setName(name)
              .setBlocking(true)
              .setBufferSize(asyncLoggingBufferSize)
              .setIncludeLocation(false)
              .setConfiguration(config)
              .setAppenderRefs(refs)
              .setErrorRef(null)
              .build();
      async.start();
    } catch (Exception ex) {
      async = null;
    }

    context.updateLoggers();

    return async;
  }

  private static Appender setDailyFileAppender(Path logdir, String name, Layout layout) {
    LoggerContext ctx = LoggerContext.getContext(false);
    Configuration config = ctx.getConfiguration();

    Appender dst =
        RollingFileAppender.createAppender(
            resolve(logdir).resolve(name).toString(),
            resolve(logdir).resolve(name).toString() + ".%d{yyyy-MM-dd}.gz",
            "true",
            name,
            null,
            null,
            "true",
            CronTriggeringPolicy.createPolicy(new NullConfiguration(), "true", CRON_EXPRESSION),
            DefaultRolloverStrategy.createStrategy(
                "7",
                "1",
                "max",
                String.valueOf(Deflater.DEFAULT_COMPRESSION),
                null,
                false,
                new NullConfiguration()),
            layout,
            null,
            "false",
            null,
            null,
            config);

    return dst;
  }

  private static Appender setFileAppender(Path logdir, String name, Layout layout) {
    LoggerContext ctx = LoggerContext.getContext(false);
    Configuration config = ctx.getConfiguration();

    Appender dst =
        FileAppender.createAppender(
            resolve(logdir).resolve(name).toString(),
            "true",
            null,
            name,
            "true",
            null,
            null,
            null,
            layout,
            null,
            "false",
            null,
            config);

    return dst;
  }

  private static Path resolve(Path p) {
    try {
      return p.toRealPath().normalize();
    } catch (IOException e) {
      return p.toAbsolutePath().normalize();
    }
  }
}
