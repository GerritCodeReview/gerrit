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

package com.google.gerrit.server;

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.rpc.Common;
import com.google.gwt.user.server.rpc.RPCServletUtils;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;

import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.ObjectId;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.security.MessageDigest;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Sends the Gerrit host page to clients. */
@SuppressWarnings("serial")
public class HostPageServlet extends HttpServlet {
  private GerritServer server;
  private String canonicalUrl;
  private byte[] hostPageRaw;
  private byte[] hostPageCompressed;
  private Document hostDoc;

  @Override
  public void init(final ServletConfig config) throws ServletException {
    super.init(config);

    try {
      server = GerritServer.getInstance();
    } catch (OrmException e) {
      throw new ServletException("Cannot load GerritServer", e);
    } catch (XsrfException e) {
      throw new ServletException("Cannot load GerritServer", e);
    }

    final File sitePath = server.getSitePath();
    canonicalUrl = server.getCanonicalURL();

    final String hostPageName = "WEB-INF/Gerrit.html";
    hostDoc = HtmlDomUtil.parseFile(getServletContext(), "/" + hostPageName);
    if (hostDoc == null) {
      throw new ServletException("No " + hostPageName + " in webapp");
    }
    fixModuleReference(hostDoc);
    injectJson(hostDoc, "gerrit_gerritconfig", Common.getGerritConfig());
    injectCssFile(hostDoc, "gerrit_sitecss", sitePath, "GerritSite.css");
    injectXmlFile(hostDoc, "gerrit_header", sitePath, "GerritSiteHeader.html");
    injectXmlFile(hostDoc, "gerrit_footer", sitePath, "GerritSiteFooter.html");

    try {
      final Document anon = HtmlDomUtil.clone(hostDoc);
      injectJson(anon, "gerrit_myaccount", null);
      hostPageRaw = HtmlDomUtil.toUTF8(anon);
      hostPageCompressed = HtmlDomUtil.compress(hostPageRaw);
    } catch (IOException e) {
      throw new ServletException(e.getMessage(), e);
    }
  }

  private void injectXmlFile(final Document hostDoc, final String id,
      final File sitePath, final String fileName) throws ServletException {
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
      final File sitePath, final String fileName) throws ServletException {
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
    GerritJsonServlet.defaultGsonBuilder().create().toJson(obj, w);
    w.write(";\n// -->\n");
    scriptNode.removeAttribute("id");
    scriptNode.setAttribute("type", "text/javascript");
    scriptNode.setAttribute("language", "javascript");
    scriptNode.appendChild(hostDoc.createCDATASection(w.toString()));
  }

  private void fixModuleReference(final Document hostDoc)
      throws ServletException {
    final Element scriptNode = HtmlDomUtil.find(hostDoc, "gerrit_module");
    if (scriptNode == null) {
      throw new ServletException("No gerrit_module to rewrite in host document");
    }
    scriptNode.removeAttribute("id");

    final String src = scriptNode.getAttribute("src");
    InputStream in = getServletContext().getResourceAsStream("/" + src);
    if (in == null) {
      throw new ServletException("No " + src + " in webapp root");
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
      throw new ServletException("Failed reading " + src, e);
    }

    final String vstr = ObjectId.fromRaw(md.digest()).name();
    scriptNode.setAttribute("src", src + "?content=" + vstr);
  }

  @Override
  protected void doGet(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {

    // If we get a request for "/Gerrit/change,1" rewrite it the way
    // it should have been, as "/Gerrit#change,1". This may happen
    // coming out of Google Analytics, where its common to replace
    // the anchor mark ('#') with '/' so it logs independent pages.
    //
    final String screen = req.getPathInfo();
    if (screen != null && screen.length() > 1 && screen.startsWith("/")) {
      final StringBuilder r = new StringBuilder();
      if (canonicalUrl != null) {
        r.append(canonicalUrl);
      } else {
        r.append(GerritServer.serverUrl(req));
      }
      r.append("Gerrit#");
      r.append(screen.substring(1));
      rsp.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
      rsp.setHeader("Location", r.toString());
      return;
    }

    if (canonicalUrl != null
        && !canonicalUrl.equals(GerritServer.serverUrl(req))) {
      rsp.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
      rsp.setHeader("Location", canonicalUrl + "Gerrit");
      return;
    }

    final Account.Id me = new GerritCall(server, req, rsp).getAccountId();
    final Account account = Common.getAccountCache().get(me);
    final byte[] tosend;
    if (account != null) {
      // We know who the user is; embed their account data into the host
      // page to avoid an RPC during module loading.
      //
      final Document peruser = HtmlDomUtil.clone(hostDoc);
      injectJson(peruser, "gerrit_myaccount", account);
      final byte[] raw = HtmlDomUtil.toUTF8(peruser);
      if (RPCServletUtils.acceptsGzipEncoding(req)) {
        rsp.setHeader("Content-Encoding", "gzip");
        tosend = HtmlDomUtil.compress(raw);
      } else {
        tosend = raw;
      }

    } else {
      // User is anonymous (hasn't authenticated with us).
      //
      if (RPCServletUtils.acceptsGzipEncoding(req)) {
        rsp.setHeader("Content-Encoding", "gzip");
        tosend = hostPageCompressed;
      } else {
        tosend = hostPageRaw;
      }
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
}
