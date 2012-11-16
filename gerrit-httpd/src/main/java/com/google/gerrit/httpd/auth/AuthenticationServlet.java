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

import com.google.common.base.Strings;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.server.account.AccountManager2;
import com.google.gerrit.server.account.AuthResult;
import com.google.gerrit.server.auth.AuthException;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
@SuppressWarnings("serial")
public class AuthenticationServlet extends HttpServlet {
  public static final String PARAMETER_USERNAME = "username";
  public static final String PARAMETER_PASSWORD = "password";
  public static final String PARAMETER_REDIRECT = "redirect";

  private static final boolean IS_DEV = Boolean.getBoolean("Gerrit.GwtDevMode");
  private static final Logger log = LoggerFactory.getLogger(AuthenticationServlet.class);

  private final String canonicalWebUrl;
  private final AccountManager2 accountManager;
  private final Provider<WebSession> sessionProvider;

  @Inject
  AuthenticationServlet(
      AccountManager2 accountManager,
      Provider<WebSession> sessionProvider,
      @CanonicalWebUrl String canonicalWebUrl) {
    this.accountManager = accountManager;
    this.sessionProvider = sessionProvider;
    this.canonicalWebUrl = canonicalWebUrl;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    doPost(req, resp);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    String username = req.getParameter(PARAMETER_USERNAME);
    String password = req.getParameter(PARAMETER_PASSWORD);
    String redirect = req.getParameter(PARAMETER_REDIRECT);
    HttpAuthRequest authRequest = new HttpAuthRequest(username, password, req, resp);
    AuthResult result;
    try {
      result = accountManager.authenticate(authRequest);
    } catch (AuthException e) {
      log.error("Unable to authenticate user \"" + username + "\"", e);
      redirect(resp, PageLinks.AUTH_FAILED);
      return;
    }
    sessionProvider.get().login(result, false);
    if (result.isNew()) {
      redirect(resp, PageLinks.REGISTER);
    } else if (!Strings.isNullOrEmpty(redirect)) {
      redirect(resp, redirect);
    } else {
      redirect(resp);
    }
  }

  private void redirect(HttpServletResponse resp) throws IOException {
    redirect(resp, "");
  }

  private void redirect(HttpServletResponse resp, String url) throws IOException {
    String  selfUrl = "#" + url;
    if (IS_DEV) {
      resp.sendRedirect(canonicalWebUrl + "?gwt.codesrv=127.0.0.1:9997" + selfUrl);
    } else {
      resp.sendRedirect(canonicalWebUrl + selfUrl);
    }
  }
}
