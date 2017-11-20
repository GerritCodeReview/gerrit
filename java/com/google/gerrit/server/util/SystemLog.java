// Copyright (C) 2014 The Android Open Source Project
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import com.google.gerrit.common.Die;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.log4j.Appender;
import org.apache.logging.log4j.core.appender.AsyncAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.LoggingEvent;
import org.eclipse.jgit.lib.Config;
import org.slf4j.LoggerFactory;

@Singleton
public class SystemLog {
  private static final org.slf4j.Logger log = LoggerFactory.getLogger(SystemLog.class);

  public static final String LOG4J_CONFIGURATION = "log4j.configurationFile";

  private final SitePaths site;
  private final Config config;

  @Inject
  public SystemLog(SitePaths site, @GerritServerConfig Config config) {
    this.site = site;
    this.config = config;
  }

  public static boolean shouldConfigure() {
    return Strings.isNullOrEmpty(System.getProperty(LOG4J_CONFIGURATION));
  }

  public static Appender createAppender(Path logdir, String name, Layout layout) {
    Appender dst = RollingFileAppender.newBuilder()
            .withAdvertise(Boolean.parseBoolean(null))
            .withAdvertiseUri(null)
            .withAppend(true)
            .withBufferedIo(null)
            .withBufferSize(null)
            .setConfiguration(this)
            .withFileName(resolve(logdir).resolve(name).toString())
            .withFilePattern(null)
            .withFilter(null)
            .withIgnoreExceptions(true)
            .withImmediateFlush(true)
            .withLayout(layout)
            .withCreateOnDemand(false)
            .withLocking(false)
            .withName(name)
            .withPolicy(null)
            .withStrategy(null)
            .build();
    return dst;
  }

  public AsyncAppender createAsyncAppender(String name, Layout layout) {
    AsyncAppender async = new AsyncAppender();
    async.setName(name);
    async.setBlocking(true);
    async.setBufferSize(config.getInt("core", "asyncLoggingBufferSize", 64));
    async.setLocationInfo(false);

    if (shouldConfigure()) {
      async.addAppender(createAppender(site.logs_dir, name, layout));
    } else {
      Appender appender = LogManager.getLogger(name).getAppender(name);
      if (appender != null) {
        async.addAppender(appender);
      } else {
        log.warn(
            "No appender with the name: " + name + " was found. " + name + " logging is disabled");
      }
    }
    async.activateOptions();
    return async;
  }

  private static Path resolve(Path p) {
    try {
      return p.toRealPath().normalize();
    } catch (IOException e) {
      return p.toAbsolutePath().normalize();
    }
  }

  private static final class DieErrorHandler implements ErrorHandler {
    @Override
    public void error(String message, Exception e, int errorCode, LoggingEvent event) {
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
    public void activateOptions() {}

    @Override
    public void setAppender(Appender appender) {}

    @Override
    public void setBackupAppender(Appender appender) {}

    @Override
    public void setLogger(Logger logger) {}
  }
}
