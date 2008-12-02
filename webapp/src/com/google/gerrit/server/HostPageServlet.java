// Copyright 2008 Google Inc.
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

import com.google.gwt.user.server.rpc.RPCServletUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Sends the Gerrit host page to clients. */
public class HostPageServlet extends HttpServlet {
  private static final long MAX_AGE = 5 * 60 * 1000L/* milliseconds */;
  private static final String CACHE_CTRL =
      "public, max-age=" + (MAX_AGE / 1000L);

  private byte[] hostPageRaw;
  private byte[] hostPageCompressed;
  private long lastModified;

  @Override
  public void init(final ServletConfig config) throws ServletException {
    super.init(config);

    final String hostPageName = "com/google/gerrit/public/Gerrit.html";
    final Document hostDoc = HtmlDomUtil.parseFile(hostPageName);
    if (hostDoc == null) {
      throw new ServletException("No " + hostPageName + " in CLASSPATH");
    }
    injectCssFile(hostDoc, "gerrit_sitecss", "GerritSite.css");
    injectXmlFile(hostDoc, "gerrit_header", "GerritSiteHeader.html");
    injectXmlFile(hostDoc, "gerrit_footer", "GerritSiteFooter.html");
    try {
      hostPageRaw = HtmlDomUtil.toUTF8(hostDoc);
    } catch (IOException e) {
      throw new ServletException(e.getMessage(), e);
    }
    hostPageCompressed = compress(hostPageRaw);
    lastModified = System.currentTimeMillis();
  }

  private void injectXmlFile(final Document hostDoc, final String id,
      final String fileName) throws ServletException {
    final Element banner = HtmlDomUtil.find(hostDoc, id);
    if (banner == null) {
      return;
    }

    while (banner.getFirstChild() != null) {
      banner.removeChild(banner.getFirstChild());
    }

    final Document bannerHTML = HtmlDomUtil.parseFile(fileName);
    if (bannerHTML == null) {
      banner.getParentNode().removeChild(banner);
      return;
    }

    final Element content = bannerHTML.getDocumentElement();
    banner.appendChild(hostDoc.importNode(content, true));
  }

  private void injectCssFile(final Document hostDoc, final String id,
      final String fileName) throws ServletException {
    final Element banner = HtmlDomUtil.find(hostDoc, id);
    if (banner == null) {
      return;
    }

    while (banner.getFirstChild() != null) {
      banner.removeChild(banner.getFirstChild());
    }

    InputStream in = getClass().getClassLoader().getResourceAsStream(fileName);
    if (in == null) {
      banner.getParentNode().removeChild(banner);
      return;
    }

    final StringWriter w = new StringWriter();
    w.write('\n');
    try {
      try {
        final InputStreamReader r = new InputStreamReader(in, HtmlDomUtil.ENC);
        final char[] buf = new char[512];
        int n;
        while ((n = r.read(buf)) > 0) {
          w.write(buf, 0, n);
        }
      } finally {
        in.close();
      }
    } catch (IOException e) {
      throw new ServletException("Error reading " + fileName, e);
    }
    w.write('\n');

    banner.removeAttribute("id");
    banner.appendChild(hostDoc.createCDATASection(w.toString()));
  }

  private byte[] compress(final byte[] raw) throws ServletException {
    try {
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      final GZIPOutputStream gz = new GZIPOutputStream(out);
      gz.write(raw);
      gz.finish();
      gz.flush();
      return out.toByteArray();
    } catch (IOException e) {
      throw new ServletException("Cannot compress host page", e);
    }
  }

  @Override
  protected long getLastModified(final HttpServletRequest req) {
    return lastModified;
  }

  @Override
  protected void doGet(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    final byte[] tosend;
    if (RPCServletUtils.acceptsGzipEncoding(req)) {
      rsp.setHeader("Content-Encoding", "gzip");
      tosend = hostPageCompressed;
    } else {
      tosend = hostPageRaw;
    }

    rsp.setHeader("Cache-Control", CACHE_CTRL);
    rsp.setDateHeader("Expires", System.currentTimeMillis() + MAX_AGE);
    rsp.setDateHeader("Last-Modified", lastModified);
    rsp.setContentType("text/html");
    rsp.setCharacterEncoding(HtmlDomUtil.ENC);
    rsp.setContentLength(tosend.length);
    final OutputStream out = rsp.getOutputStream();
    try {
      out.write(tosend);
    } finally {
      out.close();
    }
  }
}
