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

import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.extensions.auth.oauth.OAuthVerifier;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthResult;
import com.google.inject.Inject;
import com.google.inject.servlet.SessionScoped;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SessionScoped
/* OAuth protocol implementation */
class OAuthSession {
  private static final Logger log = LoggerFactory.getLogger(OAuthSession.class);
  private static final SecureRandom randomState = newRandomGenerator();
  private final String state;
  private final DynamicItem<WebSession> webSession;
  private final AccountManager accountManager;
  private OAuthServiceProvider serviceProvider;
  private OAuthToken token;
  private OAuthUserInfo user;
  private String redirectUrl;

  @Inject
  OAuthSession(DynamicItem<WebSession> webSession,
      AccountManager accountManager) {
    this.state = generateRandomState();
    this.webSession = webSession;
    this.accountManager = accountManager;
  }

  boolean isLoggedIn() {
    return token != null && user != null;
  }

  boolean isOAuthFinal(HttpServletRequest request) {
    return Strings.emptyToNull(request.getParameter("code")) != null;
  }

  boolean login(HttpServletRequest request, HttpServletResponse response,
      OAuthServiceProvider oauth) throws IOException {
    if (isLoggedIn()) {
      return true;
    }

    log.debug("Login " + this);

    if (isOAuthFinal(request)) {
      if (!checkState(request)) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        return false;
      }

      log.debug("Login-Retrieve-User " + this);
      token = oauth.getAccessToken(null,
          new OAuthVerifier(request.getParameter("code")));

      user = oauth.getUserInfo(token);

      if (isLoggedIn()) {
        log.debug("Login-SUCCESS " + this);
        authenticateAndRedirect(response);
        return true;
      } else {
        response.sendError(SC_UNAUTHORIZED);
        return false;
      }
    } else {
      log.debug("Login-PHASE1 " + this);
      redirectUrl = request.getRequestURI();
      response.sendRedirect(oauth.getAuthorizationUrl(null) +
          "&state=" + state);
      return false;
    }
  }

  private void authenticateAndRedirect(HttpServletResponse rsp)
      throws IOException {
    com.google.gerrit.server.account.AuthRequest areq =
        new com.google.gerrit.server.account.AuthRequest(user.getExternalId());
    areq.setUserName(user.getUserName());
    areq.setEmailAddress(user.getEmailAddress());
    areq.setDisplayName(user.getDisplayName());
    AuthResult arsp;
    try {
      arsp = accountManager.authenticate(areq);
    } catch (AccountException e) {
      log.error("Unable to authenticate user \"" + user + "\"", e);
      rsp.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    webSession.get().login(arsp, true);
    String suffix = redirectUrl.substring(
        OAuthWebFilter.GERRIT_LOGIN.length() + 1);
    suffix = URLDecoder.decode(suffix, StandardCharsets.UTF_8.name());
    rsp.sendRedirect(suffix);
  }

  void logout() {
    token = null;
    user = null;
    redirectUrl = null;
    serviceProvider = null;
  }

  private boolean checkState(ServletRequest request) {
    String s = Strings.nullToEmpty(request.getParameter("state"));
    if (!s.equals(state)) {
      log.error("Illegal request state '" + s + "' on OAuthProtocol " + this);
      return false;
    }
    return true;
  }

  private static SecureRandom newRandomGenerator() {
    try {
      return SecureRandom.getInstance("SHA1PRNG");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(
          "No SecureRandom available for GitHub authentication", e);
    }
  }

  private static String generateRandomState() {
    byte[] state = new byte[32];
    randomState.nextBytes(state);
    return Base64.encodeBase64URLSafeString(state);
  }

  @Override
  public String toString() {
    return "OAuthSession [token=" + token + ", user=" + user + "]";
  }

  public void setServiceProvider(OAuthServiceProvider provider) {
    this.serviceProvider = provider;
  }

  public OAuthServiceProvider getServiceProvider() {
    return serviceProvider;
  }
}
