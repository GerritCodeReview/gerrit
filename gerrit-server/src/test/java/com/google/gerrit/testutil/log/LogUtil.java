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

package com.google.gerrit.testutil.log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import org.apache.log4j.Appender;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;

public class LogUtil {
  /**
   * Change logger's setting so it only logs to a collection.
   *
   * @param logName Name of the logger to modify.
   * @param collection The collection to log into.
   * @return The logger's original settings.
   */
  public static LoggerSettings logToCollection(
      String logName, Collection<LoggingEvent> collection) {
    Logger logger = LogManager.getLogger(logName);
    LoggerSettings loggerSettings = new LoggerSettings(logger);
    logger.removeAllAppenders();
    logger.setAdditivity(false);
    CollectionAppender listAppender = new CollectionAppender(collection);
    logger.addAppender(listAppender);
    return loggerSettings;
  }

  /** Capsule for a logger's settings that get mangled by rerouting logging to a collection */
  public static class LoggerSettings {
    private final boolean additive;
    private final List<Appender> appenders;

    /**
     * Read off logger settings from an instance.
     *
     * @param logger The logger to read the settings off from.
     */
    private LoggerSettings(Logger logger) {
      this.additive = logger.getAdditivity();

      Enumeration<?> appenders = logger.getAllAppenders();
      this.appenders = new ArrayList<>();
      while (appenders.hasMoreElements()) {
        Object appender = appenders.nextElement();
        if (appender instanceof Appender) {
          this.appenders.add((Appender) appender);
        } else {
          throw new RuntimeException(
              "getAllAppenders of " + logger + " contained an object that is not an Appender");
        }
      }
    }

    /**
     * Pushes this settings back onto a logger.
     *
     * @param logger the logger on which to push the settings.
     */
    public void pushOntoLogger(Logger logger) {
      logger.setAdditivity(additive);

      logger.removeAllAppenders();
      for (Appender appender : appenders) {
        logger.addAppender(appender);
      }
    }
  }
}
