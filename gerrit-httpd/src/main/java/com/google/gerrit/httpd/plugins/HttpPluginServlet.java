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

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.server.MimeUtilFileTypeRegistry;
import com.google.gerrit.server.plugins.Plugin;
import com.google.gerrit.server.plugins.ReloadPluginListener;
import com.google.gerrit.server.plugins.StartPluginListener;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.servlet.GuiceFilter;

import eu.medsea.mimeutil.MimeType;

import org.eclipse.jgit.util.IO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.servlet.FilterChain;
import javax.servlet.ServletConfig;
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
  private static final long serialVersionUID = 1L;
  private static final Logger log
      = LoggerFactory.getLogger(HttpPluginServlet.class);

  private final MimeUtilFileTypeRegistry mimeUtil;
  private List<Plugin> pending = Lists.newArrayList();
  private String base;
  private final ConcurrentMap<String, PluginHolder> plugins
      = Maps.newConcurrentMap();

  @Inject
  HttpPluginServlet(MimeUtilFileTypeRegistry mimeUtil) {
    this.mimeUtil = mimeUtil;
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
        WrappedContext ctx = new WrappedContext(plugin, base + name);
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
    String name = extractName(req);
    final PluginHolder holder = plugins.get(name);
    if (holder == null) {
      noCache(res);
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

  private void onDefault(PluginHolder holder,
      HttpServletRequest req,
      HttpServletResponse res) throws IOException {
    String uri = req.getRequestURI();
    String ctx = req.getContextPath();
    if (uri.length() > ctx.length()) {
      String file = uri.substring(ctx.length() + 1);
      if (file.startsWith("Documentation/") || file.startsWith("static/")) {
        JarFile jar = holder.plugin.getJarFile();
        JarEntry entry = jar.getJarEntry(file);
        if (entry != null && entry.getSize() > 0) {
          sendResource(jar, entry, res);
          return;
        }
      }
    }

    noCache(res);
    res.sendError(HttpServletResponse.SC_NOT_FOUND);
  }

  private void sendResource(JarFile jar, JarEntry entry, HttpServletResponse res)
      throws IOException {
    byte[] data = null;
    if (entry.getSize() <= 128 * 1024) {
      data = new byte[(int) entry.getSize()];
      InputStream in = jar.getInputStream(entry);
      try {
        IO.readFully(in, data, 0, data.length);
      } finally {
        in.close();
      }
    }

    String contentType = null;
    Attributes atts = entry.getAttributes();
    if (atts != null) {
      contentType = Strings.emptyToNull(atts.getValue("Content-Type"));
    }
    if (contentType == null) {
      MimeType type = mimeUtil.getMimeType(entry.getName(), data);
      contentType = type.toString();
    }

    long time = entry.getTime();
    if (0 < time) {
      res.setDateHeader("Last-Modified", time);
    }
    res.setContentType(contentType);
    res.setHeader("Content-Length", Long.toString(entry.getSize()));
    if (data != null) {
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

  private static String extractName(HttpServletRequest req) {
    String path = req.getPathInfo();
    if (Strings.isNullOrEmpty(path) || "/".equals(path)) {
      return "";
    }
    int s = path.indexOf('/', 1);
    return 0 <= s ? path.substring(1, s) : path.substring(1);
  }

  private static void noCache(HttpServletResponse res) {
    res.setHeader("Expires", "Fri, 01 Jan 1980 00:00:00 GMT");
    res.setHeader("Pragma", "no-cache");
    res.setHeader("Cache-Control", "no-cache, must-revalidate");
    res.setHeader("Content-Disposition", "attachment");
  }

  private static class PluginHolder {
    final Plugin plugin;
    final GuiceFilter filter;

    PluginHolder(Plugin plugin, GuiceFilter filter) {
      this.plugin = plugin;
      this.filter = filter;
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
      return ((HttpServletRequest) getRequest()).getRequestURI();
    }
  }
}
