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

import static com.google.gerrit.common.FileUtil.lastModified;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Bytes;
import com.google.gerrit.common.Version;
import com.google.gerrit.common.data.HostPageData;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.systemstatus.MessageOfTheDay;
import com.google.gerrit.extensions.webui.WebUiPlugin;
import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gerrit.httpd.auth.AuthorizationPage;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.restapi.account.GetDiffPreferences;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.gwtjsonrpc.server.JsonServlet;
import com.google.gwtjsonrpc.server.RPCServletUtils;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Sends the Gerrit host page to clients. */
@SuppressWarnings("serial")
@Singleton
public class HostPageServlet extends HttpServlet {
  private static final Logger log = LoggerFactory.getLogger(HostPageServlet.class);

  private static final String HPD_ID = "gerrit_hostpagedata";
  private static final int DEFAULT_JS_LOAD_TIMEOUT = 5000;

  private final Provider<CurrentUser> currentUser;
  private final DynamicSet<AuthorizationPage> authPages;
  private final DynamicSet<WebUiPlugin> plugins;
  private final DynamicSet<MessageOfTheDay> messages;
  private final HostPageData.Theme signedOutTheme;
  private final HostPageData.Theme signedInTheme;
  private final SitePaths site;
  private final Document template;
  private final String noCacheName;
  private final boolean refreshHeaderFooter;
  private final SiteStaticDirectoryServlet staticServlet;
  private final boolean isNoteDbEnabled;
  private final Integer pluginsLoadTimeout;
  private final boolean canLoadInIFrame;
  private final GetDiffPreferences getDiff;
  private volatile Page page;

  @Inject
  HostPageServlet(
      Provider<CurrentUser> cu,
      SitePaths sp,
      ThemeFactory themeFactory,
      ServletContext servletContext,
      DynamicSet<AuthorizationPage> authPages,
      DynamicSet<WebUiPlugin> webUiPlugins,
      DynamicSet<MessageOfTheDay> motd,
      @GerritServerConfig Config cfg,
      SiteStaticDirectoryServlet ss,
      NotesMigration migration,
      GetDiffPreferences diffPref)
      throws IOException, ServletException {
    currentUser = cu;
    this.authPages = authPages;
    plugins = webUiPlugins;
    messages = motd;
    signedOutTheme = themeFactory.getSignedOutTheme();
    signedInTheme = themeFactory.getSignedInTheme();
    site = sp;
    refreshHeaderFooter = cfg.getBoolean("site", "refreshHeaderFooter", true);
    staticServlet = ss;
    isNoteDbEnabled = migration.readChanges();
    pluginsLoadTimeout = getPluginsLoadTimeout(cfg);
    canLoadInIFrame = cfg.getBoolean("gerrit", "canLoadInIFrame", false);
    getDiff = diffPref;

    String pageName = "HostPage.html";
    template = HtmlDomUtil.parseFile(getClass(), pageName);
    if (template == null) {
      throw new FileNotFoundException("No " + pageName + " in webapp");
    }

    if (HtmlDomUtil.find(template, "gerrit_module") == null) {
      throw new ServletException("No gerrit_module in " + pageName);
    }
    if (HtmlDomUtil.find(template, HPD_ID) == null) {
      throw new ServletException("No " + HPD_ID + " in " + pageName);
    }

    String src = "gerrit_ui/gerrit_ui.nocache.js";
    try (InputStream in = servletContext.getResourceAsStream("/" + src)) {
      if (in != null) {
        Hasher md = Hashing.murmur3_128().newHasher();
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) > 0) {
          md.putBytes(buf, 0, n);
        }
        src += "?content=" + md.hash().toString();
      } else {
        log.debug("No " + src + " in webapp root; keeping noncache.js URL");
      }
    } catch (IOException e) {
      throw new IOException("Failed reading " + src, e);
    }

    noCacheName = src;
    page = new Page();
  }

  private static int getPluginsLoadTimeout(Config cfg) {
    long cfgValue =
        ConfigUtil.getTimeUnit(
            cfg, "plugins", null, "jsLoadTimeout", DEFAULT_JS_LOAD_TIMEOUT, TimeUnit.MILLISECONDS);
    if (cfgValue < 0) {
      return 0;
    }
    return (int) cfgValue;
  }

  private void json(Object data, StringWriter w) {
    JsonServlet.defaultGsonBuilder().create().toJson(data, w);
  }

  private Page get() {
    Page p = page;
    try {
      if (refreshHeaderFooter && p.isStale()) {
        p = new Page();
        page = p;
      }
    } catch (IOException e) {
      log.error("Cannot refresh site header/footer", e);
    }
    return p;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    Page.Content page = select(req);
    StringWriter w = new StringWriter();
    CurrentUser user = currentUser.get();
    if (user.isIdentifiedUser()) {
      w.write(HPD_ID + ".accountDiffPref=");
      json(getDiffPreferences(user.asIdentifiedUser()), w);
      w.write(";");

      w.write(HPD_ID + ".theme=");
      json(signedInTheme, w);
      w.write(";");
    } else {
      w.write(HPD_ID + ".theme=");
      json(signedOutTheme, w);
      w.write(";");
    }
    plugins(w);
    messages(w);
    authPages(w);

    byte[] hpd = w.toString().getBytes(UTF_8);
    byte[] raw = Bytes.concat(page.part1, hpd, page.part2);
    byte[] tosend;
    if (RPCServletUtils.acceptsGzipEncoding(req)) {
      rsp.setHeader("Content-Encoding", "gzip");
      tosend = HtmlDomUtil.compress(raw);
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

  private DiffPreferencesInfo getDiffPreferences(IdentifiedUser user) {
    try {
      return getDiff.apply(new AccountResource(user));
    } catch (RestApiException
        | ConfigInvalidException
        | IOException
        | PermissionBackendException e) {
      log.warn("Cannot query account diff preferences", e);
    }
    return DiffPreferencesInfo.defaults();
  }

  private void plugins(StringWriter w) {
    List<String> urls = new ArrayList<>();
    for (WebUiPlugin u : plugins) {
      urls.add(String.format("plugins/%s/%s", u.getPluginName(), u.getJavaScriptResourcePath()));
    }
    if (!urls.isEmpty()) {
      w.write(HPD_ID + ".plugins=");
      json(urls, w);
      w.write(";");
    }
  }

  private void messages(StringWriter w) {
    List<HostPageData.Message> list = new ArrayList<>(2);
    for (MessageOfTheDay motd : messages) {
      String html = motd.getHtmlMessage();
      if (!Strings.isNullOrEmpty(html)) {
        HostPageData.Message m = new HostPageData.Message();
        m.id = motd.getMessageId();
        m.redisplay = motd.getRedisplay();
        m.html = html;
        list.add(m);
      }
    }
    if (!list.isEmpty()) {
      w.write(HPD_ID + ".messages=");
      json(list, w);
      w.write(";");
    }
  }

  private void authPages(StringWriter w) {
    Map<String, String> pages = Maps.newHashMap();
    for (AuthorizationPage page : authPages) {
      pages.put(page.getAuthName(), page.getAuthPageContent());
    }
    if (!pages.isEmpty()) {
      w.write(HPD_ID + ".authPages=");
      json(pages, w);
      w.write(";");
    }
  }

  private Page.Content select(HttpServletRequest req) {
    Page pg = get();
    if ("1".equals(req.getParameter("dbg"))) {
      return pg.debug;
    }
    return pg.opt;
  }

  private void insertETags(Element e) {
    if ("img".equalsIgnoreCase(e.getTagName()) || "script".equalsIgnoreCase(e.getTagName())) {
      String src = e.getAttribute("src");
      if (src != null && src.startsWith("static/")) {
        String name = src.substring("static/".length());
        ResourceServlet.Resource r = staticServlet.getResource(name);
        if (r != null) {
          e.setAttribute("src", src + "?e=" + r.etag);
        }
      }
    }

    for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n instanceof Element) {
        insertETags((Element) n);
      }
    }
  }

  private static class FileInfo {
    private final Path path;
    private final long time;

    FileInfo(Path p) {
      path = p;
      time = lastModified(path);
    }

    boolean isStale() {
      return time != lastModified(path);
    }
  }

  private class Page {
    private final FileInfo css;
    private final FileInfo header;
    private final FileInfo footer;
    private final Content opt;
    private final Content debug;

    Page() throws IOException {
      Document hostDoc = HtmlDomUtil.clone(template);

      css = injectCssFile(hostDoc, "gerrit_sitecss", site.site_css);
      header = injectXmlFile(hostDoc, "gerrit_header", site.site_header);
      footer = injectXmlFile(hostDoc, "gerrit_footer", site.site_footer);

      HostPageData pageData = new HostPageData();
      pageData.version = Version.getVersion();
      pageData.isNoteDbEnabled = isNoteDbEnabled;
      pageData.pluginsLoadTimeout = pluginsLoadTimeout;
      pageData.canLoadInIFrame = canLoadInIFrame;

      StringWriter w = new StringWriter();
      w.write("var " + HPD_ID + "=");
      json(pageData, w);
      w.write(";");

      Element data = HtmlDomUtil.find(hostDoc, HPD_ID);
      asScript(data);
      data.appendChild(hostDoc.createTextNode(w.toString()));
      data.appendChild(hostDoc.createComment(HPD_ID));

      Element nocache = HtmlDomUtil.find(hostDoc, "gerrit_module");
      asScript(nocache);
      nocache.removeAttribute("id");
      nocache.setAttribute("src", noCacheName);
      opt = new Content(hostDoc);

      nocache.setAttribute("src", "gerrit_ui/dbg_gerrit_ui.nocache.js");
      debug = new Content(hostDoc);
    }

    boolean isStale() {
      return css.isStale() || header.isStale() || footer.isStale();
    }

    private void asScript(Element scriptNode) {
      scriptNode.setAttribute("type", "text/javascript");
      scriptNode.setAttribute("language", "javascript");
    }

    class Content {
      final byte[] part1;
      final byte[] part2;

      Content(Document hostDoc) throws IOException {
        String raw = HtmlDomUtil.toString(hostDoc);
        int p = raw.indexOf("<!--" + HPD_ID);
        if (p < 0) {
          throw new IOException("No tag in transformed host page HTML");
        }
        part1 = raw.substring(0, p).getBytes(UTF_8);
        part2 = raw.substring(raw.indexOf('>', p) + 1).getBytes(UTF_8);
      }
    }

    private FileInfo injectCssFile(Document hostDoc, String id, Path src) throws IOException {
      FileInfo info = new FileInfo(src);
      Element banner = HtmlDomUtil.find(hostDoc, id);
      if (banner == null) {
        return info;
      }

      while (banner.getFirstChild() != null) {
        banner.removeChild(banner.getFirstChild());
      }

      String css = HtmlDomUtil.readFile(src.getParent(), src.getFileName().toString());
      if (css == null) {
        return info;
      }

      banner.appendChild(hostDoc.createCDATASection("\n" + css + "\n"));
      return info;
    }

    private FileInfo injectXmlFile(Document hostDoc, String id, Path src) throws IOException {
      FileInfo info = new FileInfo(src);
      Element banner = HtmlDomUtil.find(hostDoc, id);
      if (banner == null) {
        return info;
      }

      while (banner.getFirstChild() != null) {
        banner.removeChild(banner.getFirstChild());
      }

      Document html = HtmlDomUtil.parseFile(src);
      if (html == null) {
        return info;
      }

      Element content = html.getDocumentElement();
      insertETags(content);
      banner.appendChild(hostDoc.importNode(content, true));
      return info;
    }
  }
}
