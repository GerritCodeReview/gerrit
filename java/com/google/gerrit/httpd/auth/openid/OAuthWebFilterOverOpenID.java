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

package com.google.gerrit.httpd.auth.openid;

import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.SortedMap;
import java.util.SortedSet;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** OAuth web filter uses active OAuth session to perform OAuth requests */
@Singleton
class OAuthWebFilterOverOpenID implements Filter {
  static final String GERRIT_LOGIN = "/login";

  private final Provider<OAuthSessionOverOpenID> oauthSessionProvider;
  private final DynamicMap<OAuthServiceProvider> oauthServiceProviders;
  private OAuthServiceProvider ssoProvider;

  @Inject
  OAuthWebFilterOverOpenID(
      DynamicMap<OAuthServiceProvider> oauthServiceProviders,
      Provider<OAuthSessionOverOpenID> oauthSessionProvider) {
    this.oauthServiceProviders = oauthServiceProviders;
    this.oauthSessionProvider = oauthSessionProvider;
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    pickSSOServiceProvider();
  }

  @Override
  public void destroy() {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    OAuthSessionOverOpenID oauthSession = oauthSessionProvider.get();
    OAuthServiceProvider service =
        ssoProvider == null ? oauthSession.getServiceProvider() : ssoProvider;

    if (isGerritLogin(httpRequest) || oauthSession.isOAuthFinal(httpRequest)) {
      if (service == null) {
        throw new IllegalStateException("service is unknown");
      }
      oauthSession.setServiceProvider(service);
      oauthSession.login(httpRequest, httpResponse, service);
    } else {
      chain.doFilter(httpRequest, response);
    }
  }

  private void pickSSOServiceProvider() {
    SortedSet<String> plugins = oauthServiceProviders.plugins();
    if (plugins.size() == 1) {
      SortedMap<String, Provider<OAuthServiceProvider>> services =
          oauthServiceProviders.byPlugin(Iterables.getOnlyElement(plugins));
      if (services.size() == 1) {
        ssoProvider = Iterables.getOnlyElement(services.values()).get();
      }
    }
  }

  private static boolean isGerritLogin(HttpServletRequest request) {
    return request.getRequestURI().indexOf(GERRIT_LOGIN) >= 0;
  }
}
