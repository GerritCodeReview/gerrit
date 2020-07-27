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
import java.io.IOException;
import java.nio.file.Path;
import net.logstash.log4j.JSONEventLayoutV1;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.eclipse.jgit.lib.Config;

public class ErrorLogFile {
  static final String LOG_NAME = "error_log";
  static final String JSON_SUFFIX = ".json";

  public static void errorOnlyConsole() {
    LogManager.resetConfiguration();

    PatternLayout layout = new PatternLayout();
    layout.setConversionPattern("%-5p %c %x: %m%n");

    ConsoleAppender dst = new ConsoleAppender();
    dst.setLayout(layout);
    dst.setTarget("System.err");
    dst.setThreshold(Level.ERROR);
    dst.activateOptions();

    Logger root = LogManager.getRootLogger();
    root.removeAllAppenders();
    root.addAppender(dst);
  }

  public static LifecycleListener start(Path sitePath, Config config, boolean consoleLog)
      throws IOException {
    Path logdir =
        FileUtil.mkdirsOrDie(new SitePaths(sitePath).logs_dir, "Cannot create log directory");
    if (SystemLog.shouldConfigure()) {
      initLogSystem(logdir, config, consoleLog);
    }

    return new LifecycleListener() {
      @Override
      public void start() {}

      @Override
      public void stop() {
        LogManager.shutdown();
      }
    };
  }

  private static void initLogSystem(Path logdir, Config config, boolean consoleLog) {
    Logger root = LogManager.getRootLogger();
    root.removeAllAppenders();

    PatternLayout errorLogLayout = new PatternLayout("[%d] [%t] %-5p %c %x: %m%n");

    ConsoleAppender dst = new ConsoleAppender();
    dst.setLayout(errorLogLayout);
    dst.setTarget("System.err");
    dst.setThreshold(Level.INFO);
    dst.activateOptions();

    if (consoleLog) {
      root.addAppender(dst);
    }

    boolean json = config.getBoolean("log", "jsonLogging", false);
    boolean text = config.getBoolean("log", "textLogging", true) || !(json || consoleLog);
    boolean rotate = config.getBoolean("log", "rotate", true);

    if (text) {
      root.addAppender(SystemLog.createAppender(logdir, LOG_NAME, errorLogLayout, rotate));
    }

    if (json) {
      root.addAppender(
          SystemLog.createAppender(
              logdir, LOG_NAME + JSON_SUFFIX, new JSONEventLayoutV1(), rotate));
    }
  }
}
