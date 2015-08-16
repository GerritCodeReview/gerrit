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

package com.google.gerrit.httpd.auth.saml;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.pac4j.core.context.J2EContext;
import org.pac4j.core.exception.RequiresHttpAction;
import org.pac4j.core.exception.TechnicalException;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Singleton
/* SAML filter that delegates to SamlSession for signing in and signing out. */
class SamlWebFilter implements Filter {
  static final String GERRIT_LOGIN = "/login";
  static final String SAML_POSTBACK = "/saml";

  private final Provider<SamlSession> samlSessionProvider;

  @Inject
  SamlWebFilter(Provider<SamlSession> samlSessionProvider) {
    this.samlSessionProvider = samlSessionProvider;
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
  }

  @Override
  public void destroy() {
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response,
      FilterChain chain) throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    J2EContext context = new J2EContext(httpRequest, httpResponse);
    SamlSession samlSession = samlSessionProvider.get();

    try {
      if (isGerritLogin(httpRequest)) {
        samlSession.redirectToIdentityProvider(context);
      } else if (isSamlPostback(httpRequest)) {
        samlSession.login(context);
      } else {
        chain.doFilter(request, response);
      }
    } catch (final RequiresHttpAction requiresHttpAction) {
      throw new TechnicalException("Unexpected HTTP action", requiresHttpAction);
    }
  }

  private static boolean isGerritLogin(HttpServletRequest request) {
    return request.getRequestURI().indexOf(GERRIT_LOGIN) >= 0;
  }

  private static boolean isSamlPostback(HttpServletRequest request) {
    return "POST".equals(request.getMethod())
        && request.getRequestURI().indexOf(SAML_POSTBACK) >= 0;
  }
}
