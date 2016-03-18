// Copyright (C) 2015 The Android Open Source Project
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

import static javax.servlet.http.HttpServletResponse.SC_NOT_IMPLEMENTED;

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.httpd.resources.Resource;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.plugins.Plugin;
import com.google.gerrit.server.plugins.ReloadPluginListener;
import com.google.gerrit.server.plugins.StartPluginListener;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.servlet.GuiceFilter;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import javax.servlet.FilterChain;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class LfsPluginServlet extends HttpServlet
    implements StartPluginListener, ReloadPluginListener {
  private static final long serialVersionUID = 1L;
  private static final Logger log
      = LoggerFactory.getLogger(LfsPluginServlet.class);

  public static final String URL_REGEX =
      "^(?:/a)?(?:/p/|/)(.+)(?:/info/lfs/objects/batch)$";

  private List<Plugin> pending = Lists.newArrayList();
  private final String pluginName;
  private GuiceFilter filter;

  @Inject
  LfsPluginServlet(@GerritServerConfig Config cfg) {
    this.pluginName = cfg.getString("lfs", null, "plugin");
  }

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    if (filter == null) {
      CacheHeaders.setNotCacheable(res);
      res.sendError(SC_NOT_IMPLEMENTED);
      return;
    }

    FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest req, ServletResponse res)
          throws IOException {
        Resource.NOT_FOUND.send((HttpServletRequest) req, (HttpServletResponse) res);
      }
    };
    if (filter != null) {
      filter.doFilter(req, res, chain);
    } else {
      chain.doFilter(req, res);
    }
  }

  @Override
  public synchronized void init(ServletConfig config) throws ServletException {
    super.init(config);

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
    if (!plugin.getName().equals(pluginName)) {
      return;
    }
    filter = load(plugin);
    plugin.add(new RegistrationHandle() {
      @Override
      public void remove() {
        filter = null;
      }
    });
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
        ServletContext ctx =
            PluginServletContext.create(plugin, "/");
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
}
