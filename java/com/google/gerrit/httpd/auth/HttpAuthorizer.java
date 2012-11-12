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

package com.google.gerrit.httpd.auth;

import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.auth.AuthBackend;
import com.google.gerrit.server.auth.AuthException;
import com.google.gerrit.server.auth.AuthUser;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * An HTTP Filter that applies the authorization protocol handler to the request and authenticates
 * the user.
 */
@Singleton
public class HttpAuthorizer implements Filter {
  private final AuthBackend authBackend;
  private final AccountCache accountCache;
  private final Provider<WebSession> session;
  private final DynamicSet<HttpAuthProtocolHandler> handlers;
  private final DefaultHttpAuthProtocolSelector defaultSelector;
  private ServletContext context;

  @Inject
  HttpAuthorizer(
      AuthBackend authBackend,
      AccountCache accountCache,
      Provider<WebSession> session,
      DynamicSet<HttpAuthProtocolHandler> handlers,
      DefaultHttpAuthProtocolSelector defaultSelector) {
    this.session = session;
    this.handlers = handlers;
    this.authBackend = authBackend;
    this.accountCache = accountCache;
    this.defaultSelector = defaultSelector;
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
      throws IOException, ServletException {
    HttpAuthRequest authRequest;
    try {
      authRequest = select((HttpServletRequest) req, (HttpServletResponse) resp);
    } catch (AuthProtocolException e) {
      context.log("error selecting auth protocol", e);
      ((HttpServletResponse) resp).sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }

    if (authRequest.getUsername().isPresent()) {
      try {
        AuthUser user = authBackend.authenticate(authRequest);
        Optional <AccountState>who = accountCache.getByUsername(user.getUsername());
        if (!who.isPresent()) {
          // user does not exists in Gerrit, authentication fails
          authRequest.getResponse().sendError(SC_UNAUTHORIZED);
          return;
        }
        WebSession ws = session.get();
        ws.setUserAccountId(who.get().getAccount().getId());
        ws.setAccessPathOk(AccessPath.GIT, true);
        ws.setAccessPathOk(AccessPath.REST_API, true);
      } catch (AuthException e) {
        // TODO: log only the exceptions that are interesting
        context.log("error authorizing user", e);
        authRequest.getResponse().sendError(SC_UNAUTHORIZED);
        return;
      }
    }

    chain.doFilter(authRequest.getRequest(), authRequest.getResponse());
  }

  private HttpAuthRequest select(HttpServletRequest req, HttpServletResponse resp)
      throws AuthProtocolException {
    for (HttpAuthProtocolHandler handler : handlers) {
      HttpAuthRequest authRequest = handler.handle(req, resp);
      if (authRequest != null) {
        return authRequest;
      }
    }

    HttpAuthProtocolHandler handler = defaultSelector.selectDefault(req, resp);
    return handler.handleAnonymous(req, resp);
  }

  @Override
  public void init(FilterConfig config) throws ServletException {
    context = config.getServletContext();
  }

  @Override
  public void destroy() {}
}
