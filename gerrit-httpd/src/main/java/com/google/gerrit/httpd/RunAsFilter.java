// Copyright (C) 2013 The Android Open Source Project
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

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

import com.google.gerrit.httpd.restapi.RestApiServlet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Allows running a request as another user account. */
@Singleton
class RunAsFilter implements Filter {
  private static final Logger log = LoggerFactory.getLogger(RunAsFilter.class);
  private static final String RUN_AS = "X-Gerrit-RunAs";

  static class Module extends ServletModule {
    @Override
    protected void configureServlets() {
      filter("/*").through(RunAsFilter.class);
    }
  }

  private final boolean enabled;
  private final Provider<WebSession> session;
  private final AccountResolver accountResolver;

  @Inject
  RunAsFilter(AuthConfig config,
      Provider<WebSession> session,
      AccountResolver accountResolver) {
    this.enabled = config.isRunAsEnabled();
    this.session = session;
    this.accountResolver = accountResolver;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response,
      FilterChain chain) throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse res = (HttpServletResponse) response;

    String runas = req.getHeader(RUN_AS);
    if (runas != null) {
      if (!enabled) {
        RestApiServlet.replyError(req, res,
            SC_FORBIDDEN,
            RUN_AS + " disabled by auth.enableRunAs = false");
        return;
      }

      CurrentUser self = session.get().getCurrentUser();
      if (!self.getCapabilities().canRunAs()) {
        RestApiServlet.replyError(req, res,
            SC_FORBIDDEN,
            "not permitted to use " + RUN_AS);
        return;
      }

      Account target;
      try {
        target = accountResolver.find(runas);
      } catch (OrmException e) {
        log.warn("cannot resolve account for " + RUN_AS, e);
        RestApiServlet.replyError(req, res,
            SC_INTERNAL_SERVER_ERROR,
            "cannot resolve " + RUN_AS);
        return;
      }
      if (target == null) {
        RestApiServlet.replyError(req, res,
            SC_FORBIDDEN,
            "no account matches " + RUN_AS);
        return;
      }
      session.get().setUserAccountId(target.getId());
    }

    chain.doFilter(req, res);
  }

  @Override
  public void init(FilterConfig filterConfig) {
  }

  @Override
  public void destroy() {
  }
}
