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

import com.google.inject.Inject;
import com.google.inject.servlet.SessionScoped;

import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SessionScoped
public class GitHubLogin {
  private static final Logger LOG = LoggerFactory.getLogger(GitHubLogin.class);

  public String token;
  public String user;

  private OAuthProtocol oauth;

  @Inject
  public GitHubLogin(final OAuthProtocol oauth) {
    this.oauth = oauth;
  }

  public boolean isLoggedIn() {
    return token != null && user != null;
  }

  public boolean login(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    if (isLoggedIn()) {
      return true;
    }

    LOG.debug("Login " + this);

    if (OAuthProtocol.isOAuthFinal(request)) {
      String redirectUrl = oauth.getTargetUrl(request);
      if (redirectUrl == null) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        return false;
      }

      LOG.debug("Login-Retrieve-User " + this);
      retrieveUser(oauth.loginPhase2(request, response));
      if (isLoggedIn()) {
        LOG.debug("Login-SUCCESS " + this);
        response.sendRedirect(redirectUrl);
        return true;
      } else {
        response.sendError(HttpStatus.SC_UNAUTHORIZED);
        return false;
      }
    } else {
      LOG.debug("Login-PHASE1 " + this);
      oauth.loginPhase1(request, response);
      return false;
    }
  }

  public void logout() {
    token = null;
    user = null;
  }

  public boolean isLoginRequest(HttpServletRequest httpRequest) {
    return oauth.isOAuthRequest(httpRequest);
  }

  public String getUsername() {
    if (isLoggedIn()) {
      return user;
    } else {
      return null;
    }
  }

  @Override
  public String toString() {
    return "GitHubLogin [token=" + token + ", user=" + user + "]";
  }

  private void retrieveUser(String authToken) throws IOException {
    this.token = authToken;
    this.user = oauth.retrieveUser(authToken);
  }
}
