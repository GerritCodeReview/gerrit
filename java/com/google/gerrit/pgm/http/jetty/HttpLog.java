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
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AsyncAppender;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jgit.lib.Config;

/** Writes the {@code httpd_log} file with per-request data. */
class HttpLog extends AbstractLifeCycle implements RequestLog {
  private static final Logger log = LogManager.getLogger(HttpLog.class);
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

  private AsyncAppender async;

  @Inject
  HttpLog(SystemLog systemLog, @GerritServerConfig Config config) {
    boolean json = config.getBoolean("log", "jsonLogging", false);
    boolean text = config.getBoolean("log", "textLogging", true) || !json;

    if (text) {
      Layout<? extends Serializable> httpLayout1 = new HttpLogLayout();
      async = systemLog.createAsyncAppender(LOG_NAME, httpLayout1);
    }

    /*if (json) {
      Layout<? extends Serializable> httpLayout2 = new HttpLogLayout();
      layout2 = systemLog.createAsyncAppender(LOG_NAME + JSON_SUFFIX, httpLayout2);

      async.addAppender(layout2);
    }*/
  }

  @Override
  protected void doStart() throws Exception {}

  @Override
  protected void doStop() throws Exception {
    if (async != null) {
      async.stop();
    }
  }

  @Override
  public void log(Request req, Response rsp) {
    Map<String, String> map = new HashMap<>();

    String uri = req.getRequestURI();
    if (!Strings.isNullOrEmpty(req.getQueryString())) {
      uri += "?" + LogRedactUtil.redactQueryString(req.getQueryString());
    }
    String user = (String) req.getAttribute(GetUserFilter.USER_ATTR_KEY);
    if (user != null) {
      map.put(P_USER, user);
    }

<<<<<<< PATCH SET (e78401 Migrate to log4j2)
    map.putAll(set(P_HOST, req.getRemoteAddr()));
    map.putAll(set(P_METHOD, req.getMethod()));
    map.putAll(set(P_RESOURCE, uri));
    map.putAll(set(P_PROTOCOL, req.getProtocol()));
    map.putAll(set(P_STATUS, rsp.getStatus()));
    map.putAll(set(P_CONTENT_LENGTH, rsp.getContentCount()));
    map.putAll(set(P_LATENCY, System.currentTimeMillis() - req.getTimeStamp()));
    map.putAll(set(P_REFERER, req.getHeader("Referer")));
    map.putAll(set(P_USER_AGENT, req.getHeader("User-Agent")));
=======
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
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')


    final LogEvent event =
        Log4jLogEvent.newBuilder()
            .setLoggerName(log.toString())
            .setLoggerFqcn(Logger.class.getName())
            .setLevel(Level.INFO)
            .setMessage(new SimpleMessage(""))
            .setThreadName("HTTPD")
            .setTimeMillis(TimeUtil.nowMs())
            .setContextMap(map)
            .build();

    if (async != null) {
      async.append(event);
    }
  }

  private HashMap<String, String> set(String key, String val) {
    HashMap<String, String> map = new HashMap<>();
    if (val != null && !val.isEmpty()) {
      map.put(key, val);
    }
    return map;
  }

  private HashMap<String, String> set(String key, long val) {
    HashMap<String, String> map = new HashMap<>();
    if (0 < val) {
      map.put(key, String.valueOf(val));
    }
    return map;
  }
}
