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
import java.util.Iterator;
import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import java.util.HashMap;
import java.util.Map;
import ch.qos.logback.classic.LoggerContext;

/** Writes the {@code httpd_log} file with per-request data. */
class HttpLog extends AbstractLifeCycle implements RequestLog {
  private static LoggerContext loggerContext = new LoggerContext();
  private static final Logger log = loggerContext.getLogger(HttpLog.class);
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
    async = systemLog.createAsyncAppender(LOG_NAME, new HttpLogLayout());
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
    final LoggingEvent event =
        new LoggingEvent( //
            Logger.class.getName(), // fqnOfCategoryClass
            log, // logger
            Level.INFO, // level
            "", // message text
            null,
            null);
    // thread name
    event.setThreadName("HTTPD");

    String uri = req.getRequestURI();
    uri = redactQueryString(uri, req.getQueryString());

    String user = (String) req.getAttribute(GetUserFilter.REQ_ATTR_KEY);
    if (user != null) {
      Map<String, String> map = new HashMap<>();
      map.put(P_USER, user);
      event.setMDCPropertyMap(map);
    }

    set(event, P_HOST, req.getRemoteAddr());
    set(event, P_METHOD, req.getMethod());
    set(event, P_RESOURCE, uri);
    set(event, P_PROTOCOL, req.getProtocol());
    set(event, P_STATUS, rsp.getStatus());
    set(event, P_CONTENT_LENGTH, rsp.getContentCount());
    set(event, P_REFERER, req.getHeader("Referer"));
    set(event, P_USER_AGENT, req.getHeader("User-Agent"));

    async.doAppend(event);
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

  private static void set(LoggingEvent event, String key, String val) {
    Map<String, String> map = new HashMap<>();
    if (val != null && !val.isEmpty()) {
      map.put(key, val);
      event.setMDCPropertyMap(map);
    }
  }

  private static void set(LoggingEvent event, String key, long val) {
    Map<String, String> map = new HashMap<>();
    if (0 < val) {
      map.put(key, String.valueOf(val));
      event.setMDCPropertyMap(map);
    }
  }
}
