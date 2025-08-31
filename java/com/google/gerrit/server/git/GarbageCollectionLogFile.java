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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Logger;
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
    removeAllAppenders(getLogger(com.google.gerrit.server.git.GarbageCollection.class));
    removeAllAppenders(getLogger(com.google.gerrit.server.git.GarbageCollectionRunner.class));
  }

  private static void initLogSystem(Path logdir, boolean rotate) {
    Appender appender =
        SystemLog.createAppender(
            logdir,
            LOG_NAME,
            PatternLayout.newBuilder()
                .withPattern("[%d] %-5p %x: %m%n")
                .withCharset(java.nio.charset.StandardCharsets.UTF_8)
                .build(),
            rotate);

    initGcLogger(getLogger(com.google.gerrit.server.git.GarbageCollection.class), appender);
    initGcLogger(getLogger(com.google.gerrit.server.git.GarbageCollectionRunner.class), appender);
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
