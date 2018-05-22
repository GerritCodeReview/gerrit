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
import java.nio.file.Path;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.eclipse.jgit.lib.Config;

public class GarbageCollectionLogFile implements LifecycleListener {

  @Inject
  public GarbageCollectionLogFile(SitePaths sitePaths, @GerritServerConfig Config config) {
    if (SystemLog.shouldConfigure()) {
      initLogSystem(sitePaths.logs_dir, config);
    }
  }

  @Override
  public void start() {}

  @Override
  public void stop() {
    LogManager.getLogger(GarbageCollection.LOG_NAME).removeAllAppenders();
  }

  private static void initLogSystem(Path logdir, @GerritServerConfig Config config) {
    Logger gcLogger = LogManager.getLogger(GarbageCollection.LOG_NAME);
    gcLogger.removeAllAppenders();

    String logDir = config.getString("log", null, "logDir") || logdir.toString();
    gcLogger.addAppender(
        SystemLog.createAppender(
            logDir, GarbageCollection.LOG_NAME, new PatternLayout("[%d] %-5p %x: %m%n")));

    gcLogger.setAdditivity(false);
  }
}
