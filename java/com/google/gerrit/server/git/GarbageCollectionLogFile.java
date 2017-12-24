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
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.eclipse.jgit.lib.Config;

public class GarbageCollectionLogFile implements LifecycleListener {
  static final String PATTERN_LAYOUT = "[%d] %-5p %x: %m%n";

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
    LoggerConfig logger = new LoggerConfig(GarbageCollection.LOG_NAME, null, false);
    logger.removeAppender(GarbageCollection.LOG_NAME);
  }

  private static void initLogSystem(Path logdir, boolean rotate) {
    LoggerConfig logger = new LoggerConfig(GarbageCollection.LOG_NAME, null, false);
    logger.removeAppender(GarbageCollection.LOG_NAME);
    Layout<? extends Serializable> layout =
        PatternLayout.newBuilder().withPattern(PATTERN_LAYOUT).build();
    logger.addAppender(
        SystemLog.createAppender(logdir, GarbageCollection.LOG_NAME, layout, rotate), null, null);
    logger.setAdditive(false);
  }
}
