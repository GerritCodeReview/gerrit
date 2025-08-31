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
import com.google.gerrit.httpd.restapi.RestApiServlet;
import com.google.gerrit.server.config.LogConfig;
import com.google.gerrit.server.util.SystemLog;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AsyncAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.util.StringMap;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.component.AbstractLifeCycle;

/** Writes the {@code httpd_log} file with per-request data. */
class HttpLog extends AbstractLifeCycle implements RequestLog {
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
  protected static final String P_TRACE_ID = "Trace-Id";

  private final AsyncAppender async;

  @Inject
  HttpLog(SystemLog systemLog, LogConfig config) {
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    Configuration cfg = ctx.getConfiguration();

    if (config.isTextLogging()) {
      cfg.addAppender(systemLog.createAsyncAppender(LOG_NAME, new HttpLogLayout()));
    }

    if (config.isJsonLogging()) {
      cfg.addAppender(systemLog.createAsyncAppender(LOG_NAME + JSON_SUFFIX, new HttpLogJsonLayout()));
    }

    AppenderRef[] refs =
        new AppenderRef[] {
          AppenderRef.createAppenderRef(LOG_NAME, null, null),
          AppenderRef.createAppenderRef(LOG_NAME + JSON_SUFFIX, null, null)
        };

    async =
        AsyncAppender.newBuilder()
            .setName("HttpAsync")
            .setAppenderRefs(refs)
            .setConfiguration(cfg)
            .build();

    async.start();
    cfg.addAppender(async);
    ctx.updateLoggers();
  }

  @Override
  protected void doStart() throws Exception {}

  @Override
  protected void doStop() throws Exception {
    async.stop();
  }

  @Override
  public void log(Request req, Response rsp) {
    StringMap contextMap = new SortedArrayStringMap();

    String uri = req.getRequestURI();
    if (!Strings.isNullOrEmpty(req.getQueryString())) {
      uri += "?" + LogRedactUtil.redactQueryString(req.getQueryString());
    }

    String user = (String) req.getAttribute(GetUserFilter.USER_ATTR_KEY);
    if (user != null) {
      contextMap.putValue(P_USER, user);
    }

    set(contextMap, P_HOST, req.getRemoteAddr());
    set(contextMap, P_METHOD, req.getMethod());
    set(contextMap, P_RESOURCE, uri);
    set(contextMap, P_PROTOCOL, req.getProtocol());
    set(contextMap, P_STATUS, rsp.getStatus());
    set(contextMap, P_CONTENT_LENGTH, rsp.getContentCount());
    set(contextMap, P_LATENCY, System.currentTimeMillis() - req.getTimeStamp());
    set(contextMap, P_REFERER, req.getHeader("Referer"));
    set(contextMap, P_USER_AGENT, req.getHeader("User-Agent"));
    set(contextMap, P_COMMAND_STATUS, rsp.getHeader(GIT_COMMAND_STATUS_HEADER));

    String traceId = rsp.getHeader(RestApiServlet.X_GERRIT_TRACE);
    if (traceId != null) {
      set(contextMap, P_TRACE_ID, traceId);
    }

    RequestMetricsFilter.Context ctx =
        (RequestMetricsFilter.Context) req.getAttribute(RequestMetricsFilter.METRICS_CONTEXT);
    if (ctx != null) {
      set(contextMap, P_CPU_TOTAL, ctx.getTotalCpuTime());
      set(contextMap, P_CPU_USER, ctx.getUserCpuTime());
      set(contextMap, P_MEMORY, ctx.getAllocatedMemory());
    }

    LogEvent event =
        Log4jLogEvent.newBuilder()
            .setLoggerName(HttpLog.class.getName())
            .setLoggerFqcn(HttpLog.class.getName())
            .setLevel(Level.INFO)
            .setMessage(new SimpleMessage(""))
            .setTimeMillis(TimeUtil.nowMs())
            .setThreadName(Thread.currentThread().getName())
            .setContextData(contextMap)
            .build();

    async.append(event);
  }

  private static void set(StringMap map, String key, String val) {
    if (val != null && !val.isEmpty()) {
      map.putValue(key, val);
    }
  }

  private static void set(StringMap map, String key, long val) {
    if (0 < val) {
      map.putValue(key, String.valueOf(val));
    }
  }
}
