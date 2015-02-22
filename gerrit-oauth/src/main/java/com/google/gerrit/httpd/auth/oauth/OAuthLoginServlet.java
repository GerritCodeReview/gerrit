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
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.httpd.LoginUrlToken;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.AuthResult;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
class OAuthLoginServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger log = LoggerFactory.getLogger(
      OAuthLoginServlet.class);

  private final Provider<OAuthSession> oauthSessionProvider;
  private final DynamicItem<WebSession> webSession;
  private final AccountManager accountManager;

  @Inject
  OAuthLoginServlet(Provider<OAuthSession> oauthSessionProvider,
      DynamicItem<WebSession> webSession,
      AccountManager accountManager) {
    this.oauthSessionProvider = oauthSessionProvider;
    this.webSession = webSession;
    this.accountManager = accountManager;
  }

  @Override
  protected void doGet(HttpServletRequest req,
      HttpServletResponse rsp) throws IOException {
    String user = oauthSessionProvider.get().getUsername();
    if (Strings.isNullOrEmpty(user)) {
      log.error("Unable to authenticate user");
      rsp.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    AuthRequest areq = AuthRequest.forUser(user);
    AuthResult arsp;
    try {
      arsp = accountManager.authenticate(areq);
    } catch (AccountException e) {
      log.error("Unable to authenticate user \"" + user + "\"", e);
      rsp.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    webSession.get().login(arsp, true /* persistent cookie */);
    rsp.sendRedirect(LoginUrlToken.getToken(req));
  }
}
