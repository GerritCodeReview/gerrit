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

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_NOT_IMPLEMENTED;

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
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class LfsPluginServlet extends HttpServlet
    implements StartPluginListener, ReloadPluginListener {
  private static final long serialVersionUID = 1L;
  private static final Logger log = LoggerFactory.getLogger(LfsPluginServlet.class);

  public static final String URL_REGEX = "^(?:/a)?(?:/p/|/)(.+)(?:/info/lfs/objects/batch)$";

  private static final String CONTENTTYPE_VND_GIT_LFS_JSON =
      "application/vnd.git-lfs+json; charset=utf-8";
  private static final String MESSAGE_LFS_NOT_CONFIGURED =
      "{\"message\":\"No LFS plugin is configured to handle LFS requests.\"}";

  private List<Plugin> pending = new ArrayList<>();
  private final String pluginName;
  private final FilterChain chain;
  private AtomicReference<GuiceFilter> filter;

  @Inject
  LfsPluginServlet(@GerritServerConfig Config cfg) {
    this.pluginName = cfg.getString("lfs", null, "plugin");
    this.chain =
        new FilterChain() {
          @Override
          public void doFilter(ServletRequest req, ServletResponse res) throws IOException {
            Resource.NOT_FOUND.send((HttpServletRequest) req, (HttpServletResponse) res);
          }
        };
    this.filter = new AtomicReference<>();
  }

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    if (filter.get() == null) {
      responseLfsNotConfigured(res);
      return;
    }
    filter.get().doFilter(req, res, chain);
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

  private void responseLfsNotConfigured(HttpServletResponse res) throws IOException {
    CacheHeaders.setNotCacheable(res);
    res.setContentType(CONTENTTYPE_VND_GIT_LFS_JSON);
    res.setStatus(SC_NOT_IMPLEMENTED);
    Writer w = new BufferedWriter(new OutputStreamWriter(res.getOutputStream(), UTF_8));
    w.write(MESSAGE_LFS_NOT_CONFIGURED);
    w.flush();
  }

  private void install(Plugin plugin) {
    if (!plugin.getName().equals(pluginName)) {
      return;
    }
    final GuiceFilter guiceFilter = load(plugin);
    plugin.add(
        new RegistrationHandle() {
          @Override
          public void remove() {
            filter.compareAndSet(guiceFilter, null);
          }
        });
    filter.set(guiceFilter);
  }

  private GuiceFilter load(Plugin plugin) {
    if (plugin.getHttpInjector() != null) {
      final String name = plugin.getName();
      final GuiceFilter guiceFilter;
      try {
        guiceFilter = plugin.getHttpInjector().getInstance(GuiceFilter.class);
      } catch (RuntimeException e) {
        log.warn(String.format("Plugin %s cannot load GuiceFilter", name), e);
        return null;
      }

      try {
        ServletContext ctx = PluginServletContext.create(plugin, "/");
        guiceFilter.init(new WrappedFilterConfig(ctx));
      } catch (ServletException e) {
        log.warn(String.format("Plugin %s failed to initialize HTTP", name), e);
        return null;
      }

      plugin.add(
          new RegistrationHandle() {
            @Override
            public void remove() {
              guiceFilter.destroy();
            }
          });
      return guiceFilter;
    }
    return null;
  }
}
