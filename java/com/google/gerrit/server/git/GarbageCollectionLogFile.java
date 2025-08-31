// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.common.flogger.backend.Platform;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.LogConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.util.SystemLog;
import com.google.inject.Inject;
import java.nio.file.Path;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

public class GarbageCollectionLogFile implements LifecycleListener {
  private static final String LOG_NAME = "gc_log";

  @Inject
  public GarbageCollectionLogFile(SitePaths sitePaths, LogConfig config) {
    if (SystemLog.shouldConfigure()) {
      initLogSystem(sitePaths.logs_dir, config.shouldRotate());
    }
  }

  @Override
  public void start() {}

  @Override
  public void stop() {
    detachLogger(GarbageCollection.class);
    detachLogger(GarbageCollectionRunner.class);
  }

  private static void initLogSystem(Path logdir, boolean rotate) {
    PatternLayout layout = PatternLayout.newBuilder().withPattern("[%d] %-5p %x: %m%n").build();

    Appender appender = SystemLog.createAppender(logdir, LOG_NAME, layout, rotate);

    attachAppender(GarbageCollection.class, appender);
    attachAppender(GarbageCollectionRunner.class, appender);
  }

  private static void attachAppender(Class<?> clazz, Appender appender) {
    String loggerName = Platform.getBackend(clazz.getName()).getLoggerName();
    LoggerContext ctx = LoggerContext.getContext(false);
    Configuration config = ctx.getConfiguration();

    LoggerConfig loggerConfig = config.getLoggerConfig(loggerName);
    // Avoid attaching multiple times
    if (!loggerConfig.getAppenders().containsKey(appender.getName())) {
      loggerConfig.addAppender(appender, null, null);
      loggerConfig.setAdditive(false);
      ctx.updateLoggers();
    }
  }

  private static void detachLogger(Class<?> clazz) {
    String loggerName = Platform.getBackend(clazz.getName()).getLoggerName();
    LoggerContext ctx = LoggerContext.getContext(false);
    Configuration config = ctx.getConfiguration();

    LoggerConfig loggerConfig = config.getLoggerConfig(loggerName);
    loggerConfig.getAppenders().keySet().forEach(loggerConfig::removeAppender);
    ctx.updateLoggers();
  }
}
