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
import com.google.gerrit.server.plugins.Plugin;
import com.google.gerrit.server.plugins.RegistrationHandle;
import com.google.gerrit.server.plugins.ReloadPluginListener;
import com.google.gerrit.server.plugins.StartPluginListener;
import com.google.inject.Singleton;
import com.google.inject.servlet.GuiceFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

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

  private List<Plugin> pending = Lists.newArrayList();
  private String base;
  private final ConcurrentMap<String, GuiceFilter> plugins
      = Maps.newConcurrentMap();

  @Override
  public synchronized void init(ServletConfig config) throws ServletException {
    super.init(config);

    String path = config.getServletContext().getContextPath();
    base = Strings.nullToEmpty(path) + "/plugins/";
    for (Plugin plugin : pending) {
      GuiceFilter filter = load(plugin);
      if (filter != null) {
        plugins.put(plugin.getName(), filter);
      }
    }
    pending = null;
  }

  @Override
  public synchronized void onStartPlugin(Plugin plugin) {
    if (pending != null) {
      pending.add(plugin);
    } else {
      GuiceFilter filter = load(plugin);
      if (filter != null) {
        plugins.put(plugin.getName(), filter);
      }
    }
  }

  @Override
  public void onReloadPlugin(Plugin oldPlugin, Plugin newPlugin) {
    GuiceFilter filter = load(newPlugin);
    if (filter != null) {
      plugins.put(newPlugin.getName(), filter);
    }
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
          try {
            filter.destroy();
          } finally {
            plugins.remove(name, filter);
          }
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
    GuiceFilter filter = plugins.get(name);
    if (filter == null) {
      noCache(res);
      res.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    filter.doFilter(new WrappedRequest(req, base + name), res,
        new FilterChain() {
          @Override
          public void doFilter(ServletRequest req, ServletResponse response)
              throws IOException, ServletException {
            HttpServletResponse res = (HttpServletResponse) response;
            noCache(res);
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
          }
        });
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
