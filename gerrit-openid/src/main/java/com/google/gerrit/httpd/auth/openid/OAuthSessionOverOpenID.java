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

import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.extensions.auth.oauth.OAuthVerifier;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.httpd.CanonicalWebUrl;
import com.google.gerrit.httpd.LoginUrlToken;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthResult;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.servlet.SessionScoped;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** OAuth protocol implementation */
@SessionScoped
class OAuthSessionOverOpenID {
  static final String GERRIT_LOGIN = "/login";
  private static final Logger log = LoggerFactory.getLogger(OAuthSessionOverOpenID.class);
  private static final SecureRandom randomState = newRandomGenerator();
  private final String state;
  private final DynamicItem<WebSession> webSession;
  private final Provider<IdentifiedUser> identifiedUser;
  private final AccountManager accountManager;
  private final CanonicalWebUrl urlProvider;
  private OAuthServiceProvider serviceProvider;
  private OAuthToken token;
  private OAuthUserInfo user;
  private String redirectToken;
  private boolean linkMode;

  @Inject
  OAuthSessionOverOpenID(
      DynamicItem<WebSession> webSession,
      Provider<IdentifiedUser> identifiedUser,
      AccountManager accountManager,
      CanonicalWebUrl urlProvider) {
    this.state = generateRandomState();
    this.webSession = webSession;
    this.identifiedUser = identifiedUser;
    this.accountManager = accountManager;
    this.urlProvider = urlProvider;
  }

  boolean isLoggedIn() {
    return token != null && user != null;
  }

  boolean isOAuthFinal(HttpServletRequest request) {
    return Strings.emptyToNull(request.getParameter("code")) != null;
  }

  boolean login(
      HttpServletRequest request, HttpServletResponse response, OAuthServiceProvider oauth)
      throws IOException {
    log.debug("Login " + this);

    if (isOAuthFinal(request)) {
      if (!checkState(request)) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        return false;
      }

      log.debug("Login-Retrieve-User " + this);
      token = oauth.getAccessToken(new OAuthVerifier(request.getParameter("code")));
      user = oauth.getUserInfo(token);

      if (isLoggedIn()) {
        log.debug("Login-SUCCESS " + this);
        authenticateAndRedirect(request, response);
        return true;
      }
      response.sendError(SC_UNAUTHORIZED);
      return false;
    }
    log.debug("Login-PHASE1 " + this);
    redirectToken = LoginUrlToken.getToken(request);
    response.sendRedirect(oauth.getAuthorizationUrl() + "&state=" + state);
    return false;
  }

  private void authenticateAndRedirect(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException {
    com.google.gerrit.server.account.AuthRequest areq =
        new com.google.gerrit.server.account.AuthRequest(user.getExternalId());
    AuthResult arsp = null;
    try {
      String claimedIdentifier = user.getClaimedIdentity();
      Account.Id actualId = accountManager.lookup(user.getExternalId());
      Account.Id claimedId = null;

      // We try to retrieve claimed identity.
      // For some reason, for example staging instance
      // it may deviate from the really old OpenID identity.
      // What we want to avoid in any event is to create new
      // account instead of linking to the existing one.
      // That why we query it here, not to lose linking mode.
      if (!Strings.isNullOrEmpty(claimedIdentifier)) {
        claimedId = accountManager.lookup(claimedIdentifier);
        if (claimedId == null) {
          log.debug("Claimed identity is unknown");
        }
      }

      // Use case 1: claimed identity was provided during handshake phase
      // and user account exists for this identity
      if (claimedId != null) {
        log.debug("Claimed identity is set and is known");
        if (actualId != null) {
          if (claimedId.equals(actualId)) {
            // Both link to the same account, that's what we expected.
            log.debug("Both link to the same account. All is fine.");
          } else {
            // This is (for now) a fatal error. There are two records
            // for what might be the same user. The admin would have to
            // link the accounts manually.
            log.error(
                "OAuth accounts disagree over user identity:\n"
                    + "  Claimed ID: "
                    + claimedId
                    + " is "
                    + claimedIdentifier
                    + "\n"
                    + "  Delgate ID: "
                    + actualId
                    + " is "
                    + user.getExternalId());
            rsp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
          }
        } else {
          // Claimed account already exists: link to it.
          log.debug("Claimed account already exists: link to it.");
          try {
            accountManager.link(claimedId, areq);
          } catch (OrmException e) {
            log.error(
                "Cannot link: "
                    + user.getExternalId()
                    + " to user identity:\n"
                    + "  Claimed ID: "
                    + claimedId
                    + " is "
                    + claimedIdentifier);
            rsp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
          }
        }
      } else if (linkMode) {
        // Use case 2: link mode activated from the UI
        Account.Id accountId = identifiedUser.get().getAccountId();
        try {
          log.debug("Linking \"{}\" to \"{}\"", user.getExternalId(), accountId);
          accountManager.link(accountId, areq);
        } catch (OrmException e) {
          log.error("Cannot link: " + user.getExternalId() + " to user identity: " + accountId);
          rsp.sendError(HttpServletResponse.SC_FORBIDDEN);
          return;
        } finally {
          linkMode = false;
        }
      }
      areq.setUserName(user.getUserName());
      areq.setEmailAddress(user.getEmailAddress());
      areq.setDisplayName(user.getDisplayName());
      arsp = accountManager.authenticate(areq);
    } catch (AccountException e) {
      log.error("Unable to authenticate user \"" + user + "\"", e);
      rsp.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    webSession.get().login(arsp, true);
    StringBuilder rdr = new StringBuilder(urlProvider.get(req));
    rdr.append(Url.decode(redirectToken));
    rsp.sendRedirect(rdr.toString());
  }

  void logout() {
    token = null;
    user = null;
    redirectToken = null;
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
      throw new IllegalArgumentException("No SecureRandom available for GitHub authentication", e);
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

  public void setLinkMode(boolean linkMode) {
    this.linkMode = linkMode;
  }

  public boolean isLinkMode() {
    return linkMode;
  }
}
