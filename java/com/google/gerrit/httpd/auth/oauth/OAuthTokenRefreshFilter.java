// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.httpd.auth.oauth;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.auth.oauth.OAuthTokenRefresher;
import com.google.gerrit.extensions.auth.oauth.OAuthRevokedException;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.httpd.AllRequestFilter;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.CurrentUser;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Renews an expired browser OAuth access token before the request proceeds, delegating the actual
 * refresh to the shared {@link OAuthTokenRefresher}. Installed only when {@code
 * auth.enableOAuthTokenRefreshFilter = true} (see {@link OAuthModule}), so a server that has not
 * opted in never runs it.
 *
 * <p>Acts only on signed-in interactive sessions (any {@link AccessPath} other than {@link
 * AccessPath#GIT}, which authenticates per request and uses the plugin's own validation cache). On
 * a revoked grant ({@code invalid_grant}) the session is ended and the request is redirected to
 * re-authenticate.
 */
@Singleton
class OAuthTokenRefreshFilter extends AllRequestFilter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<CurrentUser> userProvider;
  private final DynamicItem<WebSession> webSession;
  private final OAuthTokenRefresher refresher;

  @Inject
  OAuthTokenRefreshFilter(
      Provider<CurrentUser> userProvider,
      DynamicItem<WebSession> webSession,
      OAuthTokenRefresher refresher) {
    this.userProvider = userProvider;
    this.webSession = webSession;
    this.refresher = refresher;
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    if (refreshIfNeeded() && redirectToLogin(req, res)) {
      // Grant revoked and session ended: do not serve this request to the now-revoked user.
      return;
    }
    chain.doFilter(req, res);
  }

  /** Returns true if the session was revoked, so the caller must stop serving the request. */
  private boolean refreshIfNeeded() {
    WebSession session = webSession.get();
    if (session == null || !session.isSignedIn()) {
      return false;
    }
    CurrentUser user = userProvider.get();
    if (user == null || !user.isIdentifiedUser() || user.getAccessPath() == AccessPath.GIT) {
      return false;
    }
    try {
      // Refresh-on-read: renews the cached token if it has expired. The refresher skips decryption
      // entirely when the token is still valid, so this is cheap on the common request.
      refresher.refreshIfExpired(user.getAccountId());
      return false;
    } catch (OAuthRevokedException e) {
      session.logout();
      logger.atInfo().log(
          "OAuth grant revoked for account %s; ended session, redirecting to login",
          user.getAccountId());
      return true;
    }
  }

  private boolean redirectToLogin(ServletRequest req, ServletResponse res) throws IOException {
    if (req instanceof HttpServletRequest httpReq
        && res instanceof HttpServletResponse httpRes
        && !httpRes.isCommitted()) {
      httpRes.sendRedirect(httpReq.getContextPath() + "/login");
      return true;
    }
    return false;
  }
}
