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
 * <p>Filter out inactive accounts for preventing old sessions to be reused for authenticating
 * incoming HTTP calls as come from an IdentifiedUser.
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
          "Logging out inactive user %s(%d) on session %s",
          identifiedUser.getLoggableName(), identifiedUser.getAccountId().get(), session);

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
