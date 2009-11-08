// Copyright (C) 2008 The Android Open Source Project
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

import com.google.gerrit.common.data.GerritConfig;
import com.google.gerrit.common.data.HostPageData;
import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.Nullable;
import com.google.gerrit.server.config.SitePath;
import com.google.gwt.user.server.rpc.RPCServletUtils;
import com.google.gwtjsonrpc.server.JsonServlet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.security.MessageDigest;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Sends the Gerrit host page to clients. */
@SuppressWarnings("serial")
@Singleton
public class HostPageServlet extends HttpServlet {
  private final Provider<CurrentUser> currentUser;
  private final File sitePath;
  private final GerritConfig config;
  private final Provider<String> urlProvider;
  private final boolean wantSSL;
  private final Document hostDoc;

  @Inject
  HostPageServlet(final Provider<CurrentUser> cu, @SitePath final File path,
      final GerritConfig gc,
      @CanonicalWebUrl @Nullable final Provider<String> up,
      @CanonicalWebUrl @Nullable final String configuredUrl,
      final ServletContext servletContext) throws IOException {
    currentUser = cu;
    urlProvider = up;
    sitePath = path;
    config = gc;
    wantSSL = configuredUrl != null && configuredUrl.startsWith("https:");

    final String pageName = "HostPage.html";
    hostDoc = HtmlDomUtil.parseFile(getClass(), pageName);
    if (hostDoc == null) {
      throw new FileNotFoundException("No " + pageName + " in webapp");
    }
    fixModuleReference(hostDoc, servletContext);
    injectCssFile(hostDoc, "gerrit_sitecss", sitePath, "GerritSite.css");
    injectXmlFile(hostDoc, "gerrit_header", sitePath, "GerritSiteHeader.html");
    injectXmlFile(hostDoc, "gerrit_footer", sitePath, "GerritSiteFooter.html");
  }

  private void injectXmlFile(final Document hostDoc, final String id,
      final File sitePath, final String fileName) throws IOException {
    final Element banner = HtmlDomUtil.find(hostDoc, id);
    if (banner == null) {
      return;
    }

    while (banner.getFirstChild() != null) {
      banner.removeChild(banner.getFirstChild());
    }

    final Document html = HtmlDomUtil.parseFile(sitePath, fileName);
    if (html == null) {
      banner.getParentNode().removeChild(banner);
      return;
    }

    final Element content = html.getDocumentElement();
    banner.appendChild(hostDoc.importNode(content, true));
  }

  private void injectCssFile(final Document hostDoc, final String id,
      final File sitePath, final String fileName) throws IOException {
    final Element banner = HtmlDomUtil.find(hostDoc, id);
    if (banner == null) {
      return;
    }

    while (banner.getFirstChild() != null) {
      banner.removeChild(banner.getFirstChild());
    }

    final String css = HtmlDomUtil.readFile(sitePath, fileName);
    if (css == null) {
      banner.getParentNode().removeChild(banner);
      return;
    }

    banner.removeAttribute("id");
    banner.appendChild(hostDoc.createCDATASection("\n" + css + "\n"));
  }

  private void injectJson(final Document hostDoc, final String id,
      final Object obj) {
    final Element scriptNode = HtmlDomUtil.find(hostDoc, id);
    if (scriptNode == null) {
      return;
    }

    while (scriptNode.getFirstChild() != null) {
      scriptNode.removeChild(scriptNode.getFirstChild());
    }

    if (obj == null) {
      scriptNode.getParentNode().removeChild(scriptNode);
      return;
    }

    final StringWriter w = new StringWriter();
    w.write("<!--\n");
    w.write("var ");
    w.write(id);
    w.write("_obj=");
    JsonServlet.defaultGsonBuilder().create().toJson(obj, w);
    w.write(";\n// -->\n");
    scriptNode.removeAttribute("id");
    scriptNode.setAttribute("type", "text/javascript");
    scriptNode.setAttribute("language", "javascript");
    scriptNode.appendChild(hostDoc.createCDATASection(w.toString()));
  }

  private void fixModuleReference(final Document hostDoc,
      final ServletContext servletContext) throws IOException {
    final Element scriptNode = HtmlDomUtil.find(hostDoc, "gerrit_module");
    if (scriptNode == null) {
      throw new IOException("No gerrit_module to rewrite in host document");
    }
    scriptNode.removeAttribute("id");

    final String src = scriptNode.getAttribute("src");
    InputStream in = servletContext.getResourceAsStream("/" + src);
    if (in == null) {
      throw new IOException("No " + src + " in webapp root");
    }

    final MessageDigest md = Constants.newMessageDigest();
    try {
      try {
        final byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) > 0) {
          md.update(buf, 0, n);
        }
      } finally {
        in.close();
      }
    } catch (IOException e) {
      throw new IOException("Failed reading " + src, e);
    }

    final String vstr = ObjectId.fromRaw(md.digest()).name();
    scriptNode.setAttribute("src", src + "?content=" + vstr);
  }

  @Override
  protected void doGet(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    // If we wanted SSL, but the user didn't come to us over an SSL channel,
    // force it to be SSL by issuing a protocol redirect. Try to keep the
    // name "localhost" in case this is an SSH port tunnel.
    //
    if (wantSSL && !isSecure(req)) {
      final StringBuffer reqUrl = req.getRequestURL();
      if (isLocalHost(req)) {
        reqUrl.replace(0, reqUrl.indexOf(":"), "https");
      } else {
        reqUrl.setLength(0);
        reqUrl.append(urlProvider.get());
      }
      rsp.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
      rsp.setHeader("Location", reqUrl.toString());
      return;
    }

    final HostPageData pageData = new HostPageData();
    pageData.config = config;

    final CurrentUser user = currentUser.get();
    if (user instanceof IdentifiedUser) {
      pageData.userAccount = ((IdentifiedUser) user).getAccount();
    }

    final Document peruser = HtmlDomUtil.clone(hostDoc);
    injectJson(peruser, "gerrit_hostpagedata", pageData);

    final byte[] raw = HtmlDomUtil.toUTF8(peruser);
    final byte[] tosend;
    if (RPCServletUtils.acceptsGzipEncoding(req)) {
      rsp.setHeader("Content-Encoding", "gzip");
      tosend = HtmlDomUtil.compress(raw);
    } else {
      tosend = raw;
    }

    rsp.setHeader("Expires", "Fri, 01 Jan 1980 00:00:00 GMT");
    rsp.setHeader("Pragma", "no-cache");
    rsp.setHeader("Cache-Control", "no-cache, must-revalidate");
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

  private static boolean isSecure(final HttpServletRequest req) {
    return "https".equals(req.getScheme()) || req.isSecure();
  }

  private static boolean isLocalHost(final HttpServletRequest req) {
    return "localhost".equals(req.getServerName())
        || "127.0.0.1".equals(req.getServerName());
  }
}
