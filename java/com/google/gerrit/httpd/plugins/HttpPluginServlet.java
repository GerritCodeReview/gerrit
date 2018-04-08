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

import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static com.google.common.net.HttpHeaders.ORIGIN;
import static com.google.common.net.HttpHeaders.VARY;
import static com.google.gerrit.common.FileUtil.lastModified;
import static com.google.gerrit.server.plugins.PluginEntry.ATTR_CHARACTER_ENCODING;
import static com.google.gerrit.server.plugins.PluginEntry.ATTR_CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.net.HttpHeaders;
import com.google.gerrit.config.CanonicalWebUrl;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.httpd.resources.Resource;
import com.google.gerrit.httpd.resources.ResourceKey;
import com.google.gerrit.httpd.resources.SmallResource;
import com.google.gerrit.httpd.restapi.RestApiServlet;
import com.google.gerrit.server.documentation.MarkdownFormatter;
import com.google.gerrit.server.mime.MimeUtilFileTypeRegistry;
import com.google.gerrit.server.plugins.Plugin;
import com.google.gerrit.server.plugins.Plugin.ApiType;
import com.google.gerrit.server.plugins.PluginContentScanner;
import com.google.gerrit.server.plugins.PluginEntry;
import com.google.gerrit.server.plugins.PluginsCollection;
import com.google.gerrit.server.plugins.ReloadPluginListener;
import com.google.gerrit.server.plugins.StartPluginListener;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gerrit.util.http.RequestUtil;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.servlet.GuiceFilter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import java.util.jar.Attributes;
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
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class HttpPluginServlet extends HttpServlet implements StartPluginListener, ReloadPluginListener {
  private static final int SMALL_RESOURCE = 128 * 1024;
  private static final long serialVersionUID = 1L;
  private static final Logger log = LoggerFactory.getLogger(HttpPluginServlet.class);

  private final MimeUtilFileTypeRegistry mimeUtil;
  private final Provider<String> webUrl;
  private final Cache<ResourceKey, Resource> resourceCache;
  private final String sshHost;
  private final int sshPort;
  private final RestApiServlet managerApi;

  private List<Plugin> pending = new ArrayList<>();
  private ContextMapper wrapper;
  private final ConcurrentMap<String, PluginHolder> plugins = Maps.newConcurrentMap();
  private final Pattern allowOrigin;

  @Inject
  HttpPluginServlet(
      MimeUtilFileTypeRegistry mimeUtil,
      @CanonicalWebUrl Provider<String> webUrl,
      @Named(HttpPluginModule.PLUGIN_RESOURCES) Cache<ResourceKey, Resource> cache,
      SshInfo sshInfo,
      RestApiServlet.Globals globals,
      PluginsCollection plugins,
      @GerritServerConfig Config cfg) {
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
        sshPort = Integer.parseInt(host.substring(c + 1));
      } else {
        sshHost = host;
        sshPort = 22;
      }
    }
    this.sshHost = sshHost;
    this.sshPort = sshPort;
    this.allowOrigin = makeAllowOrigin(cfg);
  }

  @Override
  public synchronized void init(ServletConfig config) throws ServletException {
    super.init(config);

    wrapper = new ContextMapper(config.getServletContext().getContextPath());
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
    plugin.add(
        new RegistrationHandle() {
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
        ServletContext ctx = PluginServletContext.create(plugin, wrapper.getFullPath(name));
        filter.init(new WrappedFilterConfig(ctx));
      } catch (ServletException e) {
        log.warn(String.format("Plugin %s failed to initialize HTTP", name), e);
        return null;
      }

      plugin.add(
          new RegistrationHandle() {
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
    List<String> parts =
        Lists.newArrayList(
            Splitter.on('/')
                .limit(3)
                .omitEmptyStrings()
                .split(Strings.nullToEmpty(RequestUtil.getEncodedPathInfo(req))));

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

    HttpServletRequest wr = wrapper.create(req, name);
    FilterChain chain =
        new FilterChain() {
          @Override
          public void doFilter(ServletRequest req, ServletResponse res) throws IOException {
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

  private void onDefault(PluginHolder holder, HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    if (!"GET".equals(req.getMethod()) && !"HEAD".equals(req.getMethod())) {
      CacheHeaders.setNotCacheable(res);
      res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
      return;
    }

    String pathInfo = RequestUtil.getEncodedPathInfo(req);
    if (pathInfo.length() < 1) {
      Resource.NOT_FOUND.send(req, res);
      return;
    }

    checkCors(req, res);

    String file = pathInfo.substring(1);
    PluginResourceKey key = PluginResourceKey.create(holder.plugin, file);
    Resource rsc = resourceCache.getIfPresent(key);
    if (rsc != null && req.getHeader(HttpHeaders.IF_MODIFIED_SINCE) == null) {
      rsc.send(req, res);
      return;
    }

    String uri = req.getRequestURI();
    if ("".equals(file)) {
      res.sendRedirect(uri + holder.docPrefix + "index.html");
      return;
    }

    if (file.startsWith(holder.staticPrefix)) {
      if (holder.plugin.getApiType() == ApiType.JS) {
        sendJsPlugin(holder.plugin, key, req, res);
      } else {
        PluginContentScanner scanner = holder.plugin.getContentScanner();
        Optional<PluginEntry> entry = scanner.getEntry(file);
        if (entry.isPresent()) {
          if (hasUpToDateCachedResource(rsc, entry.get().getTime())) {
            rsc.send(req, res);
          } else {
            sendResource(scanner, entry.get(), key, res);
          }
        } else {
          resourceCache.put(key, Resource.NOT_FOUND);
          Resource.NOT_FOUND.send(req, res);
        }
      }
    } else if (file.equals(holder.docPrefix.substring(0, holder.docPrefix.length() - 1))) {
      res.sendRedirect(uri + "/index.html");
    } else if (file.startsWith(holder.docPrefix) && file.endsWith("/")) {
      res.sendRedirect(uri + "index.html");
    } else if (file.startsWith(holder.docPrefix)) {
      PluginContentScanner scanner = holder.plugin.getContentScanner();
      Optional<PluginEntry> entry = scanner.getEntry(file);
      if (!entry.isPresent()) {
        entry = findSource(scanner, file);
      }
      if (!entry.isPresent() && file.endsWith("/index.html")) {
        String pfx = file.substring(0, file.length() - "index.html".length());
        long pluginLastModified = lastModified(holder.plugin.getSrcFile());
        if (hasUpToDateCachedResource(rsc, pluginLastModified)) {
          rsc.send(req, res);
        } else {
          sendAutoIndex(scanner, pfx, holder.plugin.getName(), key, res, pluginLastModified);
        }
      } else if (entry.isPresent() && entry.get().getName().endsWith(".md")) {
        if (hasUpToDateCachedResource(rsc, entry.get().getTime())) {
          rsc.send(req, res);
        } else {
          sendMarkdownAsHtml(scanner, entry.get(), holder.plugin.getName(), key, res);
        }
      } else if (entry.isPresent()) {
        if (hasUpToDateCachedResource(rsc, entry.get().getTime())) {
          rsc.send(req, res);
        } else {
          sendResource(scanner, entry.get(), key, res);
        }
      } else {
        resourceCache.put(key, Resource.NOT_FOUND);
        Resource.NOT_FOUND.send(req, res);
      }
    } else {
      resourceCache.put(key, Resource.NOT_FOUND);
      Resource.NOT_FOUND.send(req, res);
    }
  }

  private static Pattern makeAllowOrigin(Config cfg) {
    String[] allow = cfg.getStringList("site", null, "allowOriginRegex");
    if (allow.length > 0) {
      return Pattern.compile(Joiner.on('|').join(allow));
    }
    return null;
  }

  private void checkCors(HttpServletRequest req, HttpServletResponse res) {
    String origin = req.getHeader(ORIGIN);
    if (!Strings.isNullOrEmpty(origin) && isOriginAllowed(origin)) {
      res.addHeader(VARY, ORIGIN);
      setCorsHeaders(res, origin);
    }
  }

  private void setCorsHeaders(HttpServletResponse res, String origin) {
    res.setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
    res.setHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    res.setHeader(ACCESS_CONTROL_ALLOW_METHODS, "GET, HEAD");
  }

  private boolean isOriginAllowed(String origin) {
    return allowOrigin == null || allowOrigin.matcher(origin).matches();
  }

  private boolean hasUpToDateCachedResource(Resource cachedResource, long lastUpdateTime) {
    return cachedResource != null && cachedResource.isUnchanged(lastUpdateTime);
  }

  private void appendEntriesSection(
      PluginContentScanner scanner,
      List<PluginEntry> entries,
      String sectionTitle,
      StringBuilder md,
      String prefix,
      int nameOffset)
      throws IOException {
    if (!entries.isEmpty()) {
      md.append("## ").append(sectionTitle).append(" ##\n");
      for (PluginEntry entry : entries) {
        String rsrc = entry.getName().substring(prefix.length());
        String entryTitle;
        if (rsrc.endsWith(".html")) {
          entryTitle = rsrc.substring(nameOffset, rsrc.length() - 5).replace('-', ' ');
        } else if (rsrc.endsWith(".md")) {
          entryTitle = extractTitleFromMarkdown(scanner, entry);
          if (Strings.isNullOrEmpty(entryTitle)) {
            entryTitle = rsrc.substring(nameOffset, rsrc.length() - 3).replace('-', ' ');
          }
        } else {
          entryTitle = rsrc.substring(nameOffset).replace('-', ' ');
        }
        md.append(String.format("* [%s](%s)\n", entryTitle, rsrc));
      }
      md.append("\n");
    }
  }

  private void sendAutoIndex(
      PluginContentScanner scanner,
      final String prefix,
      final String pluginName,
      PluginResourceKey cacheKey,
      HttpServletResponse res,
      long lastModifiedTime)
      throws IOException {
    List<PluginEntry> cmds = new ArrayList<>();
    List<PluginEntry> servlets = new ArrayList<>();
    List<PluginEntry> restApis = new ArrayList<>();
    List<PluginEntry> docs = new ArrayList<>();
    PluginEntry about = null;

    Predicate<PluginEntry> filter =
        entry -> {
          String name = entry.getName();
          Optional<Long> size = entry.getSize();
          if (name.startsWith(prefix)
              && (name.endsWith(".md") || name.endsWith(".html"))
              && size.isPresent()) {
            if (size.get() <= 0 || size.get() > SMALL_RESOURCE) {
              log.warn(
                  String.format(
                      "Plugin %s: %s omitted from document index. "
                          + "Size %d out of range (0,%d).",
                      pluginName, name.substring(prefix.length()), size.get(), SMALL_RESOURCE));
              return false;
            }
            return true;
          }
          return false;
        };

    List<PluginEntry> entries =
        Collections.list(scanner.entries()).stream().filter(filter).collect(toList());
    for (PluginEntry entry : entries) {
      String name = entry.getName().substring(prefix.length());
      if (name.startsWith("cmd-")) {
        cmds.add(entry);
      } else if (name.startsWith("servlet-")) {
        servlets.add(entry);
      } else if (name.startsWith("rest-api-")) {
        restApis.add(entry);
      } else if (name.startsWith("about.")) {
        if (about == null) {
          about = entry;
        } else {
          log.warn(
              String.format(
                  "Plugin %s: Multiple 'about' documents found; using %s",
                  pluginName, about.getName().substring(prefix.length())));
        }
      } else {
        docs.add(entry);
      }
    }

    Collections.sort(cmds, PluginEntry.COMPARATOR_BY_NAME);
    Collections.sort(docs, PluginEntry.COMPARATOR_BY_NAME);

    StringBuilder md = new StringBuilder();
    md.append(String.format("# Plugin %s #\n", pluginName));
    md.append("\n");
    appendPluginInfoTable(md, scanner.getManifest().getMainAttributes());

    if (about != null) {
      InputStreamReader isr = new InputStreamReader(scanner.getInputStream(about), UTF_8);
      StringBuilder aboutContent = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(isr)) {
        String line;
        while ((line = reader.readLine()) != null) {
          line = line.trim();
          if (line.isEmpty()) {
            aboutContent.append("\n");
          } else {
            aboutContent.append(line).append("\n");
          }
        }
      }

      // Only append the About section if there was anything in it
      if (aboutContent.toString().trim().length() > 0) {
        md.append("## About ##\n");
        md.append("\n").append(aboutContent);
      }
    }

    appendEntriesSection(scanner, docs, "Documentation", md, prefix, 0);
    appendEntriesSection(scanner, servlets, "Servlets", md, prefix, "servlet-".length());
    appendEntriesSection(scanner, restApis, "REST APIs", md, prefix, "rest-api-".length());
    appendEntriesSection(scanner, cmds, "Commands", md, prefix, "cmd-".length());

    sendMarkdownAsHtml(md.toString(), pluginName, cacheKey, res, lastModifiedTime);
  }

  private void sendMarkdownAsHtml(
      String md,
      String pluginName,
      PluginResourceKey cacheKey,
      HttpServletResponse res,
      long lastModifiedTime)
      throws UnsupportedEncodingException, IOException {
    Map<String, String> macros = new HashMap<>();
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

    byte[] html = new MarkdownFormatter().markdownToDocHtml(sb.toString(), UTF_8.name());
    resourceCache.put(
        cacheKey,
        new SmallResource(html)
            .setContentType("text/html")
            .setCharacterEncoding(UTF_8.name())
            .setLastModified(lastModifiedTime));
    res.setContentType("text/html");
    res.setCharacterEncoding(UTF_8.name());
    res.setContentLength(html.length);
    res.setDateHeader("Last-Modified", lastModifiedTime);
    res.getOutputStream().write(html);
  }

  private static void appendPluginInfoTable(StringBuilder html, Attributes main) {
    if (main != null) {
      String t = main.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
      String n = main.getValue(Attributes.Name.IMPLEMENTATION_VENDOR);
      String v = main.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
      String a = main.getValue("Gerrit-ApiVersion");

      html.append("<table class=\"plugin_info\">");
      if (!Strings.isNullOrEmpty(t)) {
        html.append("<tr><th>Name</th><td>").append(t).append("</td></tr>\n");
      }
      if (!Strings.isNullOrEmpty(n)) {
        html.append("<tr><th>Vendor</th><td>").append(n).append("</td></tr>\n");
      }
      if (!Strings.isNullOrEmpty(v)) {
        html.append("<tr><th>Version</th><td>").append(v).append("</td></tr>\n");
      }
      if (!Strings.isNullOrEmpty(a)) {
        html.append("<tr><th>API Version</th><td>").append(a).append("</td></tr>\n");
      }
      html.append("</table>\n");
    }
  }

  private static String extractTitleFromMarkdown(PluginContentScanner scanner, PluginEntry entry)
      throws IOException {
    String charEnc = null;
    Map<Object, String> atts = entry.getAttrs();
    if (atts != null) {
      charEnc = Strings.emptyToNull(atts.get(ATTR_CHARACTER_ENCODING));
    }
    if (charEnc == null) {
      charEnc = UTF_8.name();
    }
    return new MarkdownFormatter()
        .extractTitleFromMarkdown(readWholeEntry(scanner, entry), charEnc);
  }

  private static Optional<PluginEntry> findSource(PluginContentScanner scanner, String file)
      throws IOException {
    if (file.endsWith(".html")) {
      int d = file.lastIndexOf('.');
      return scanner.getEntry(file.substring(0, d) + ".md");
    }
    return Optional.empty();
  }

  private void sendMarkdownAsHtml(
      PluginContentScanner scanner,
      PluginEntry entry,
      String pluginName,
      PluginResourceKey key,
      HttpServletResponse res)
      throws IOException {
    byte[] rawmd = readWholeEntry(scanner, entry);
    String encoding = null;
    Map<Object, String> atts = entry.getAttrs();
    if (atts != null) {
      encoding = Strings.emptyToNull(atts.get(ATTR_CHARACTER_ENCODING));
    }

    String txtmd =
        RawParseUtils.decode(Charset.forName(encoding != null ? encoding : UTF_8.name()), rawmd);
    long time = entry.getTime();
    if (0 < time) {
      res.setDateHeader("Last-Modified", time);
    }
    sendMarkdownAsHtml(txtmd, pluginName, key, res, time);
  }

  private void sendResource(
      PluginContentScanner scanner,
      PluginEntry entry,
      PluginResourceKey key,
      HttpServletResponse res)
      throws IOException {
    byte[] data = null;
    Optional<Long> size = entry.getSize();
    if (size.isPresent() && size.get() <= SMALL_RESOURCE) {
      data = readWholeEntry(scanner, entry);
    }

    String contentType = null;
    String charEnc = null;
    Map<Object, String> atts = entry.getAttrs();
    if (atts != null) {
      contentType = Strings.emptyToNull(atts.get(ATTR_CONTENT_TYPE));
      charEnc = Strings.emptyToNull(atts.get(ATTR_CHARACTER_ENCODING));
    }
    if (contentType == null) {
      contentType = mimeUtil.getMimeType(entry.getName(), data).toString();
      if ("application/octet-stream".equals(contentType) && entry.getName().endsWith(".js")) {
        contentType = "application/javascript";
      } else if ("application/x-pointplus".equals(contentType)
          && entry.getName().endsWith(".css")) {
        contentType = "text/css";
      }
    }

    long time = entry.getTime();
    if (0 < time) {
      res.setDateHeader("Last-Modified", time);
    }
    if (size.isPresent()) {
      res.setHeader("Content-Length", size.get().toString());
    }
    res.setContentType(contentType);
    if (charEnc != null) {
      res.setCharacterEncoding(charEnc);
    }
    if (data != null) {
      resourceCache.put(
          key,
          new SmallResource(data)
              .setContentType(contentType)
              .setCharacterEncoding(charEnc)
              .setLastModified(time));
      res.getOutputStream().write(data);
    } else {
      writeToResponse(res, scanner.getInputStream(entry));
    }
  }

  private void sendJsPlugin(
      Plugin plugin, PluginResourceKey key, HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    Path path = plugin.getSrcFile();
    if (req.getRequestURI().endsWith(getJsPluginPath(plugin)) && Files.exists(path)) {
      res.setHeader("Content-Length", Long.toString(Files.size(path)));
      if (path.toString().toLowerCase(Locale.US).endsWith(".html")) {
        res.setContentType("text/html");
      } else {
        res.setContentType("application/javascript");
      }
      writeToResponse(res, Files.newInputStream(path));
    } else {
      resourceCache.put(key, Resource.NOT_FOUND);
      Resource.NOT_FOUND.send(req, res);
    }
  }

  private static String getJsPluginPath(Plugin plugin) {
    return String.format(
        "/plugins/%s/static/%s", plugin.getName(), plugin.getSrcFile().getFileName());
  }

  private void writeToResponse(HttpServletResponse res, InputStream inputStream)
      throws IOException {
    try (InputStream in = inputStream;
        OutputStream out = res.getOutputStream()) {
      ByteStreams.copy(in, out);
    }
  }

  private static byte[] readWholeEntry(PluginContentScanner scanner, PluginEntry entry)
      throws IOException {
    try (InputStream in = scanner.getInputStream(entry)) {
      return IO.readWholeStream(in, entry.getSize().get().intValue()).array();
    }
  }

  private static class PluginHolder {
    final Plugin plugin;
    final GuiceFilter filter;
    final String staticPrefix;
    final String docPrefix;

    PluginHolder(Plugin plugin, GuiceFilter filter) {
      this.plugin = plugin;
      this.filter = filter;
      this.staticPrefix = getPrefix(plugin, "Gerrit-HttpStaticPrefix", "static/");
      this.docPrefix = getPrefix(plugin, "Gerrit-HttpDocumentationPrefix", "Documentation/");
    }

    private static String getPrefix(Plugin plugin, String attr, String def) {
      Path path = plugin.getSrcFile();
      PluginContentScanner scanner = plugin.getContentScanner();
      if (path == null || scanner == PluginContentScanner.EMPTY) {
        return def;
      }
      try {
        String prefix = scanner.getManifest().getMainAttributes().getValue(attr);
        if (prefix != null) {
          return CharMatcher.is('/').trimFrom(prefix) + "/";
        }
        return def;
      } catch (IOException e) {
        log.warn(
            String.format("Error getting %s for plugin %s, using default", attr, plugin.getName()),
            e);
        return null;
      }
    }
  }
}
