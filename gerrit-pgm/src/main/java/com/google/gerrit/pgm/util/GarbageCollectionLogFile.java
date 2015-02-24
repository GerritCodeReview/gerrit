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

package com.google.gerrit.pgm.util;

import com.google.gerrit.common.FileUtil;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GarbageCollection;
import com.google.gerrit.server.util.SystemLog;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public class GarbageCollectionLogFile {
  public static LifecycleListener start(Path sitePath)
      throws NoSuchFileException {
    Path logdir = FileUtil.mkdirsOrDie(new SitePaths(sitePath).logs_dir,
        "Cannot create log directory");
    if (SystemLog.shouldConfigure()) {
      initLogSystem(logdir);
    }

    return new LifecycleListener() {
      @Override
      public void start() {
      }

      @Override
      public void stop() {
        LogManager.getLogger(GarbageCollection.LOG_NAME).removeAllAppenders();
      }
    };
  }

  private static void initLogSystem(Path logdir) {
    Logger gcLogger = LogManager.getLogger(GarbageCollection.LOG_NAME);
    gcLogger.removeAllAppenders();
    gcLogger.addAppender(SystemLog.createAppender(logdir,
        GarbageCollection.LOG_NAME, new PatternLayout("[%d] %-5p %x: %m%n")));
    gcLogger.setAdditivity(false);
  }
}
