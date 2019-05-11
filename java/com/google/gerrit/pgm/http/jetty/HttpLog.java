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

import com.google.common.base.Strings;
import com.google.gerrit.httpd.GetUserFilter;
import com.google.gerrit.httpd.restapi.LogRedactUtil;
import com.google.gerrit.server.util.SystemLog;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
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

/** Writes the {@code httpd_log} file with per-request data. */
class HttpLog extends AbstractLifeCycle implements RequestLog {
  private static final Logger log = LogManager.getLogger(HttpLog.class);
  private static final String LOG_NAME = "httpd_log";

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

  private final AsyncAppender async;

  @Inject
  HttpLog(SystemLog systemLog) {
    Layout<? extends Serializable> layout = new HttpLogLayout();
    async = systemLog.createAsyncAppender(LOG_NAME, layout);
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

    set(map, P_HOST, req.getRemoteAddr());
    set(map, P_METHOD, req.getMethod());
    set(map, P_RESOURCE, uri);
    set(map, P_PROTOCOL, req.getProtocol());
    set(map, P_STATUS, rsp.getStatus());
    set(map, P_CONTENT_LENGTH, rsp.getContentCount());
    set(map, P_LATENCY, System.currentTimeMillis() - req.getTimeStamp());
    set(map, P_REFERER, req.getHeader("Referer"));
    set(map, P_USER_AGENT, req.getHeader("User-Agent"));

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

  private static void set(Map<String, String> map, String key, String val) {
    if (val != null && !val.isEmpty()) {
      map.put(key, val);
    }
  }

  private static void set(Map<String, String> map, String key, long val) {
    if (0 < val) {
      map.put(key, String.valueOf(val));
    }
  }
}
