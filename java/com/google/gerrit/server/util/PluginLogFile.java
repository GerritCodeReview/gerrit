// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.util;

import com.google.errorprone.annotations.InlineMe;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.systemstatus.ServerInformation;
import com.google.gerrit.server.config.LogConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.layout.AbstractLayout;

public abstract class PluginLogFile implements LifecycleListener {

  private final SystemLog systemLog;
  private final ServerInformation serverInfo;
  private final String logName;
  private final AbstractLayout<?> layout;
  private final AbstractLayout<?> jsonLayout;
  private final boolean textLogging;
  private final boolean jsonLogging;

  /** Kept for backwards compatibility until all plugins have been updated. */
  @Deprecated
  @InlineMe(replacement = "this(systemLog, serverInfo, logName, layout, null, true, false)")
  public PluginLogFile(
      SystemLog systemLog, ServerInformation serverInfo, String logName, AbstractLayout<?> layout) {
    this(systemLog, serverInfo, logName, layout, null, true, false);
  }

  public PluginLogFile(
      SystemLog systemLog,
      ServerInformation serverInfo,
      String logName,
      AbstractLayout<?> layout,
      @Nullable AbstractLayout<?> jsonLayout,
      LogConfig config) {
    this(
        systemLog,
        serverInfo,
        logName,
        layout,
        jsonLayout,
        config.isTextLogging(),
        config.isJsonLogging());
  }

  public PluginLogFile(
      SystemLog systemLog,
      ServerInformation serverInfo,
      String logName,
      AbstractLayout<?> layout,
      @Nullable AbstractLayout<?> jsonLayout,
      boolean textLogging,
      boolean jsonLogging) {
    this.systemLog = systemLog;
    this.serverInfo = serverInfo;
    this.logName = logName;
    this.layout = layout;
    this.jsonLayout = jsonLayout;
    this.textLogging = textLogging;
    this.jsonLogging = jsonLogging && jsonLayout != null;
  }

  @Override
  public void start() {
    if (textLogging) {
      initLogger(logName, layout);
    }
    if (jsonLogging) {
      initLogger(logName, ".json", jsonLayout);
    }
  }

  @Override
  public void stop() {
    if (serverInfo.getState() == ServerInformation.State.SHUTDOWN) {
      LoggerContext ctx = LoggerContext.getContext(false);
      org.apache.logging.log4j.core.Logger logger =
          ctx.getLogger(logName);
      if (logger != null) {
        for (Appender appender : logger.getAppenders().values()) {
          logger.removeAppender(appender);
        }
      }
    }
  }

  private void initLogger(String logName, AbstractLayout<?> layout) {
    initLogger(logName, "", layout);
  }

  private void initLogger(String logName, String logFileExtension, AbstractLayout<?> layout) {
    LoggerContext ctx = LoggerContext.getContext(false);
    org.apache.logging.log4j.core.Logger logger = ctx.getLogger(logName);
    String appenderName = logName + logFileExtension;

    if (logger.getAppenders().get(appenderName) == null) {
      synchronized (systemLog) {
        if (logger.getAppenders().get(appenderName) == null) {
          Appender appender = systemLog.createAsyncAppender(appenderName, layout, true, true);
          logger.addAppender(appender);
        }
      }
    }
    logger.setAdditive(false);
  }
}
