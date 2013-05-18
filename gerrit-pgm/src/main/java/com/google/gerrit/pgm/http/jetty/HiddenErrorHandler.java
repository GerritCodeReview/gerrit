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

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.gwtexpui.server.CacheHeaders;

import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.AbstractHttpConnection;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class HiddenErrorHandler extends ErrorHandler {
  private static final Logger log = LoggerFactory.getLogger(HiddenErrorHandler.class);

  public void handle(String target, Request baseRequest,
      HttpServletRequest req, HttpServletResponse res) throws IOException {
    AbstractHttpConnection conn = AbstractHttpConnection.getCurrentConnection();
    conn.getRequest().setHandled(true);
    try {
      log(req);
    } finally {
      reply(conn, res);
    }
  }

  private void reply(AbstractHttpConnection conn, HttpServletResponse res)
      throws IOException {
    byte[] msg = message(conn);
    res.setHeader(HttpHeaders.CONTENT_TYPE, "text/plain; charset=ISO-8859-1");
    res.setContentLength(msg.length);
    try {
      CacheHeaders.setNotCacheable(res);
    } finally {
      ServletOutputStream out = res.getOutputStream();
      try {
        out.write(msg);
      } finally {
        out.close();
      }
    }
  }

  private static byte[] message(AbstractHttpConnection conn) {
    String msg = conn.getResponse().getReason();
    if (msg == null)
      msg = HttpStatus.getMessage(conn.getResponse().getStatus());
    return msg.getBytes(Charsets.ISO_8859_1);
  }

  private static void log(HttpServletRequest req) {
    Throwable err = (Throwable)req.getAttribute("javax.servlet.error.exception");
    if (err != null) {
      String uri = req.getRequestURI();
      if (!Strings.isNullOrEmpty(req.getQueryString())) {
        uri += "?" + req.getQueryString();
      }
      log.error(String.format("Error in %s %s", req.getMethod(), uri), err);
    }
  }
}
