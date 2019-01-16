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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
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
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Allows running a request as another user account. */
@Singleton
class RunAsFilter implements Filter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String RUN_AS = "X-Gerrit-RunAs";

  static class Module extends ServletModule {
    @Override
    protected void configureServlets() {
      filter("/*").through(RunAsFilter.class);
    }
  }

  private final boolean enabled;
  private final DynamicItem<WebSession> session;
  private final PermissionBackend permissionBackend;
  private final AccountResolver accountResolver;

  @Inject
  RunAsFilter(
      AuthConfig config,
      DynamicItem<WebSession> session,
      PermissionBackend permissionBackend,
      AccountResolver accountResolver) {
    this.enabled = config.isRunAsEnabled();
    this.session = session;
    this.permissionBackend = permissionBackend;
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
      try {
        if (!self.isIdentifiedUser()) {
          // Always disallow for anonymous users, even if permitted by the ACL,
          // because that would be crazy.
          throw new AuthException("denied");
        }
        permissionBackend.user(self).check(GlobalPermission.RUN_AS);
      } catch (AuthException e) {
        replyError(req, res, SC_FORBIDDEN, "not permitted to use " + RUN_AS, null);
        return;
      } catch (PermissionBackendException e) {
        logger.atWarning().withCause(e).log("cannot check runAs");
        replyError(req, res, SC_INTERNAL_SERVER_ERROR, RUN_AS + " unavailable", null);
        return;
      }

      Account.Id target;
      try {
        target = accountResolver.resolve(runas).asUnique().getAccount().getId();
      } catch (UnprocessableEntityException e) {
        replyError(req, res, SC_FORBIDDEN, "no account matches " + RUN_AS, null);
        return;
      } catch (IOException | ConfigInvalidException | RuntimeException e) {
        logger.atWarning().withCause(e).log("cannot resolve account for %s", RUN_AS);
        replyError(req, res, SC_INTERNAL_SERVER_ERROR, "cannot resolve " + RUN_AS, e);
        return;
      }
      session.get().setUserAccountId(target);
    }

    chain.doFilter(req, res);
  }

  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void destroy() {}
}
