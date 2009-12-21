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
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gwt.user.server.rpc.RPCServletUtils;
import com.google.gwtjsonrpc.server.JsonServlet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;
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
  private static final boolean IS_DEV = Boolean.getBoolean("Gerrit.GwtDevMode");

  private final Provider<CurrentUser> currentUser;
  private final GerritConfig config;
  private final Document hostDoc;

  @Inject
  HostPageServlet(final Provider<CurrentUser> cu, final SitePaths site,
      final GerritConfig gc, @GerritServerConfig final Config cfg,
      final ServletContext servletContext) throws IOException {
    currentUser = cu;
    config = gc;

    final String pageName = "HostPage.html";
    hostDoc = HtmlDomUtil.parseFile(getClass(), pageName);
    if (hostDoc == null) {
      throw new FileNotFoundException("No " + pageName + " in webapp");
    }

    if (!IS_DEV) {
      final Element devmode = HtmlDomUtil.find(hostDoc, "gerrit_gwtdevmode");
      if (devmode != null) {
        devmode.getParentNode().removeChild(devmode);
      }
    }

    fixModuleReference(hostDoc, servletContext);
    injectCssFile(hostDoc, "gerrit_sitecss", site.site_css);
    injectXmlFile(hostDoc, "gerrit_header", site.site_header);
    injectXmlFile(hostDoc, "gerrit_footer", site.site_footer);
  }

  private void injectXmlFile(final Document hostDoc, final String id,
      final File src) throws IOException {
    final Element banner = HtmlDomUtil.find(hostDoc, id);
    if (banner == null) {
      return;
    }

    while (banner.getFirstChild() != null) {
      banner.removeChild(banner.getFirstChild());
    }

    Document html = HtmlDomUtil.parseFile(src.getParentFile(), src.getName());
    if (html == null) {
      banner.getParentNode().removeChild(banner);
      return;
    }

    final Element content = html.getDocumentElement();
    banner.appendChild(hostDoc.importNode(content, true));
  }

  private void injectCssFile(final Document hostDoc, final String id,
      final File src) throws IOException {
    final Element banner = HtmlDomUtil.find(hostDoc, id);
    if (banner == null) {
      return;
    }

    while (banner.getFirstChild() != null) {
      banner.removeChild(banner.getFirstChild());
    }

    final String css = HtmlDomUtil.readFile(src.getParentFile(), src.getName());
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
    asScript(scriptNode);
    scriptNode.appendChild(hostDoc.createCDATASection(w.toString()));
  }

  private void asScript(final Element scriptNode) {
    scriptNode.removeAttribute("id");
    scriptNode.setAttribute("type", "text/javascript");
    scriptNode.setAttribute("language", "javascript");
  }

  private void fixModuleReference(final Document hostDoc,
      final ServletContext servletContext) throws IOException {
    final Element scriptNode = HtmlDomUtil.find(hostDoc, "gerrit_module");
    if (scriptNode == null) {
      throw new IOException("No gerrit_module to rewrite in host document");
    }

    String src = "gerrit/gerrit.nocache.js";
    if (!IS_DEV) {
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

      src += "?content=" + ObjectId.fromRaw(md.digest()).name();
    }
    scriptNode.setAttribute("src", src);
    asScript(scriptNode);
  }

  @Override
  protected void doGet(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
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
}
