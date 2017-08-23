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

import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
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
import org.eclipse.jgit.lib.Config;

/** Stores user as a request attribute, so servlets can access it outside of the request scope. */
@Singleton
public class GetUserFilter implements Filter {

  public static final String REQ_ATTR_KEY = "User";

  public static class Module extends ServletModule {

    private final boolean enabled;

    @Inject
    Module(@GerritServerConfig Config cfg) {
      enabled = cfg.getBoolean("http", "addUserAsRequestAttribute", true);
    }

    @Override
    protected void configureServlets() {
      if (enabled) {
        filter("/*").through(GetUserFilter.class);
      }
    }
  }

  private final Provider<CurrentUser> userProvider;

  @Inject
  GetUserFilter(Provider<CurrentUser> userProvider) {
    this.userProvider = userProvider;
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
      throws IOException, ServletException {
    CurrentUser user = userProvider.get();
    if (user != null && user.isIdentifiedUser()) {
      IdentifiedUser who = user.asIdentifiedUser();
      if (who.getUserName() != null && !who.getUserName().isEmpty()) {
        req.setAttribute(REQ_ATTR_KEY, who.getUserName());
      } else {
        req.setAttribute(REQ_ATTR_KEY, "a/" + who.getAccountId());
      }
    }
    chain.doFilter(req, resp);
  }

  @Override
  public void destroy() {}

  @Override
  public void init(FilterConfig arg0) {}
}
