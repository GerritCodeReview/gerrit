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
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AsyncAppender;

/** Base class for plugin log files, resilient against live Log4j reconfiguration. */
public abstract class PluginLogFile implements LifecycleListener {

  private final SystemLog systemLog;
  private final ServerInformation serverInfo;
  private final String logName;
  private final Layout<?> layout;
  private final Layout<?> jsonLayout;
  private final boolean textLogging;
  private final boolean jsonLogging;

  private final Object lock = new Object();
  private final AtomicBoolean reconfiguring = new AtomicBoolean(false);

  /** Kept for backwards compatibility until all plugins have been updated. */
  @Deprecated
  @InlineMe(replacement = "this(systemLog, serverInfo, logName, layout, null, true, false)")
  public PluginLogFile(
      SystemLog systemLog, ServerInformation serverInfo, String logName, Layout<?> layout) {
    this(systemLog, serverInfo, logName, layout, null, true, false);
  }

  public PluginLogFile(
      SystemLog systemLog,
      ServerInformation serverInfo,
      String logName,
      Layout<?> layout,
      @Nullable Layout<?> jsonLayout,
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
      Layout<?> layout,
      @Nullable Layout<?> jsonLayout,
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

    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    ctx.addPropertyChangeListener(
        evt -> {
          if ("config".equals(evt.getPropertyName())) {
            if (textLogging) {
              initLogger(logName, layout);
            }
            if (jsonLogging) {
              initLogger(logName, ".json", jsonLayout);
            }
          }
        });
  }

  @Override
  public void stop() {
    // stop is called when plugin is unloaded or when the server shuts down.
    // Only clean up when the server is shutting down to prevent issues when a
    // plugin is reloaded. Because loggers are static, unloading the old plugin
    // would remove appenders just created by the new plugin.
    if (serverInfo.getState() == ServerInformation.State.SHUTDOWN) {
      Logger logger = (Logger) LogManager.getLogger(logName);
      logger.getAppenders().values().forEach(a -> a.stop());
      logger.getAppenders().clear();
    }
  }

  private void initLogger(String logName, Layout<?> layout) {
    initLogger(logName, "", layout);
  }

  private void initLogger(String logName, String logFileExtension, Layout<?> layout) {
    synchronized (lock) {
      if (reconfiguring.get()) {
        return;
      }
      reconfiguring.set(true);
      try {
        Logger logger = (Logger) LogManager.getLogger(logName);
        String appenderName = logName + logFileExtension;

        // If already installed and running, skip reinstall
        if (logger.getAppenders().containsKey(appenderName)
            && logger.getAppenders().get(appenderName).isStarted()) {
          return;
        }

        AsyncAppender appender = systemLog.createAsyncAppender(appenderName, layout, true, true);
        logger.addAppender(appender);
        logger.setAdditive(false);

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.updateLoggers();
      } finally {
        reconfiguring.set(false);
      }
    }
  }
}
