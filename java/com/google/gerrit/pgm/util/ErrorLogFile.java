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
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.PatternLayout;
import org.eclipse.jgit.lib.Config;
import ch.qos.logback.core.Layout;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;

public class ErrorLogFile {
  static final String LOG_NAME = "error_log";
  static final String JSON_SUFFIX = ".json";

  public static void errorOnlyConsole() {
    // LogManager.resetConfiguration();

         /*Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

         LoggerContext loggerContext = rootLogger.getLoggerContext();*/
    //LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
    LoggerContext ctx = new LoggerContext();

    //PatternLayoutEncoder logEncoder = new PatternLayoutEncoder();
    PatternLayout logEncoder = new PatternLayout();
    logEncoder.setContext(ctx);
    logEncoder.setPattern("[%d] [%t] %-5p %c %x: %m%n");
    logEncoder.start();

    final ConsoleAppender dst = new ConsoleAppender();
    dst.setContext(ctx);
    dst.setName("console");
    dst.setTarget("System.err");
    //dst.setEncoder(logEncoder);
    dst.setLayout(logEncoder);
    dst.start();

    LoggerContext loggerContext = new LoggerContext();
    Logger root = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
    root.detachAndStopAllAppenders();
    root.addAppender(dst);
  }

  public static LifecycleListener start(Path sitePath, Config config) throws IOException {
    Path logdir =
        FileUtil.mkdirsOrDie(new SitePaths(sitePath).logs_dir, "Cannot create log directory");
    if (SystemLog.shouldConfigure()) {
      initLogSystem(logdir, config);
    }

    return new LifecycleListener() {
      @Override
      public void start() {}

      @Override
      public void stop() {
        //LogManager.shutdown();
      }
    };
  }

  private static void initLogSystem(Path logdir, Config config) {
    //LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();

    LoggerContext loggerContext = new LoggerContext();
    Logger root = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
    root.detachAndStopAllAppenders();

    boolean json = config.getBoolean("log", "jsonLogging", false);
    boolean text = config.getBoolean("log", "textLogging", true) || !json;
    boolean rotate = config.getBoolean("log", "rotate", true);

    if (text) {
      //PatternLayoutEncoder logEncoder = new PatternLayoutEncoder();
      PatternLayout logEncoder = new PatternLayout();
      logEncoder.setContext(loggerContext);
      logEncoder.setPattern("[%d] [%t] %-5p %c %x: %m%n");
      logEncoder.start();

      root.addAppender(
          SystemLog.createAppender(
              logdir, LOG_NAME, logEncoder, rotate));
    }

    /*if (json) {
      root.addAppender(
          SystemLog.createAppender(
              logdir, LOG_NAME + JSON_SUFFIX, new JSONEventLayoutV1(), rotate));
    }*/
  }
}
