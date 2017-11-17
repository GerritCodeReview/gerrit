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
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AsyncAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.OnStartupTriggeringPolicy;
import org.eclipse.jgit.lib.Config;
import org.slf4j.LoggerFactory;

@Singleton
public class SystemLog {
  private static final org.slf4j.Logger log = LoggerFactory.getLogger(SystemLog.class);

  public static final String LOG4J_CONFIGURATION = "log4j.configurationFile";

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
        RollingFileAppender.createAppender(
            resolve(logdir).resolve(name).toString(),
            name + "-%d{MM-dd-yyyy}.gz",
            "true",
            name,
            null,
            null,
            "true",
            OnStartupTriggeringPolicy.createPolicy(1),
            null,
            layout,
            null,
            null,
            null,
            null,
            null);

    return dst;
  }

  public AsyncAppender createAsyncAppender(String name, Layout layout) {
    return createAsyncAppender(name, layout, rotateLogs);
  }

  private AsyncAppender createAsyncAppender(String name, Layout layout, boolean rotate) {
    AsyncAppender async =
        AsyncAppender.newBuilder()
            .setName(name)
            .setBlocking(true)
            .setBufferSize(asyncLoggingBufferSize)
            .setIncludeLocation(false)
            .build();

    if (shouldConfigure()) {
      LoggerContext ctx = LoggerContext.getContext(false);
      Logger logger = ctx.getLogger(name);
      logger.addAppender(createAppender(site.logs_dir, name, layout, rotate));
    } else {
      final LoggerContext context = LoggerContext.getContext(false);
      Appender appender = context.getConfiguration().getAppender(name);
      if (appender != null) {
        LoggerContext ctx = LoggerContext.getContext(false);
        Logger logger = ctx.getLogger(name);
        logger.addAppender(appender);
      } else {
        log.warn(
            "No appender with the name: " + name + " was found. " + name + " logging is disabled");
      }
    }
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
