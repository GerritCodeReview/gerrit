// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class PrettifyServlet extends HttpServlet {
  private static final String VERSION = "20090521";

  private byte[] content;

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);

    final String myDir = "/gerrit/prettify" + VERSION + "/";
    final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    load(buffer, myDir + "prettify.js");
    for (Object p : getServletContext().getResourcePaths(myDir)) {
      String name = (String) p;
      if (name.startsWith(myDir + "lang-") && name.endsWith(".js")) {
        load(buffer, name);
      }
    }
    content = buffer.toByteArray();
  }

  private void load(final OutputStream buffer, final String path) {
    final InputStream in = getServletContext().getResourceAsStream(path);
    if (in != null) {
      try {
        final byte[] tmp = new byte[4096];
        int cnt;
        while ((cnt = in.read(tmp)) > 0) {
          buffer.write(tmp, 0, cnt);
        }
        buffer.write(';');
        buffer.write('\n');
        in.close();
      } catch (IOException e) {
        getServletContext().log("Cannot read " + path, e);
      }
    }
  }

  @Override
  protected void doGet(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    final String want = req.getPathInfo();
    if (want.equals("/" + VERSION + ".js")) {
      final long now = System.currentTimeMillis();
      rsp.setHeader("Cache-Control", "max-age=31536000,public");
      rsp.setDateHeader("Expires", now + 31536000000L);
      rsp.setDateHeader("Date", now);
      rsp.setContentType("application/x-javascript");
      rsp.setContentLength(content.length);
      rsp.getOutputStream().write(content);
    } else {
      rsp.setHeader("Expires", "Fri, 01 Jan 1980 00:00:00 GMT");
      rsp.setHeader("Pragma", "no-cache");
      rsp.setHeader("Cache-Control", "no-cache, must-revalidate");
      rsp.setDateHeader("Date", System.currentTimeMillis());
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
  }
}
