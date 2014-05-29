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
package com.google.gerrit.httpd.auth.github;


import com.google.gerrit.httpd.auth.github.OAuthProtocol.AccessToken;
import com.google.gerrit.server.CurrentUser;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

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

@Singleton
public class OAuthWebFilter implements Filter {
  private static final org.slf4j.Logger log = LoggerFactory
      .getLogger(OAuthWebFilter.class);

  private final GitHubOAuthConfig config;
  private final Provider<GitHubLogin> loginProvider;
  private final Provider<CurrentUser> currentUserProvider;
  private final OAuthProtocol oauthProtocol;

  @Inject
  public OAuthWebFilter(GitHubOAuthConfig config, OAuthProtocol oauthProtocol,
      Provider<GitHubLogin> loginProvider,
      Provider<CurrentUser> currentUserProvider) {
    this.config = config;
    this.oauthProtocol = oauthProtocol;
    this.loginProvider = loginProvider;
    this.currentUserProvider = currentUserProvider;
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response,
      FilterChain chain) throws IOException, ServletException {
    if (currentUserProvider.get().isIdentifiedUser()) {
      chain.doFilter(request, response);
      return;
    }

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;
    String authCode = request.getParameter("oauth");
    log.debug("OAuthWebFilter(" + httpRequest.getRequestURL() + ") oauth="
        + authCode);

    GitHubLogin ghLogin = loginProvider.get();
    if (authCode != null) {
      ghLogin.login(new AccessToken(authCode));
    }

    if (oauthProtocol.isOAuthRequest(httpRequest) && !ghLogin.isLoggedIn()) {
      ghLogin.login(httpRequest, httpResponse);
    } else if (config.autoLogin && !ghLogin.isLoggedIn()) {
      httpResponse.sendRedirect("/login");
    } else {
      if (ghLogin.isLoggedIn()) {
        httpRequest =
            new AuthenticatedHttpRequest(httpRequest, config.httpHeader,
                ghLogin.getMyself().getLogin());
      }
      chain.doFilter(httpRequest, response);
    }
  }

  @Override
  public void destroy() {
    log.info("Init");
  }
}
