// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.pgm.http.jetty;

import com.google.gerrit.httpd.GetUserFilter;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.util.LogUtil;
import com.google.gerrit.server.util.TimeUtil;

import org.apache.log4j.Appender;
import org.apache.log4j.AsyncAppender;
import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.LoggingEvent;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jgit.lib.Config;

import java.io.File;
import java.io.IOException;

/** Writes the {@code httpd_log} file with per-request data. */
class HttpLog extends AbstractLifeCycle implements RequestLog {
  private static final Logger log = Logger.getLogger(HttpLog.class);
  private static final String LOG_NAME = "httpd_log";

  protected static final String P_HOST = "Host";
  protected static final String P_USER = "User";
  protected static final String P_METHOD = "Method";
  protected static final String P_RESOURCE = "Resource";
  protected static final String P_PROTOCOL = "Version";
  protected static final String P_STATUS = "Status";
  protected static final String P_CONTENT_LENGTH = "Content-Length";
  protected static final String P_REFERER = "Referer";
  protected static final String P_USER_AGENT = "User-Agent";

  private final AsyncAppender async;

  HttpLog(final SitePaths site, final Config config) {
    async = new AsyncAppender();
    async.setBlocking(true);
    async.setBufferSize(config.getInt("core", "asyncLoggingBufferSize", 64));
    async.setLocationInfo(false);
    if (LogUtil.shouldConfigureLogSystem()) {
      final DailyRollingFileAppender dst = new DailyRollingFileAppender();
      dst.setName(LOG_NAME);
      dst.setLayout(new HttpLogLayout());
      dst.setEncoding("UTF-8");
      dst.setFile(new File(resolve(site.logs_dir), LOG_NAME).getPath());
      dst.setImmediateFlush(true);
      dst.setAppend(true);
      dst.setThreshold(Level.INFO);
      dst.setErrorHandler(new DieErrorHandler());
      dst.activateOptions();
      dst.setErrorHandler(new LogLogHandler());
      async.addAppender(dst);
    } else {
      Appender appender = log.getAppender(LOG_NAME);
      if (appender != null) {
        async.addAppender(appender);
      } else {
        log.warn("No appender with the name: "
            + LOG_NAME
            + " was found. HTTPD logging is disabled");
      }
    }
    async.activateOptions();
  }

  @Override
  protected void doStart() throws Exception {
  }

  @Override
  protected void doStop() throws Exception {
    async.close();
  }

  @Override
  public void log(final Request req, final Response rsp) {
    final LoggingEvent event = new LoggingEvent( //
        Logger.class.getName(), // fqnOfCategoryClass
        log, // logger
        TimeUtil.nowMs(), // when
        Level.INFO, // level
        "", // message text
        "HTTPD", // thread name
        null, // exception information
        null, // current NDC string
        null, // caller location
        null // MDC properties
        );

    String uri = req.getRequestURI();
    String qs = req.getQueryString();
    if (qs != null) {
      uri = uri + "?" + qs;
    }

    String user = (String) req.getAttribute(GetUserFilter.REQ_ATTR_KEY);
    if (user != null) {
      event.setProperty(P_USER, user);
    }

    set(event, P_HOST, req.getRemoteAddr());
    set(event, P_METHOD, req.getMethod());
    set(event, P_RESOURCE, uri);
    set(event, P_PROTOCOL, req.getProtocol());
    set(event, P_STATUS, rsp.getStatus());
    set(event, P_CONTENT_LENGTH, rsp.getContentCount());
    set(event, P_REFERER, req.getHeader("Referer"));
    set(event, P_USER_AGENT, req.getHeader("User-Agent"));

    async.append(event);
  }

  private static void set(LoggingEvent event, String key, String val) {
    if (val != null && !val.isEmpty()) {
      event.setProperty(key, val);
    }
  }

  private static void set(LoggingEvent event, String key, long val) {
    if (0 < val) {
      event.setProperty(key, String.valueOf(val));
    }
  }

  private static File resolve(final File logs_dir) {
    try {
      return logs_dir.getCanonicalFile();
    } catch (IOException e) {
      return logs_dir.getAbsoluteFile();
    }
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
      throw new RuntimeException("Cannot open log file: " + message);
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

  private static final class LogLogHandler implements ErrorHandler {
    @Override
    public void error(String message, Exception e, int errorCode,
        LoggingEvent event) {
      log.error(message, e);
    }

    @Override
    public void error(String message, Exception e, int errorCode) {
      log.error(message, e);
    }

    @Override
    public void error(String message) {
      log.error(message);
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
