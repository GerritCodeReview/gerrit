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

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.util.SystemLog;
import com.google.inject.Inject;
import java.io.Serializable;
import java.nio.file.Path;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.eclipse.jgit.lib.Config;

public class GarbageCollectionLogFile implements LifecycleListener {
  @Inject
  public GarbageCollectionLogFile(SitePaths sitePaths, @GerritServerConfig Config config) {
    if (SystemLog.shouldConfigure()) {
      initLogSystem(sitePaths.logs_dir, config.getBoolean("log", "rotate", true));
    }
  }

  @Override
  public void start() {}

  @Override
  public void stop() {
    LoggerContext ctx = LoggerContext.getContext(false);
    Logger root = ctx.getLogger(GarbageCollection.LOG_NAME);
    for (final Appender appender : root.getAppenders().values()) {
      root.removeAppender(appender);
    }
  }

  private static void initLogSystem(Path logdir, boolean rotate) {
    LoggerContext ctx = LoggerContext.getContext(false);
    Logger root = ctx.getLogger(GarbageCollection.LOG_NAME);
    for (final Appender appender : root.getAppenders().values()) {
      root.removeAppender(appender);
    }
    Layout<? extends Serializable> layout =
        PatternLayout.newBuilder()
            .withPattern("[%d] %-5p %x: %m%n")
            .withPatternSelector(null)
            .withConfiguration(null)
            .withRegexReplacement(null)
            .withCharset(null)
            .withAlwaysWriteExceptions(false)
            .withNoConsoleNoAnsi(false)
            .withHeader(null)
            .withFooter(null)
            .build();
    root.addAppender(SystemLog.createAppender(logdir, GarbageCollection.LOG_NAME, layout, rotate));
    root.setAdditivity(false);
  }
}
