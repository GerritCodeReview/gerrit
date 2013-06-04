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

package com.google.gerrit.httpd.auth;

import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.httpd.auth.HttpAuthProtocol.BrowserLoginHandler;
import com.google.gerrit.httpd.auth.HttpAuthProtocol.ProgrammaticLoginHandler;
import com.google.gerrit.httpd.auth.HttpAuthProtocol.VerifiableCredentialsExtractor;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.auth.AuthException;
import com.google.gerrit.server.auth.AuthUser;
import com.google.gerrit.server.auth.Credentials;
import com.google.gerrit.server.auth.InternalUserBackend;
import com.google.gerrit.server.auth.VerifiableCredentials;

import org.eclipse.jgit.http.server.GitSmartHttpTools;

import java.io.IOException;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
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
 * An HTTP Filter that applies the authorization protocol handler to the request
 * and authenticates the user.
 */
@Singleton
public class HttpAuthorizer implements Filter {
  private final InternalUserBackend userBackend;
  private final Provider<WebSession> session;
  private final DynamicSet<HttpAuthProtocol.VerifiableCredentialsExtractor<?, ?>> extractors;
  private final Provider<BrowserLoginHandler> browserLogin;
  private final Provider<ProgrammaticLoginHandler> programmaticLogin;
  private ServletContext context;

  @Inject
  HttpAuthorizer(InternalUserBackend userBackend,
      Provider<WebSession> session,
      DynamicSet<HttpAuthProtocol.VerifiableCredentialsExtractor<?, ?>> extractors,
      Provider<HttpAuthProtocol.BrowserLoginHandler> browserLogin,
      Provider<HttpAuthProtocol.ProgrammaticLoginHandler> programmaticLogin) {
    this.userBackend = userBackend;
    this.session = session;
    this.extractors = extractors;
    this.browserLogin = browserLogin;
    this.programmaticLogin = programmaticLogin;
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse resp,
      FilterChain chain) throws IOException, ServletException {
    doHttpFilter((HttpServletRequest) req, (HttpServletResponse) resp, chain);
  }

  private void doHttpFilter(HttpServletRequest req, HttpServletResponse resp,
      FilterChain chain) throws IOException, ServletException {
    VerifiableCredentials<?> creds = null;
    try {
      for (VerifiableCredentialsExtractor<?, ?> extractor : extractors) {
        creds = extractor.extractCredentials((HttpServletRequest) req);
        if (creds != null) {
          AuthUser authUser = creds.verify();
          // TODO: use a "UniversalAuthUserBackend"
          AccountState who = userBackend.getByUsername(authUser.getUsername());
          // TODO: support setting the user via AuthUser.UUID
          session.get().setUserAccountId(who.getAccount().getId());        }
      }
    } catch (AuthException e) {
      context.log("error authorizing user", e);
      login(req, resp, (creds == null) ? null : creds.getCredentials());
      return;
    } catch (AuthProtocolException e) {
      context.log("error selecting auth protocol", e);
      login(req, resp, null);
      return;
    }

    chain.doFilter(req, resp);
  }

  private void login(HttpServletRequest req, HttpServletResponse resp,
      @Nullable Credentials creds) throws IOException {
    if (GitSmartHttpTools.isGitClient(req)) {
      programmaticLogin.get().loginProgrammatic(req, resp, creds);
    } else {
      String dest = null; // TODO: set the dest to the request URL
      browserLogin.get().loginBrowser(req, resp, dest);
    }
    resp.sendError(SC_UNAUTHORIZED);
  }
  @Override
  public void init(FilterConfig config) throws ServletException {
    context = config.getServletContext();
  }

  @Override
  public void destroy() {
  }
}
