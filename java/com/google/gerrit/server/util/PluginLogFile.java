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

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.systemstatus.ServerInformation;
<<<<<<< PATCH SET (e78401 Migrate to log4j2)
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.config.LoggerConfig;
=======
import org.apache.log4j.Layout;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')

public abstract class PluginLogFile implements LifecycleListener {

  private final SystemLog systemLog;
  private final ServerInformation serverInfo;
  private final String logName;
  private final Layout layout;

  public PluginLogFile(
      SystemLog systemLog, ServerInformation serverInfo, String logName, Layout layout) {
    this.systemLog = systemLog;
    this.serverInfo = serverInfo;
    this.logName = logName;
    this.layout = layout;
  }

  @Override
  public void start() {
<<<<<<< PATCH SET (e78401 Migrate to log4j2)
    Appender asyncAppender = systemLog.createAsyncAppender(logName, layout, true, true);
    LoggerConfig logger = new LoggerConfig(logName, null, false);
    logger.removeAppender(logName);
    if (asyncAppender != null) {
      logger.addAppender(asyncAppender, null, null);
      logger.setAdditive(false);
    }
=======
    Logger logger = LogManager.getLogger(logName);
    if (logger.getAppender(logName) == null) {
      synchronized (systemLog) {
        if (logger.getAppender(logName) == null) {
          logger.addAppender(systemLog.createAsyncAppender(logName, layout, true, true));
        }
      }
    }
    logger.setAdditivity(false);
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')
  }

  @Override
  public void stop() {
    // stop is called when plugin is unloaded or when the server shutdown.
    // Only clean up when the server is shutting down to prevent issue when a
    // plugin is reloaded. The issue is that gerrit load the new plugin and then
    // unload the old one so because loggers are static, the unload of the old
    // plugin would remove the appenders just created by the new plugin.
    if (serverInfo.getState() == ServerInformation.State.SHUTDOWN) {
      LoggerConfig logger = new LoggerConfig(logName, null, false);
      for (Appender appender : logger.getAppenders().values()) {
        logger.removeAppender(appender.toString());
      }
    }
  }
}
