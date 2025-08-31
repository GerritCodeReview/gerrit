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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.layout.PatternLayout;

/** Manages the gc_log configuration, resilient against live Log4j reconfiguration. */
public class GarbageCollectionLogFile implements LifecycleListener {
  private static final String LOG_NAME = "gc_log";

  private final Path logdir;
  private final boolean rotate;

  private static final Object lock = new Object();
  private static final AtomicBoolean reconfiguring = new AtomicBoolean(false);

  @Inject
  public GarbageCollectionLogFile(SitePaths sitePaths, LogConfig config) {
    this.logdir = sitePaths.logs_dir;
    this.rotate = config.shouldRotate();

    if (SystemLog.shouldConfigure()) {
      LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
      reinstallGcLoggers(ctx, logdir, rotate);

      ctx.addPropertyChangeListener(
          evt -> {
            if ("config".equals(evt.getPropertyName())) {
              reinstallGcLoggers(ctx, logdir, rotate);
            }
          });
    }
  }

  @Override
  public void start() {}

  @Override
  public void stop() {
    removeAllAppenders(getLogger(GarbageCollection.class));
    removeAllAppenders(getLogger(GarbageCollectionRunner.class));
  }

  private static void reinstallGcLoggers(LoggerContext ctx, Path logdir, boolean rotate) {
    synchronized (lock) {
      if (reconfiguring.get()) {
        return;
      }
      reconfiguring.set(true);
      try {
        Appender existing = getLogger(GarbageCollection.class).getAppenders().get(LOG_NAME);
        if (existing != null && existing.isStarted()) {
          return; // already configured
        }

        Appender appender =
            SystemLog.createAppender(
                logdir,
                LOG_NAME,
                PatternLayout.newBuilder()
                    .withPattern("[%d] %-5p %x: %m%n")
                    .withCharset(StandardCharsets.UTF_8)
                    .build(),
                rotate);

        initGcLogger(getLogger(GarbageCollection.class), appender);
        initGcLogger(getLogger(GarbageCollectionRunner.class), appender);

        ctx.updateLoggers();
      } finally {
        reconfiguring.set(false);
      }
    }
  }

  private static Logger getLogger(Class<?> clazz) {
    String loggerName = Platform.getBackend(clazz.getName()).getLoggerName();
    return (Logger) LogManager.getLogger(loggerName);
  }

  private static void initGcLogger(Logger gcLogger, Appender appender) {
    removeAllAppenders(gcLogger);
    gcLogger.addAppender(appender);
    gcLogger.setAdditive(false);
  }

  private static void removeAllAppenders(Logger logger) {
    logger.getAppenders().values().forEach(a -> logger.removeAppender(a));
  }
}
