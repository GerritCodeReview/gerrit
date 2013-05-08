// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.httpd.plugins;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.httpd.restapi.RestApiServlet;
import com.google.gerrit.server.MimeUtilFileTypeRegistry;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.documentation.MarkdownFormatter;
import com.google.gerrit.server.plugins.Plugin;
import com.google.gerrit.server.plugins.PluginsCollection;
import com.google.gerrit.server.plugins.ReloadPluginListener;
import com.google.gerrit.server.plugins.StartPluginListener;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.servlet.GuiceFilter;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

@Singleton
class HttpPluginServlet extends HttpServlet
    implements StartPluginListener, ReloadPluginListener {
  private static final int SMALL_RESOURCE = 128 * 1024;
  private static final long serialVersionUID = 1L;
  private static final Logger log
      = LoggerFactory.getLogger(HttpPluginServlet.class);

  private final MimeUtilFileTypeRegistry mimeUtil;
  private final Provider<String> webUrl;
  private final Cache<ResourceKey, Resource> resourceCache;
  private final String sshHost;
  private final int sshPort;
  private final RestApiServlet managerApi;

  private List<Plugin> pending = Lists.newArrayList();
  private String base;
  private final ConcurrentMap<String, PluginHolder> plugins
      = Maps.newConcurrentMap();

  @Inject
  HttpPluginServlet(MimeUtilFileTypeRegistry mimeUtil,
      @CanonicalWebUrl Provider<String> webUrl,
      @Named(HttpPluginModule.PLUGIN_RESOURCES) Cache<ResourceKey, Resource> cache,
      @GerritServerConfig Config cfg,
      SshInfo sshInfo,
      RestApiServlet.Globals globals,
      PluginsCollection plugins) {
    this.mimeUtil = mimeUtil;
    this.webUrl = webUrl;
    this.resourceCache = cache;
    this.managerApi = new RestApiServlet(globals, plugins);

    String sshHost = "review.example.com";
    int sshPort = 29418;
    if (!sshInfo.getHostKeys().isEmpty()) {
      String host = sshInfo.getHostKeys().get(0).getHost();
      int c = host.lastIndexOf(':');
      if (0 <= c) {
        sshHost = host.substring(0, c);
        sshPort = Integer.parseInt(host.substring(c+1));
      } else {
        sshHost = host;
        sshPort = 22;
      }
    }
    this.sshHost = sshHost;
    this.sshPort = sshPort;
  }

  @Override
  public synchronized void init(ServletConfig config) throws ServletException {
    super.init(config);

    String path = config.getServletContext().getContextPath();
    base = Strings.nullToEmpty(path) + "/plugins/";
    for (Plugin plugin : pending) {
      install(plugin);
    }
    pending = null;
  }

  @Override
  public synchronized void onStartPlugin(Plugin plugin) {
    if (pending != null) {
      pending.add(plugin);
    } else {
      install(plugin);
    }
  }

  @Override
  public void onReloadPlugin(Plugin oldPlugin, Plugin newPlugin) {
    install(newPlugin);
  }

  private void install(Plugin plugin) {
    GuiceFilter filter = load(plugin);
    final String name = plugin.getName();
    final PluginHolder holder = new PluginHolder(plugin, filter);
    plugin.add(new RegistrationHandle() {
      @Override
      public void remove() {
        plugins.remove(name, holder);
      }
    });
    plugins.put(name, holder);
  }

  private GuiceFilter load(Plugin plugin) {
    if (plugin.getHttpInjector() != null) {
      final String name = plugin.getName();
      final GuiceFilter filter;
      try {
        filter = plugin.getHttpInjector().getInstance(GuiceFilter.class);
      } catch (RuntimeException e) {
        log.warn(String.format("Plugin %s cannot load GuiceFilter", name), e);
        return null;
      }

      try {
        ServletContext ctx = PluginServletContext.create(plugin, base + name);
        filter.init(new WrappedFilterConfig(ctx));
      } catch (ServletException e) {
        log.warn(String.format("Plugin %s failed to initialize HTTP", name), e);
        return null;
      }

      plugin.add(new RegistrationHandle() {
        @Override
        public void remove() {
          filter.destroy();
        }
      });
      return filter;
    }
    return null;
  }

  @Override
  public void service(HttpServletRequest req, HttpServletResponse res)
      throws IOException, ServletException {
    List<String> parts = Lists.newArrayList(
      Splitter.on('/').limit(3).omitEmptyStrings()
        .split(Strings.nullToEmpty(req.getPathInfo())));

    if (isApiCall(req, parts)) {
      managerApi.service(req, res);
      return;
    }

    String name = parts.get(0);
    final PluginHolder holder = plugins.get(name);
    if (holder == null) {
      CacheHeaders.setNotCacheable(res);
      res.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    WrappedRequest wr = new WrappedRequest(req, base + name);
    FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest req, ServletResponse res)
          throws IOException {
        onDefault(holder, (HttpServletRequest) req, (HttpServletResponse) res);
      }
    };
    if (holder.filter != null) {
      holder.filter.doFilter(wr, res, chain);
    } else {
      chain.doFilter(wr, res);
    }
  }

  private static boolean isApiCall(HttpServletRequest req, List<String> parts) {
    String method = req.getMethod();
    int cnt = parts.size();
    return cnt == 0
        || (cnt == 1 && ("PUT".equals(method) || "DELETE".equals(method)))
        || (cnt == 2 && parts.get(1).startsWith("gerrit~"));
  }

  private void onDefault(PluginHolder holder,
      HttpServletRequest req,
      HttpServletResponse res) throws IOException {
    if (!"GET".equals(req.getMethod()) && !"HEAD".equals(req.getMethod())) {
      CacheHeaders.setNotCacheable(res);
      res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
      return;
    }

    String uri = req.getRequestURI();
    String ctx = req.getContextPath();
    if (uri.length() <= ctx.length()) {
      Resource.NOT_FOUND.send(req, res);
      return;
    }

    String file = uri.substring(ctx.length() + 1);
    ResourceKey key = new ResourceKey(holder.plugin, file);
    Resource rsc = resourceCache.getIfPresent(key);
    if (rsc != null) {
      rsc.send(req, res);
      return;
    }

    if ("".equals(file)) {
      res.sendRedirect(uri + holder.docPrefix + "index.html");
      return;
    }

    if (file.startsWith(holder.staticPrefix)) {
      JarFile jar = holder.plugin.getJarFile();
      JarEntry entry = jar.getJarEntry(file);
      if (exists(entry)) {
        sendResource(jar, entry, key, res);
      } else {
        resourceCache.put(key, Resource.NOT_FOUND);
        Resource.NOT_FOUND.send(req, res);
      }
    } else if (file.equals(
        holder.docPrefix.substring(0, holder.docPrefix.length() - 1))) {
      res.sendRedirect(uri + "/index.html");
    } else if (file.startsWith(holder.docPrefix) && file.endsWith("/")) {
      res.sendRedirect(uri + "index.html");
    } else if (file.startsWith(holder.docPrefix)) {
      JarFile jar = holder.plugin.getJarFile();
      JarEntry entry = jar.getJarEntry(file);
      if (!exists(entry)) {
        entry = findSource(jar, file);
      }
      if (!exists(entry) && file.endsWith("/index.html")) {
        String pfx = file.substring(0, file.length() - "index.html".length());
        sendAutoIndex(jar, pfx, holder.plugin.getName(), key, res);
      } else if (exists(entry) && entry.getName().endsWith(".md")) {
        sendMarkdownAsHtml(jar, entry, holder.plugin.getName(), key, res);
      } else if (exists(entry)) {
        sendResource(jar, entry, key, res);
      } else {
        resourceCache.put(key, Resource.NOT_FOUND);
        Resource.NOT_FOUND.send(req, res);
      }
    } else {
      resourceCache.put(key, Resource.NOT_FOUND);
      Resource.NOT_FOUND.send(req, res);
    }
  }

  private void sendAutoIndex(JarFile jar,
      String prefix, String pluginName,
      ResourceKey cacheKey, HttpServletResponse res) throws IOException {
    List<JarEntry> cmds = Lists.newArrayList();
    List<JarEntry> docs = Lists.newArrayList();
    Enumeration<JarEntry> entries = jar.entries();
    while (entries.hasMoreElements()) {
      JarEntry entry = entries.nextElement();
      String name = entry.getName();
      long size = entry.getSize();
      if (name.startsWith(prefix)
          && (name.endsWith(".md")
              || name.endsWith(".html"))
          && 0 < size && size <= SMALL_RESOURCE) {
        if (name.substring(prefix.length()).startsWith("cmd-")) {
          cmds.add(entry);
        } else {
          docs.add(entry);
        }
      }
    }
    Collections.sort(cmds, new Comparator<JarEntry>() {
      @Override
      public int compare(JarEntry a, JarEntry b) {
        return a.getName().compareTo(b.getName());
      }
    });
    Collections.sort(docs, new Comparator<JarEntry>() {
      @Override
      public int compare(JarEntry a, JarEntry b) {
        return a.getName().compareTo(b.getName());
      }
    });

    StringBuilder md = new StringBuilder();
    md.append(String.format("# Plugin %s #\n", pluginName));
    md.append("\n");
    appendPluginInfoTable(md, jar.getManifest().getMainAttributes());

    if (!docs.isEmpty()) {
      md.append("## Documentation ##\n");
      for(JarEntry entry : docs) {
        String rsrc = entry.getName().substring(prefix.length());
        String title;
        if (rsrc.endsWith(".html")) {
          title = rsrc.substring(0, rsrc.length() - 5).replace('-', ' ');
        } else if (rsrc.endsWith(".md")) {
          title = extractTitleFromMarkdown(jar, entry);
          if (Strings.isNullOrEmpty(title)) {
            title = rsrc.substring(0, rsrc.length() - 3).replace('-', ' ');
          }
          rsrc = rsrc.substring(0, rsrc.length() - 3) + ".html";
        } else {
          title = rsrc.replace('-', ' ');
        }
        md.append(String.format("* [%s](%s)\n", title, rsrc));
      }
      md.append("\n");
    }

    if (!cmds.isEmpty()) {
      md.append("## Commands ##\n");
      for(JarEntry entry : cmds) {
        String rsrc = entry.getName().substring(prefix.length());
        String title;
        if (rsrc.endsWith(".html")) {
          title = rsrc.substring(4, rsrc.length() - 5).replace('-', ' ');
        } else if (rsrc.endsWith(".md")) {
          title = extractTitleFromMarkdown(jar, entry);
          if (Strings.isNullOrEmpty(title)) {
            title = rsrc.substring(4, rsrc.length() - 3).replace('-', ' ');
          }
          rsrc = rsrc.substring(0, rsrc.length() - 3) + ".html";
        } else {
          title = rsrc.substring(4).replace('-', ' ');
        }
        md.append(String.format("* [%s](%s)\n", title, rsrc));
      }
      md.append("\n");
    }

    sendMarkdownAsHtml(md.toString(), pluginName, cacheKey, res);
  }

  private void sendMarkdownAsHtml(String md, String pluginName,
      ResourceKey cacheKey, HttpServletResponse res)
      throws UnsupportedEncodingException, IOException {
    Map<String, String> macros = Maps.newHashMap();
    macros.put("PLUGIN", pluginName);
    macros.put("SSH_HOST", sshHost);
    macros.put("SSH_PORT", "" + sshPort);
    String url = webUrl.get();
    if (Strings.isNullOrEmpty(url)) {
      url = "http://review.example.com/";
    }
    macros.put("URL", url);

    Matcher m = Pattern.compile("(\\\\)?@([A-Z_]+)@").matcher(md);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      String key = m.group(2);
      String val = macros.get(key);
      if (m.group(1) != null) {
        m.appendReplacement(sb, "@" + key + "@");
      } else if (val != null) {
        m.appendReplacement(sb, val);
      } else {
        m.appendReplacement(sb, "@" + key + "@");
      }
    }
    m.appendTail(sb);

    byte[] html = new MarkdownFormatter()
      .markdownToDocHtml(sb.toString(), "UTF-8");
    resourceCache.put(cacheKey, new SmallResource(html)
        .setContentType("text/html")
        .setCharacterEncoding("UTF-8"));
    res.setContentType("text/html");
    res.setCharacterEncoding("UTF-8");
    res.setContentLength(html.length);
    res.getOutputStream().write(html);
  }

  private static void appendPluginInfoTable(StringBuilder html, Attributes main) {
    if (main != null) {
      String t = main.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
      String n = main.getValue(Attributes.Name.IMPLEMENTATION_VENDOR);
      String v = main.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
      String u = main.getValue(Attributes.Name.IMPLEMENTATION_URL);
      String a = main.getValue("Gerrit-ApiVersion");

      html.append("<table class=\"plugin_info\">");
      if (!Strings.isNullOrEmpty(t)) {
        html.append("<tr><th>Name</th><td>")
            .append(t)
            .append("</td></tr>\n");
      }
      if (!Strings.isNullOrEmpty(n)) {
        html.append("<tr><th>Vendor</th><td>")
            .append(n)
            .append("</td></tr>\n");
      }
      if (!Strings.isNullOrEmpty(v)) {
        html.append("<tr><th>Version</th><td>")
            .append(v)
            .append("</td></tr>\n");
      }
      if (!Strings.isNullOrEmpty(u)) {
        html.append("<tr><th>URL</th><td>")
            .append(String.format("<a href=\"%s\">%s</a>", u, u))
            .append("</td></tr>\n");
      }
      if (!Strings.isNullOrEmpty(a)) {
        html.append("<tr><th>API Version</th><td>")
            .append(a)
            .append("</td></tr>\n");
      }
      html.append("</table>\n");
    }
  }

  private static String extractTitleFromMarkdown(JarFile jar, JarEntry entry)
        throws IOException {
    String charEnc = null;
    Attributes atts = entry.getAttributes();
    if (atts != null) {
      charEnc = Strings.emptyToNull(atts.getValue("Character-Encoding"));
    }
    if (charEnc == null) {
      charEnc = "UTF-8";
    }
    return new MarkdownFormatter().extractTitleFromMarkdown(
          readWholeEntry(jar, entry),
          charEnc);
  }

  private static JarEntry findSource(JarFile jar, String file) {
    if (file.endsWith(".html")) {
      int d = file.lastIndexOf('.');
      return jar.getJarEntry(file.substring(0, d) + ".md");
    }
    return null;
  }

  private static boolean exists(JarEntry entry) {
    return entry != null && entry.getSize() > 0;
  }

  private void sendMarkdownAsHtml(JarFile jar, JarEntry entry,
      String pluginName, ResourceKey key, HttpServletResponse res)
      throws IOException {
    byte[] rawmd = readWholeEntry(jar, entry);
    String encoding = null;
    Attributes atts = entry.getAttributes();
    if (atts != null) {
      encoding = Strings.emptyToNull(atts.getValue("Character-Encoding"));
    }

    String txtmd = RawParseUtils.decode(
        Charset.forName(encoding != null ? encoding : "UTF-8"),
        rawmd);
    long time = entry.getTime();
    if (0 < time) {
      res.setDateHeader("Last-Modified", time);
    }
    sendMarkdownAsHtml(txtmd, pluginName, key, res);
  }

  private void sendResource(JarFile jar, JarEntry entry,
      ResourceKey key, HttpServletResponse res)
      throws IOException {
    byte[] data = null;
    if (entry.getSize() <= SMALL_RESOURCE) {
      data = readWholeEntry(jar, entry);
    }

    String contentType = null;
    String charEnc = null;
    Attributes atts = entry.getAttributes();
    if (atts != null) {
      contentType = Strings.emptyToNull(atts.getValue("Content-Type"));
      charEnc = Strings.emptyToNull(atts.getValue("Character-Encoding"));
    }
    if (contentType == null) {
      contentType = mimeUtil.getMimeType(entry.getName(), data).toString();
      if ("application/octet-stream".equals(contentType)
          && entry.getName().endsWith(".js")) {
        contentType = "application/javascript";
      }
    }

    long time = entry.getTime();
    if (0 < time) {
      res.setDateHeader("Last-Modified", time);
    }
    res.setHeader("Content-Length", Long.toString(entry.getSize()));
    res.setContentType(contentType);
    if (charEnc != null) {
      res.setCharacterEncoding(charEnc);
    }
    if (data != null) {
      resourceCache.put(key, new SmallResource(data)
          .setContentType(contentType)
          .setCharacterEncoding(charEnc)
          .setLastModified(time));
      res.getOutputStream().write(data);
    } else {
      InputStream in = jar.getInputStream(entry);
      try {
        OutputStream out = res.getOutputStream();
        try {
          byte[] tmp = new byte[1024];
          int n;
          while ((n = in.read(tmp)) > 0) {
            out.write(tmp, 0, n);
          }
        } finally {
          out.close();
        }
      } finally {
        in.close();
      }
    }
  }

  private static byte[] readWholeEntry(JarFile jar, JarEntry entry)
      throws IOException {
    byte[] data = new byte[(int) entry.getSize()];
    InputStream in = jar.getInputStream(entry);
    try {
      IO.readFully(in, data, 0, data.length);
    } finally {
      in.close();
    }
    return data;
  }

  private static class PluginHolder {
    final Plugin plugin;
    final GuiceFilter filter;
    final String staticPrefix;
    final String docPrefix;

    PluginHolder(Plugin plugin, GuiceFilter filter) {
      this.plugin = plugin;
      this.filter = filter;
      this.staticPrefix =
        getPrefix(plugin, "Gerrit-HttpStaticPrefix", "static/");
      this.docPrefix =
        getPrefix(plugin, "Gerrit-HttpDocumentationPrefix", "Documentation/");
    }

    private static String getPrefix(Plugin plugin, String attr, String def) {
      try {
        String prefix = plugin.getJarFile().getManifest().getMainAttributes()
            .getValue(attr);
        if (prefix != null) {
          return CharMatcher.is('/').trimFrom(prefix) + "/";
        } else {
          return def;
        }
      } catch (IOException e) {
        log.warn(String.format("Error getting %s for plugin %s, using default",
            attr, plugin.getName()), e);
        return null;
      }
    }
  }

  private static class WrappedRequest extends HttpServletRequestWrapper {
    private final String contextPath;

    WrappedRequest(HttpServletRequest req, String contextPath) {
      super(req);
      this.contextPath = contextPath;
    }

    @Override
    public String getContextPath() {
      return contextPath;
    }

    @Override
    public String getServletPath() {
      return ((HttpServletRequest) getRequest()).getRequestURI().substring(
          contextPath.length());
    }
  }
}
