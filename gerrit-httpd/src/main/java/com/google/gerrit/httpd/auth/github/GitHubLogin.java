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

import com.google.gerrit.httpd.auth.github.OAuthProtocol.AccessToken;
import com.google.inject.Inject;

import org.apache.http.HttpStatus;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class GitHubLogin {
  private static final Logger LOG = LoggerFactory.getLogger(GitHubLogin.class);

  public AccessToken token;
  public GitHub hub;

  private transient OAuthProtocol oauth;

  private GHMyself myself;

  public GHMyself getMyself() {
    if (isLoggedIn()) {
      return myself;
    } else {
      return null;
    }
  }

  @Inject
  public GitHubLogin(final OAuthProtocol oauth) {
    this.oauth = oauth;
  }

  public boolean isLoggedIn() {
    boolean loggedIn = token != null && hub != null;
    if (loggedIn && myself == null) {
      try {
        myself = hub.getMyself();
      } catch (Throwable e) {
        LOG.error("Connection to GitHub broken: logging out", e);
        logout();
        loggedIn = false;
      }
    }
    return loggedIn;
  }

  public boolean login(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    if (isLoggedIn()) {
      return true;
    }

    LOG.debug("Login " + this);

    if (OAuthProtocol.isOAuthFinal(request)) {
      LOG.debug("Login-FINAL " + this);
      login(oauth.loginPhase2(request, response));
      if (isLoggedIn()) {
        LOG.debug("Login-SUCCESS " + this);
        response.sendRedirect(OAuthProtocol.getTargetOAuthFinal(request, token));
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
    hub = null;
    token = null;
  }

  public OAuthProtocol getOAuthProtocol() {
    return oauth;
  }

  public GitHub login(AccessToken authToken) throws IOException {
    this.token = authToken;
    this.hub = GitHub.connectUsingOAuth(authToken.access_token);
    this.myself = hub.getMyself();
    return this.hub;
  }

  @Override
  public String toString() {
    return "GitHubLogin [token=" + token + ", myself=" + myself + "]";
  }
}
