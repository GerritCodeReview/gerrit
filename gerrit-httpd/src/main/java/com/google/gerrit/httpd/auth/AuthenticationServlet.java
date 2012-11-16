// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AuthMethod;
import com.google.gerrit.server.account.AuthResult;
import com.google.gerrit.server.auth.AuthBackend;
import com.google.gerrit.server.auth.AuthException;
import com.google.gerrit.server.auth.AuthUser;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class AuthenticationServlet extends HttpServlet {
  public static final String PARAMENTER_USERNAME = "username";
  public static final String PARAMETER_PASSWORD = "password";

  private static final String OK_RESPONSE = "OK";
  private static final String FAIL_RESPONSE = "FAIL";

  private final AuthBackend authBackend;
  private final AccountCache accountCache;
  private final Provider<WebSession> session;

  @Inject
  AuthenticationServlet(AuthBackend authBackend,
      Provider<WebSession> session,
      AccountCache accountCache) {
    this.authBackend = authBackend;
    this.session = session;
    this.accountCache = accountCache;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    doPost(req, resp);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    String username = req.getParameter(PARAMENTER_USERNAME);
    String password = req.getParameter(PARAMETER_PASSWORD);
    HttpAuthRequest authRequest = new HttpAuthRequest(username, password, req, resp);
    try {
      AuthUser user = authBackend.authenticate(authRequest);
      AccountState account = accountCache.getByUsername(user.getUsername());
      AccountExternalId.Key key = new AccountExternalId.Key(user.getUUID().get());
      AuthResult res = new AuthResult(account.getAccount().getId(), key, false);
      session.get().login(res, AuthMethod.PASSWORD, false);
      resp.getOutputStream().write(OK_RESPONSE.getBytes());
      return;
    } catch (AuthException e) {
      e.printStackTrace();
    }
    resp.getOutputStream().write(FAIL_RESPONSE.getBytes());
  }
}
