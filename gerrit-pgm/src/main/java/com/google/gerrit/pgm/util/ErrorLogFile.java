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

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.SitePaths;

import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.helpers.OnlyOnceErrorHandler;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.LoggingEvent;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

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

  public static LifecycleListener start(final File sitePath)
      throws FileNotFoundException {
    final File logdir = new SitePaths(sitePath).logs_dir;
    if (!logdir.exists() && !logdir.mkdirs()) {
      throw new Die("Cannot create log directory: " + logdir);
    }

    final PatternLayout layout = new PatternLayout();
    layout.setConversionPattern("[%d] %-5p %c %x: %m%n");

    final DailyRollingFileAppender dst = new DailyRollingFileAppender();
    dst.setName(LOG_NAME);
    dst.setLayout(layout);
    dst.setEncoding("UTF-8");
    dst.setFile(new File(resolve(logdir), LOG_NAME).getPath());
    dst.setImmediateFlush(true);
    dst.setAppend(true);
    dst.setThreshold(Level.INFO);
    dst.setErrorHandler(new DieErrorHandler());
    dst.activateOptions();
    dst.setErrorHandler(new OnlyOnceErrorHandler());

    final Logger root = LogManager.getRootLogger();
    root.removeAllAppenders();
    root.addAppender(dst);

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

  private static File resolve(final File logs_dir) {
    try {
      return logs_dir.getCanonicalFile();
    } catch (IOException e) {
      return logs_dir.getAbsoluteFile();
    }
  }

  private ErrorLogFile() {
  }

  private static final class DieErrorHandler implements ErrorHandler {
    @Override
    public void error(String message, Exception e, int errorCode,
        LoggingEvent event) {
      error(e != null ? e.getMessage() : message);
    }

    @Override
    public void error(String message, Exception e, int errorCode) {
      error(e != null ? e.getMessage() : message);
    }

    @Override
    public void error(String message) {
      throw new Die("Cannot open log file: " + message);
    }

    @Override
    public void activateOptions() {
    }

    @Override
    public void setAppender(Appender appender) {
    }

    @Override
    public void setBackupAppender(Appender appender) {
    }

    @Override
    public void setLogger(Logger logger) {
    }
  }
}
