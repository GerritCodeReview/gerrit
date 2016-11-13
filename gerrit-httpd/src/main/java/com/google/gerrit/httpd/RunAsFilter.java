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

import static com.google.gerrit.httpd.restapi.RestApiServlet.replyError;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gwtorm.server.OrmException;
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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  private final Provider<ReviewDb> db;
  private final boolean enabled;
  private final DynamicItem<WebSession> session;
  private final AccountResolver accountResolver;

  @Inject
  RunAsFilter(
      Provider<ReviewDb> db,
      AuthConfig config,
      DynamicItem<WebSession> session,
      AccountResolver accountResolver) {
    this.db = db;
    this.enabled = config.isRunAsEnabled();
    this.session = session;
    this.accountResolver = accountResolver;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse res = (HttpServletResponse) response;

    String runas = req.getHeader(RUN_AS);
    if (runas != null) {
      if (!enabled) {
        replyError(req, res, SC_FORBIDDEN, RUN_AS + " disabled by auth.enableRunAs = false", null);
        return;
      }

      CurrentUser self = session.get().getUser();
      if (!self.getCapabilities().canRunAs()
          // Always disallow for anonymous users, even if permitted by the ACL,
          // because that would be crazy.
          || !self.isIdentifiedUser()) {
        replyError(req, res, SC_FORBIDDEN, "not permitted to use " + RUN_AS, null);
        return;
      }

      Account target;
      try {
        target = accountResolver.find(db.get(), runas);
      } catch (OrmException e) {
        log.warn("cannot resolve account for " + RUN_AS, e);
        replyError(req, res, SC_INTERNAL_SERVER_ERROR, "cannot resolve " + RUN_AS, e);
        return;
      }
      if (target == null) {
        replyError(req, res, SC_FORBIDDEN, "no account matches " + RUN_AS, null);
        return;
      }
      session.get().setUserAccountId(target.getId());
    }

    chain.doFilter(req, res);
  }

  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void destroy() {}
}
