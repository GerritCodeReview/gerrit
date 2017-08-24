// Copyright (C) 2013 The Android Open Source Project
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

import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

public class GuiceLogger {
  private static final Handler HANDLER;

  static {
    HANDLER =
        new StreamHandler(
            System.out,
            new Formatter() {
              @Override
              public String format(LogRecord record) {
                return String.format(
                    "[Guice %s] %s%n", record.getLevel().getName(), record.getMessage());
              }
            });
    HANDLER.setLevel(Level.ALL);
  }

  private GuiceLogger() {}

  public static Logger getLogger() {
    return Logger.getLogger("com.google.inject");
  }

  public static void enable() {
    Logger guiceLogger = getLogger();
    guiceLogger.addHandler(GuiceLogger.HANDLER);
    guiceLogger.setLevel(Level.ALL);
  }

  public static void disable() {
    Logger guiceLogger = getLogger();
    guiceLogger.setLevel(Level.OFF);
    guiceLogger.removeHandler(GuiceLogger.HANDLER);
  }
}
