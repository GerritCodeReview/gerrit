// Copyright (C) 2013 The Android Open Source Project
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

// EE10 (jakarta.servlet) hand-written variant of HiddenErrorHandler. Same FQDN
// (package com.google.gerrit.pgm.http.jetty); only one servlet flavour is on a
// classpath at a time. Excluded from the to_jakarta transform because the
// Jetty-12 ee10 error handler is a core handler (handle(Request, Response,
// Callback) -> boolean, no ee8.nested.ErrorHandler) and Response no longer
// exposes getReason().
package com.google.gerrit.pgm.http.jetty;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.util.http.CacheHeaders;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.eclipse.jetty.ee10.servlet.ServletContextRequest;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.internal.HttpConnection;
import org.eclipse.jetty.util.Callback;

class HiddenErrorHandler extends ErrorHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  public boolean handle(Request request, Response response, Callback callback) throws IOException {
    HttpConnection conn = HttpConnection.getCurrentConnection();
    ServletContextRequest servletContextRequest = Request.as(request, ServletContextRequest.class);
    HttpServletRequest httpServletRequest = servletContextRequest.getServletApiRequest();
    HttpServletResponse httpServletResponse = servletContextRequest.getHttpServletResponse();
    try {
      log(httpServletRequest);
    } finally {
      response
          .getHeaders()
          .put(HttpHeader.CONTENT_TYPE.asString(), "text/plain; charset=ISO-8859-1");
      reply(conn, response, httpServletResponse);
    }
    callback.succeeded();
    return true;
  }

  private void reply(HttpConnection conn, Response response, HttpServletResponse res)
      throws IOException {
    byte[] msg = message(conn, response);
    res.setContentLength(msg.length);
    try {
      CacheHeaders.setNotCacheable(res);
    } finally {
      try (ServletOutputStream out = res.getOutputStream()) {
        out.write(msg);
      }
    }
  }

  private static byte[] message(HttpConnection conn, Response response) {
    // Jetty 12/ee10 Response no longer exposes getReason(); fall back to the
    // standard HTTP status phrase. The "preserve custom reason phrase" path the
    // ee8 variant used does not translate to the core Response API.
    String msg;
    if (conn == null) {
      msg = "";
    } else {
      msg = Strings.nullToEmpty(HttpStatus.getMessage(response.getStatus()));
    }
    return msg.getBytes(ISO_8859_1);
  }

  private static void log(HttpServletRequest req) {
    Throwable err = (Throwable) req.getAttribute("jakarta.servlet.error.exception");
    if (err != null) {
      String uri = req.getRequestURI();
      if (!Strings.isNullOrEmpty(req.getQueryString())) {
        uri += "?" + req.getQueryString();
      }
      logger.atSevere().withCause(err).log("Error in %s %s", req.getMethod(), uri);
    }
  }
}
