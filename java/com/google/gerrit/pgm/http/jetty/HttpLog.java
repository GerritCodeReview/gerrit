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

import static com.google.gerrit.httpd.GitOverHttpServlet.GIT_COMMAND_STATUS_HEADER;

import com.google.common.base.Strings;
import com.google.gerrit.httpd.GetUserFilter;
import com.google.gerrit.httpd.RequestMetricsFilter;
import com.google.gerrit.httpd.restapi.LogRedactUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.util.SystemLog;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import org.apache.log4j.AsyncAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jgit.lib.Config;

/** Writes the {@code httpd_log} file with per-request data. */
class HttpLog extends AbstractLifeCycle implements RequestLog {
  private static final Logger log = Logger.getLogger(HttpLog.class);
  private static final String LOG_NAME = "httpd_log";
  private static final String JSON_SUFFIX = ".json";

  interface HttpLogFactory {
    HttpLog get();
  }

  protected static final String P_HOST = "Host";
  protected static final String P_USER = "User";
  protected static final String P_METHOD = "Method";
  protected static final String P_RESOURCE = "Resource";
  protected static final String P_PROTOCOL = "Version";
  protected static final String P_STATUS = "Status";
  protected static final String P_CONTENT_LENGTH = "Content-Length";
  protected static final String P_LATENCY = "Latency";
  protected static final String P_REFERER = "Referer";
  protected static final String P_USER_AGENT = "User-Agent";
  protected static final String P_CPU_TOTAL = "Cpu-Total";
  protected static final String P_CPU_USER = "Cpu-User";
  protected static final String P_MEMORY = "Memory";
  protected static final String P_COMMAND_STATUS = "Command-Status";

  private final AsyncAppender async;

  @Inject
  HttpLog(SystemLog systemLog, @GerritServerConfig Config config) {
    boolean json = config.getBoolean("log", "jsonLogging", false);
    boolean text = config.getBoolean("log", "textLogging", true) || !json;

    async = new AsyncAppender();

    if (text) {
      async.addAppender(systemLog.createAsyncAppender(LOG_NAME, new HttpLogLayout()));
    }

    if (json) {
      async.addAppender(
          systemLog.createAsyncAppender(LOG_NAME + JSON_SUFFIX, new HttpLogJsonLayout()));
    }
  }

  @Override
  protected void doStart() throws Exception {}

  @Override
  protected void doStop() throws Exception {
    async.close();
  }

  @Override
  public void log(Request req, Response rsp) {
    final LoggingEvent event =
        new LoggingEvent( //
            Logger.class.getName(), // fqnOfCategoryClass
            log, // logger
            TimeUtil.nowMs(), // when
            Level.INFO, // level
            "", // message text
            Thread.currentThread().getName(), // thread name
            null, // exception information
            null, // current NDC string
            null, // caller location
            null // MDC properties
            );

    String uri = req.getRequestURI();
    if (!Strings.isNullOrEmpty(req.getQueryString())) {
      uri += "?" + LogRedactUtil.redactQueryString(req.getQueryString());
    }
    String user = (String) req.getAttribute(GetUserFilter.USER_ATTR_KEY);
    if (user != null) {
      event.setProperty(P_USER, user);
    }

    set(event, P_HOST, req.getRemoteAddr());
    set(event, P_METHOD, req.getMethod());
    set(event, P_RESOURCE, uri);
    set(event, P_PROTOCOL, req.getProtocol());
    set(event, P_STATUS, rsp.getStatus());
    set(event, P_CONTENT_LENGTH, rsp.getContentCount());
    set(event, P_LATENCY, System.currentTimeMillis() - req.getTimeStamp());
    set(event, P_REFERER, req.getHeader("Referer"));
    set(event, P_USER_AGENT, req.getHeader("User-Agent"));
    set(event, P_COMMAND_STATUS, rsp.getHeader(GIT_COMMAND_STATUS_HEADER));

    RequestMetricsFilter.Context ctx =
        (RequestMetricsFilter.Context) req.getAttribute(RequestMetricsFilter.METRICS_CONTEXT);
    if (ctx != null) {
      set(event, P_CPU_TOTAL, ctx.getTotalCpuTime());
      set(event, P_CPU_USER, ctx.getUserCpuTime());
      set(event, P_MEMORY, ctx.getAllocatedMemory());
    }

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
}
