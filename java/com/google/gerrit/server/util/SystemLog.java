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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import com.google.gerrit.common.Die;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import ch.qos.logback.core.Appender;
import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.Layout;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.eclipse.jgit.lib.Config;
import org.slf4j.LoggerFactory;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.FileAppender;

@Singleton
public class SystemLog {
  private static final org.slf4j.Logger log = LoggerFactory.getLogger(SystemLog.class);

  public static final String LOGBACK_CONFIGURATION = "logback.configurationFile";

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
    return Strings.isNullOrEmpty(System.getProperty(LOGBACK_CONFIGURATION));
  }

  public static Appender createAppender(Path logdir, String name, Layout layout, boolean rotate) {
    /*Appender dst =
        rotate ? setDailyFileAppender(logdir, name, layout) : setFileAppender(logdir, name, layout);*/
    if (rotate) {
      Appender dst = setDailyFileAppender(logdir, name, layout);

      return dst;
    } else {
      Appender dst = setFileAppender(logdir, name, layout);

      return dst;
    }
  }

  public AsyncAppender createAsyncAppender(String name, Layout layout) {
    return createAsyncAppender(name, layout, rotateLogs);
  }

  private AsyncAppender createAsyncAppender(String name, Layout layout, boolean rotate) {
    AsyncAppender async = new AsyncAppender();
    async.setName(name);
    async.setNeverBlock(true);
    async.setQueueSize(asyncLoggingBufferSize);

    if (shouldConfigure()) {
      async.addAppender(createAppender(site.logs_dir, name, layout, rotate));
    } else {
      LoggerContext loggerContext = new LoggerContext();
      Logger logger = loggerContext.getLogger(name);
      Appender appender = logger.getAppender(name);
      if (appender != null) {
        async.addAppender(appender);
      } else {
        log.warn(
            "No appender with the name: " + name + " was found. " + name + " logging is disabled");
      }
    }
    return async;
  }

  private static Appender setDailyFileAppender(Path logdir, String name, Layout layout/*, PatternLayoutEncoder patternLayout*/) {
    LoggerContext loggerContext = new LoggerContext();

    /*PatternLayoutEncoder logEncoder = new PatternLayoutEncoder();
    logEncoder.setContext(logCtx);
    logEncoder.setPattern('%-12date{YYYY-MM-dd HH:mm:ss.SSS} %-5level - %msg%n');
    logEncoder.start();*/

    RollingFileAppender dst = new RollingFileAppender();
    dst.setContext(loggerContext);
    dst.setName(name);
    /*if (layout == null) {
      dst.setEncoder(patternLayout);
    } else {*/
      dst.setLayout(layout);
    //}
    dst.setAppend(true);
    dst.setFile(resolve(logdir).resolve(name).toString());

    TimeBasedRollingPolicy logFilePolicy = new TimeBasedRollingPolicy();
    logFilePolicy.setContext(loggerContext);
    logFilePolicy.setParent(dst);
    logFilePolicy.setFileNamePattern(resolve(logdir).resolve(name).toString() + ".%d{yyyy-MM-dd}.gz");
    logFilePolicy.setMaxHistory(7);
    logFilePolicy.start();

    dst.setRollingPolicy(logFilePolicy);
    dst.start();

    return dst;
  }

  private static Appender setFileAppender(Path logdir, String name, Layout layout) {
    LoggerContext loggerContext = new LoggerContext();

    FileAppender dst = new FileAppender();
    dst.setContext(loggerContext);
    dst.setName(name);
    dst.setLayout(layout);
    dst.setAppend(true);
    dst.setFile(resolve(logdir).resolve(name).toString());
    dst.start();

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
