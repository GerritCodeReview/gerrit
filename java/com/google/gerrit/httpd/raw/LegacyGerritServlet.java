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

package com.google.gerrit.httpd.raw;

import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.gwtjsonrpc.server.RPCServletUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Redirects from {@code /Gerrit#foo} to {@code /#foo} in JavaScript.
 *
 * <p>This redirect exists to convert the older /Gerrit URL into the more modern URL format which
 * does not use a servlet name for the host page. We cannot do the redirect here in the server side,
 * as it would lose any history token that appears in the URL. Instead we send an HTML page which
 * instructs the browser to replace the URL, but preserve the history token.
 */
@SuppressWarnings("serial")
@Singleton
public class LegacyGerritServlet extends HttpServlet {
  private final byte[] raw;
  private final byte[] compressed;

  @Inject
  LegacyGerritServlet() throws IOException {
    final String pageName = "LegacyGerrit.html";
    final String doc = HtmlDomUtil.readFile(getClass(), pageName);
    if (doc == null) {
      throw new FileNotFoundException("No " + pageName + " in webapp");
    }

    raw = doc.getBytes(HtmlDomUtil.ENC);
    compressed = HtmlDomUtil.compress(raw);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    final byte[] tosend;
    if (RPCServletUtils.acceptsGzipEncoding(req)) {
      rsp.setHeader("Content-Encoding", "gzip");
      tosend = compressed;
    } else {
      tosend = raw;
    }

    CacheHeaders.setNotCacheable(rsp);
    rsp.setContentType("text/html");
    rsp.setCharacterEncoding(HtmlDomUtil.ENC.name());
    rsp.setContentLength(tosend.length);
    try (OutputStream out = rsp.getOutputStream()) {
      out.write(tosend);
    }
  }
}
