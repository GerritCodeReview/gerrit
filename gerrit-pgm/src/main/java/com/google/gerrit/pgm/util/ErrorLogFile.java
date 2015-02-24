// Copyright (C) 2009 The Android Open Source Project
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
import com.google.gerrit.server.util.SystemLog;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.io.IOException;
import java.nio.file.Path;

public class ErrorLogFile {
  static final String LOG_NAME = "error_log";

  public static void errorOnlyConsole() {
    LogManager.resetConfiguration();

    final PatternLayout layout = new PatternLayout();
    layout.setConversionPattern("%-5p %c %x: %m%n");

    final ConsoleAppender dst = new ConsoleAppender();
    dst.setLayout(layout);
    dst.setTarget("System.err");
    dst.setThreshold(Level.ERROR);

    final Logger root = LogManager.getRootLogger();
    root.removeAllAppenders();
    root.addAppender(dst);
  }

  public static LifecycleListener start(final Path sitePath)
      throws IOException {
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
        LogManager.shutdown();
      }
    };
  }

  private static void initLogSystem(Path logdir) {
    final Logger root = LogManager.getRootLogger();
    root.removeAllAppenders();
    root.addAppender(SystemLog.createAppender(logdir, LOG_NAME,
        new PatternLayout("[%d] %-5p %c %x: %m%n")));
  }
}
