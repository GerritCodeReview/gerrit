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

package com.google.gerrit.pgm.http.jetty;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

import com.google.common.base.Strings;
import com.google.gwtexpui.server.CacheHeaders;
import java.io.IOException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class HiddenErrorHandler extends ErrorHandler {
  private static final Logger log = LoggerFactory.getLogger(HiddenErrorHandler.class);

  @Override
  public void handle(
      String target, Request baseRequest, HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    HttpConnection conn = HttpConnection.getCurrentConnection();
    baseRequest.setHandled(true);
    try {
      log(req);
    } finally {
      reply(conn, res);
    }
  }

  private void reply(HttpConnection conn, HttpServletResponse res) throws IOException {
    byte[] msg = message(conn);
    res.setHeader(HttpHeader.CONTENT_TYPE.asString(), "text/plain; charset=ISO-8859-1");
    res.setContentLength(msg.length);
    try {
      CacheHeaders.setNotCacheable(res);
    } finally {
      try (ServletOutputStream out = res.getOutputStream()) {
        out.write(msg);
      }
    }
  }

  private static byte[] message(HttpConnection conn) {
    String msg;
    if (conn == null) {
      msg = "";
    } else {
      msg = conn.getHttpChannel().getResponse().getReason();
      if (msg == null) {
        msg = HttpStatus.getMessage(conn.getHttpChannel().getResponse().getStatus());
      }
    }
    return msg.getBytes(ISO_8859_1);
  }

  private static void log(HttpServletRequest req) {
    Throwable err = (Throwable) req.getAttribute("javax.servlet.error.exception");
    if (err != null) {
      String uri = req.getRequestURI();
      if (!Strings.isNullOrEmpty(req.getQueryString())) {
        uri += "?" + req.getQueryString();
      }
      log.error(String.format("Error in %s %s", req.getMethod(), uri), err);
    }
  }
}
