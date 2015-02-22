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
import static org.scribe.model.OAuthConstants.CODE;

import com.google.common.base.Strings;
import com.google.inject.servlet.SessionScoped;

import org.apache.commons.codec.binary.Base64;
import org.scribe.model.Token;
import org.scribe.model.Verifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SessionScoped
class OAuthSession {
  private static final Logger log = LoggerFactory.getLogger(OAuthSession.class);
  private static final SecureRandom randomState = newRandomGenerator();
  private String state = generateRandomState();
  private Token token;
  private String user;
  private String redirectUrl;

  boolean isLoggedIn() {
    return token != null && user != null;
  }

  boolean isOAuthFinal(HttpServletRequest request) {
    return Strings.emptyToNull(request.getParameter(CODE)) != null;
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
      token = oauth.getService().getAccessToken(null,
          new Verifier(request.getParameter(CODE)));
      user = oauth.getUserInfo(token);

      if (isLoggedIn()) {
        log.debug("Login-SUCCESS " + this);
        response.sendRedirect(redirectUrl);
        return true;
      } else {
        response.sendError(SC_UNAUTHORIZED);
        return false;
      }
    } else {
      log.debug("Login-PHASE1 " + this);
      String authorizationUrl = oauth.getService().getAuthorizationUrl(null);
      redirectUrl = request.getRequestURI();
      String string = authorizationUrl + "&state=" + state;
      response.sendRedirect(string);
      return false;
    }
  }

  void logout() {
    token = null;
    user = null;
    redirectUrl = null;
  }

  String getUsername() {
    return isLoggedIn() ? user : null;
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
    return "GitHubLogin [token=" + token + ", user=" + user + "]";
  }
}
