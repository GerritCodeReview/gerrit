// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.vhost;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.servlet.ServletModule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Serves virtual hosted Gerrit Code Review on HTTP. */
public class SiteSelectorModule extends ServletModule {
  @Override
  protected void configureServlets() {
    bind(RunningSite.Globals.class);
    bind(RunningSiteCache.class);

    bind(new TypeLiteral<Function<HttpServletRequest, String>>() {})
        .annotatedWith(SiteName.class)
        .to(ServerNameMapper.class);

    filter("/*").through(HostSiteSelectorFilter.class);
  }

  @Singleton
  static class ServerNameMapper implements Function<HttpServletRequest, String> {
    private final GlobalDataModule globals;

    @Inject
    ServerNameMapper(GlobalDataModule globals) {
      this.globals = globals;
    }

    @Override
    @Nullable
    public String apply(HttpServletRequest req) {
      String serverName = req.getServerName();
      if (Strings.isNullOrEmpty(serverName)) {
        return null;
      }

      int dot = serverName.indexOf('.');
      if (dot <= 0) {
        return null;
      }

      String siteName = serverName.substring(0, dot);
      return globals.exists(siteName) ? siteName : null;
    }
  }

  @Singleton
  static class HostSiteSelectorFilter implements Filter {
    private static final Logger log = LoggerFactory
        .getLogger(HostSiteSelectorFilter.class);

    private final RunningSiteCache siteCache;
    private final Function<HttpServletRequest, String> mapper;
    private ServletContext servletContext;

    @Inject
    HostSiteSelectorFilter(
        RunningSiteCache siteCache,
        @SiteName Function<HttpServletRequest, String> mapper) {
      this.siteCache = siteCache;
      this.mapper = mapper;
    }

    @Override
    public void init(FilterConfig config) {
      servletContext = config.getServletContext();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
        FilterChain chain) throws IOException, ServletException {
      HttpServletRequest req = (HttpServletRequest) request;
      HttpServletResponse res = (HttpServletResponse) response;
      String siteName = mapper.apply(req);
      if (siteName == null) {
        res.sendError(SC_NOT_FOUND);
        return;
      }

      Thread self = Thread.currentThread();
      String oldName = self.getName();
      try {
        self.setName(String.format("%s [%s]", oldName, siteName));
        RunningSite site = siteCache.loadSite(siteName);
        site.initOnFirstRequest(servletContext);
        site.dispatch(req, res, chain);
      } catch (ExecutionException err) {
        log.error(String.format("Cannot load site %s", siteName), err);
        CacheHeaders.setNotCacheable(res);
        res.sendError(SC_SERVICE_UNAVAILABLE);
      } finally {
        self.setName(oldName);
      }
    }

    @Override
    public void destroy() {
    }
  }
}
