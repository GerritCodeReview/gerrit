// Copyright (C) 2011 The Android Open Source Project
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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.httpd.restapi.RestApiServlet;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.lib.Config;

/**
 * Trust the authentication which is done by the container.
 *
 * <p>Check whether the container has already authenticated the user. If yes, then lookup the
 * account and set the account ID in our current session.
 *
 * <p>This filter should only be configured to run, when authentication is configured to trust
 * container authentication. This filter is intended to protect the {@link GitOverHttpServlet} and
 * its handled URLs, which provide remote repository access over HTTP. It also protects {@link
 * RestApiServlet}.
 */
@Singleton
class ContainerAuthFilter implements Filter {
  private final DynamicItem<WebSession> session;
  private final AccountCache accountCache;
  private final Config config;
  private final String loginHttpHeader;

  @Inject
  ContainerAuthFilter(
      DynamicItem<WebSession> session,
      AccountCache accountCache,
      AuthConfig authConfig,
      @GerritServerConfig Config config) {
    this.session = session;
    this.accountCache = accountCache;
    this.config = config;

    loginHttpHeader = firstNonNull(emptyToNull(authConfig.getLoginHttpHeader()), AUTHORIZATION);
  }

  @Override
  public void init(FilterConfig config) {}

  @Override
  public void destroy() {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse rsp = (HttpServletResponse) response;

    if (verify(req, rsp)) {
      chain.doFilter(req, response);
    }
  }

  private boolean verify(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    String username = RemoteUserUtil.getRemoteUser(req, loginHttpHeader);
    if (username == null) {
      rsp.sendError(SC_FORBIDDEN);
      return false;
    }
    if (config.getBoolean("auth", "userNameToLowerCase", false)) {
      username = username.toLowerCase(Locale.US);
    }
    Optional<AccountState> who =
        accountCache.getByUsername(username).filter(a -> a.getAccount().isActive());
    if (!who.isPresent()) {
      rsp.sendError(SC_UNAUTHORIZED);
      return false;
    }
    WebSession ws = session.get();
    ws.setUserAccountId(who.get().getAccount().getId());
    ws.setAccessPathOk(AccessPath.GIT, true);
    ws.setAccessPathOk(AccessPath.REST_API, true);
    return true;
  }
}
