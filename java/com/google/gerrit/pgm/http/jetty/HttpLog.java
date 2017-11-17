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

import static com.google.gerrit.httpd.restapi.RestApiServlet.XD_AUTHORIZATION;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.httpd.GetUserFilter;
import com.google.gerrit.server.util.SystemLog;
import com.google.inject.Inject;
import java.io.Serializable;
import java.nio.charset.Charset;
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
  private static final ImmutableSet<String> REDACT_PARAM = ImmutableSet.of(XD_AUTHORIZATION);

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
  protected static final String P_REFERER = "Referer";
  protected static final String P_USER_AGENT = "User-Agent";

  private final AsyncAppender async;

  @Inject
  HttpLog(SystemLog systemLog) {
    Layout<? extends Serializable> layout = new HttpLogLayout(Charset.forName("UTF-8"));
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
    uri = redactQueryString(uri, req.getQueryString());

    String user = (String) req.getAttribute(GetUserFilter.REQ_ATTR_KEY);
    if (user != null) {
      map.put(P_USER, user);
    }

    set(map, P_HOST, req.getRemoteAddr());
    set(map, P_METHOD, req.getMethod());
    set(map, P_RESOURCE, uri);
    set(map, P_PROTOCOL, req.getProtocol());
    set(map, P_STATUS, rsp.getStatus());
    set(map, P_CONTENT_LENGTH, rsp.getContentCount());
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

  @VisibleForTesting
  static String redactQueryString(String uri, String qs) {
    if (Strings.isNullOrEmpty(qs)) {
      return uri;
    }

    StringBuilder b = new StringBuilder(uri);
    boolean first = true;
    for (String kvPair : Splitter.on('&').split(qs)) {
      Iterator<String> i = Splitter.on('=').limit(2).split(kvPair).iterator();
      String key = i.next();
      b.append(first ? '?' : '&').append(key);
      first = false;
      if (i.hasNext()) {
        b.append('=');
        if (REDACT_PARAM.contains(Url.decode(key))) {
          b.append('*');
        } else {
          b.append(i.next());
        }
      }
    }
    return b.toString();
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
