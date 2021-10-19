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

package com.google.gerrit.httpd;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.lib.Config;

/**
 * Stores user as a request attribute and/or response header, so servlets and reverse proxies can
 * access it outside of the request/response scope.
 */
@Singleton
public class GetUserFilter implements Filter {

  public static final String USER_ATTR_KEY = "User";

  public static class GetUserFilterModule extends ServletModule {

    private final boolean reqEnabled;
    private final boolean resEnabled;

    @Inject
    GetUserFilterModule(@GerritServerConfig Config cfg) {
      reqEnabled = cfg.getBoolean("http", "addUserAsRequestAttribute", true);
      resEnabled = cfg.getBoolean("http", "addUserAsResponseHeader", false);
    }

    @Override
    protected void configureServlets() {
      if (resEnabled || reqEnabled) {
        ImmutableMap.Builder<String, String> initParams = ImmutableMap.builder();
        if (reqEnabled) {
          initParams.put("reqEnabled", "");
        }
        if (resEnabled) {
          initParams.put("resEnabled", "");
        }
        filter("/*").through(GetUserFilter.class, initParams.build());
      }
    }
  }

  private final Provider<CurrentUser> userProvider;

  private boolean reqEnabled;
  private boolean resEnabled;

  @Inject
  GetUserFilter(Provider<CurrentUser> userProvider) {
    this.userProvider = userProvider;
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
      throws IOException, ServletException {
    CurrentUser user = userProvider.get();
    if (user != null && user.isIdentifiedUser()) {
      String loggableName = user.asIdentifiedUser().getLoggableName();
      if (reqEnabled) {
        req.setAttribute(USER_ATTR_KEY, loggableName);
      }
      if (resEnabled && resp instanceof HttpServletResponse) {
        ((HttpServletResponse) resp).addHeader(USER_ATTR_KEY, loggableName);
      }
    }
    chain.doFilter(req, resp);
  }

  @Override
  public void destroy() {}

  @Override
  public void init(FilterConfig arg0) {
    reqEnabled = arg0.getInitParameter("reqEnabled") != null ? true : false;
    resEnabled = arg0.getInitParameter("resEnabled") != null ? true : false;
  }
}
