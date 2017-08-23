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

package com.google.gerrit.httpd.raw;

import static com.google.gerrit.httpd.HtmlDomUtil.compress;
import static com.google.gerrit.httpd.HtmlDomUtil.newDocument;
import static com.google.gerrit.httpd.HtmlDomUtil.toUTF8;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.eclipse.jgit.util.HttpSupport.HDR_CACHE_CONTROL;
import static org.eclipse.jgit.util.HttpSupport.HDR_EXPIRES;
import static org.eclipse.jgit.util.HttpSupport.HDR_PRAGMA;

import com.google.gerrit.common.Version;
import com.google.gerrit.server.tools.ToolsCatalog;
import com.google.gerrit.server.tools.ToolsCatalog.Entry;
import com.google.gwtjsonrpc.server.RPCServletUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.OutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Sends the client side tools we keep within our software. */
@Singleton
public class ToolServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private final ToolsCatalog toc;

  @Inject
  ToolServlet(ToolsCatalog toc) {
    this.toc = toc;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    Entry ent = toc.get(req.getPathInfo());
    if (ent == null) {
      rsp.sendError(SC_NOT_FOUND);
      return;
    }

    switch (ent.getType()) {
      case FILE:
        doGetFile(ent, rsp);
        break;

      case DIR:
        doGetDirectory(ent, req, rsp);
        break;

      default:
        rsp.sendError(SC_NOT_FOUND);
        break;
    }
  }

  private void doGetFile(Entry ent, HttpServletResponse rsp) throws IOException {
    byte[] tosend = ent.getBytes();

    rsp.setDateHeader(HDR_EXPIRES, 0L);
    rsp.setHeader(HDR_PRAGMA, "no-cache");
    rsp.setHeader(HDR_CACHE_CONTROL, "no-cache, must-revalidate");
    rsp.setContentType("application/octet-stream");
    rsp.setContentLength(tosend.length);
    try (OutputStream out = rsp.getOutputStream()) {
      out.write(tosend);
    }
  }

  private void doGetDirectory(Entry ent, HttpServletRequest req, HttpServletResponse rsp)
      throws IOException {
    String path = "/tools/" + ent.getPath();
    Document page = newDocument();

    Element html = page.createElement("html");
    Element head = page.createElement("head");
    Element title = page.createElement("title");
    Element body = page.createElement("body");

    page.appendChild(html);
    html.appendChild(head);
    html.appendChild(body);
    head.appendChild(title);

    title.setTextContent("Gerrit Code Review - " + path);

    Element h1 = page.createElement("h1");
    h1.setTextContent(title.getTextContent());
    body.appendChild(h1);

    Element ul = page.createElement("ul");
    body.appendChild(ul);

    for (Entry e : ent.getChildren()) {
      String name = e.getName();
      if (e.getType() == Entry.Type.DIR && !name.endsWith("/")) {
        name += "/";
      }

      Element li = page.createElement("li");
      Element a = page.createElement("a");
      a.setAttribute("href", name);
      a.setTextContent(name);
      li.appendChild(a);
      ul.appendChild(li);
    }

    body.appendChild(page.createElement("hr"));

    Element footer = page.createElement("p");
    footer.setAttribute("style", "text-align: right; font-style: italic");
    footer.setTextContent("Powered by Gerrit Code Review " + Version.getVersion());
    body.appendChild(footer);

    byte[] tosend = toUTF8(page);
    if (RPCServletUtils.acceptsGzipEncoding(req)) {
      rsp.setHeader("Content-Encoding", "gzip");
      tosend = compress(tosend);
    }

    rsp.setDateHeader(HDR_EXPIRES, 0L);
    rsp.setHeader(HDR_PRAGMA, "no-cache");
    rsp.setHeader(HDR_CACHE_CONTROL, "no-cache, must-revalidate");
    rsp.setContentType("text/html");
    rsp.setCharacterEncoding(UTF_8.name());
    rsp.setContentLength(tosend.length);
    try (OutputStream out = rsp.getOutputStream()) {
      out.write(tosend);
    }
  }
}
