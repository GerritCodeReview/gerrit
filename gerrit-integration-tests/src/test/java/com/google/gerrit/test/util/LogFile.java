// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.test.util;

import com.google.gerrit.test.GerritTestProperty;
import com.google.gerrit.test.TraceLevel;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.FileAppender;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.helpers.OnlyOnceErrorHandler;

import java.io.File;
import java.io.IOException;

public class LogFile {
  private static final File LOG_DIR = new File("target/logs");
  private static final String LOG_NAME = "test_log";

  public static void start() {
    cleanup();

    if (!LOG_DIR.mkdirs()) {
      throw new RuntimeException("Failed to create log dir '"
          + LOG_DIR.getAbsolutePath() + "'.");
    }

    final TraceLevel traceLevel = GerritTestProperty.TRACE_LEVEL.get();
    if (traceLevel != null) {
      final PatternLayout layout = new PatternLayout();
      layout.setConversionPattern("[%d] %-5p %c %x: %m%n");

      final FileAppender dst = new FileAppender();
      dst.setName(LOG_NAME);
      dst.setLayout(layout);
      dst.setEncoding("UTF-8");
      dst.setFile(new File(resolve(LOG_DIR), LOG_NAME).getPath());
      dst.setImmediateFlush(true);
      dst.setAppend(false);
      dst.setThreshold(traceLevel.getLevel());
      dst.activateOptions();
      dst.setErrorHandler(new OnlyOnceErrorHandler());

      final Logger root = LogManager.getRootLogger();
      root.removeAllAppenders();
      root.addAppender(dst);
    }
  }

  public static void logFile(final org.slf4j.Logger log,
      final TraceLevel level, final String message, final String fileExtension,
      final String fileContent) {
    logFile(log, level, message, fileExtension, fileContent, null);
  }

  public static void logFile(final org.slf4j.Logger log,
      final TraceLevel level, final String message, final String fileExtension,
      final String fileContent, final Throwable t) {
    final File file =
        new File(LOG_DIR, System.currentTimeMillis() + "." + fileExtension);
    try {
      FileUtils.write(file, fileContent);
    } catch (IOException e) {
      throw new RuntimeException("saving log file failed", e);
    }
    level.log(log,
        message + ". Additional information in file '" + file.getAbsolutePath()
            + "'.", t);
  }

  private static void cleanup() {
    if (LOG_DIR.exists()) {
      try {
        FileUtils.deleteDirectory(LOG_DIR);
      } catch (IOException e) {
        throw new RuntimeException("Failed to cleanup log dir '"
            + LOG_DIR.getAbsolutePath() + "'.", e);
      }
    }
  }

  private static File resolve(final File logs_dir) {
    try {
      return logs_dir.getCanonicalFile();
    } catch (IOException e) {
      return logs_dir.getAbsoluteFile();
    }
  }
}
