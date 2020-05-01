// Copyright (C) 2020 The Android Open Source Project
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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * Logs out authenticated users if their account is inactive.
 *
 * <p>Ideally, when deactivating an account, we'd also purge their sessions. However, as the session
 * cache's keys alone do not allow to check if a session belongs to a given user, we'd have to
 * iterate over all sessions to check which ones to purge. Such an iteration is not implemented in
 * the current H2 based web session cache and would be resource consuming to implement. And even if,
 * one would have to do this for each future cache implementations as well. So we take a step back
 * and instead we check for each request with a signed-in session if the account is still active,
 * and log out if it is not.
 *
 * <p>Thereby, we effectively lock out inactive users without having to iterate over all sessions.
 */
@Singleton
class InactiveAccountsLogoutFilter implements Filter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<CurrentUser> userProvider;
  private final DynamicItem<WebSession> sessionProvider;

  @Inject
  InactiveAccountsLogoutFilter(Provider<CurrentUser> user, DynamicItem<WebSession> session) {
    this.userProvider = user;
    this.sessionProvider = session;
  }

  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void destroy() {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    WebSession session = sessionProvider.get();
    IdentifiedUser identifiedUser = getIdentifiedUser(session);
    if (identifiedUser != null && !identifiedUser.getAccount().isActive()) {
      // User is identified (hence logged in), but inactive. So we log the user out.
      logger.atInfo().log(
          "Loggeding out inactive user %s(%d) on session %s",
          identifiedUser.getUserName().orElse(""), identifiedUser.getAccountId().get(), session);

      session.logout();

      // As this filter is early in the filter chain, we do not need to bail out with a
      // "401 Unauthorized" response but continue filtering as normal, as all the authorization
      // requests happen only after this filter.
    }
    chain.doFilter(request, response);
  }

  private IdentifiedUser getIdentifiedUser(WebSession session) {
    IdentifiedUser ret = null;
    if (session.isSignedIn()) {
      CurrentUser currentUser = userProvider.get();
      if (currentUser.isIdentifiedUser()) {
        ret = currentUser.asIdentifiedUser();
      }
    }
    return ret;
  }
}
