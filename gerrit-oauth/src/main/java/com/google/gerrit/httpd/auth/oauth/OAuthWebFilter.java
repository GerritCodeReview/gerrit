// Copyright (C) 2015 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Singleton
class OAuthWebFilter implements Filter {
  private static final Logger log = LoggerFactory
      .getLogger(OAuthWebFilter.class);
  public static final String GERRIT_LOGIN = "/login";

  private final Provider<CurrentUser> currentUserProvider;
  private final Provider<OAuthSession> oauthSessionProvider;
  private final DynamicSet<OAuthServiceProvider> oauthServiceProviders;
  private final boolean autoLogin;
  private OAuthServiceProvider oauthServiceProvider;

  @Inject
  OAuthWebFilter(Provider<CurrentUser> currentUserProvider,
      DynamicSet<OAuthServiceProvider> oauthServiceProviders,
      Provider<OAuthSession> oauthSessionProvider,
      AuthConfig authConfig) {
    this.currentUserProvider = currentUserProvider;
    this.oauthServiceProviders = oauthServiceProviders;
    this.oauthSessionProvider = oauthSessionProvider;
    this.autoLogin = Strings.isNullOrEmpty(authConfig.getLoginUrl());
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    oauthServiceProvider = get(oauthServiceProviders);
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response,
      FilterChain chain) throws IOException, ServletException {
    HttpSession httpSession = ((HttpServletRequest) request).getSession(false);
    if (currentUserProvider.get().isIdentifiedUser()) {
      if (httpSession != null) {
        httpSession.invalidate();
      }
      chain.doFilter(request, response);
      return;
    }

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;
    log.debug("OAuthWebFilter({})", httpRequest.getRequestURL());

    OAuthSession oauthSession = oauthSessionProvider.get();
    if ((isGerritLogin(httpRequest)
        || oauthSession.isOAuthFinal(httpRequest))
        && !oauthSession.isLoggedIn()) {
      oauthSession.login(httpRequest, httpResponse, oauthServiceProvider);
    } else if (autoLogin && !oauthSession.isLoggedIn()) {
      httpResponse.sendRedirect("/login");
    } else {
      chain.doFilter(httpRequest, response);
    }
  }

  @Override
  public void destroy() {
  }

  private OAuthServiceProvider get(
      DynamicSet<OAuthServiceProvider> oauthServiceProviders) {
    List<OAuthServiceProvider> providers = new ArrayList<>(1);
    for (OAuthServiceProvider p : oauthServiceProviders) {
      providers.add(p);
    }
    switch (providers.size()) {
      case 0:
        throw new IllegalStateException(
            "OAuth service provider wasn't installed");
      case 1:
        return providers.get(0);
      default:
        // TODO(davido): support multiple OAuth service providers
        throw new IllegalStateException(
            "Multiple OAuth providers not supported");
    }
  }

  private static boolean isGerritLogin(HttpServletRequest request) {
    return request.getRequestURI().indexOf(GERRIT_LOGIN) >= 0;
  }
}
