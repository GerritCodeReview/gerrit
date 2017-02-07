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
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.httpd.CanonicalWebUrl;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.AuthResult;
import com.google.gerrit.server.auth.oauth.OAuthTokenCache;
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

@SessionScoped
/* OAuth protocol implementation */
class OAuthSession {
  private static final Logger log = LoggerFactory.getLogger(OAuthSession.class);
  private static final SecureRandom randomState = newRandomGenerator();
  private final String state;
  private final DynamicItem<WebSession> webSession;
  private final Provider<IdentifiedUser> identifiedUser;
  private final AccountManager accountManager;
  private final CanonicalWebUrl urlProvider;
  private final OAuthTokenCache tokenCache;
  private OAuthServiceProvider serviceProvider;
  private OAuthUserInfo user;
  private Account.Id accountId;
  private String redirectToken;
  private boolean linkMode;

  @Inject
  OAuthSession(
      DynamicItem<WebSession> webSession,
      Provider<IdentifiedUser> identifiedUser,
      AccountManager accountManager,
      CanonicalWebUrl urlProvider,
      OAuthTokenCache tokenCache) {
    this.state = generateRandomState();
    this.identifiedUser = identifiedUser;
    this.webSession = webSession;
    this.accountManager = accountManager;
    this.urlProvider = urlProvider;
    this.tokenCache = tokenCache;
  }

  boolean isLoggedIn() {
    return user != null;
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
      OAuthToken token = oauth.getAccessToken(new OAuthVerifier(request.getParameter("code")));
      user = oauth.getUserInfo(token);

      if (isLoggedIn()) {
        log.debug("Login-SUCCESS " + this);
        authenticateAndRedirect(request, response, token);
        return true;
      }
      response.sendError(SC_UNAUTHORIZED);
      return false;
    }
    log.debug("Login-PHASE1 " + this);
    redirectToken = request.getRequestURI();
    // We are here in content of filter.
    // Due to this Jetty limitation:
    // https://bz.apache.org/bugzilla/show_bug.cgi?id=28323
    // we cannot use LoginUrlToken.getToken() method,
    // because it relies on getPathInfo() and it is always null here.
    redirectToken = redirectToken.substring(request.getContextPath().length());
    response.sendRedirect(oauth.getAuthorizationUrl() + "&state=" + state);
    return false;
  }

  private void authenticateAndRedirect(
      HttpServletRequest req, HttpServletResponse rsp, OAuthToken token) throws IOException {
    AuthRequest areq = new AuthRequest(user.getExternalId());
    AuthResult arsp;
    try {
      String claimedIdentifier = user.getClaimedIdentity();
      if (!Strings.isNullOrEmpty(claimedIdentifier)) {
        if (!authenticateWithIdentityClaimedDuringHandshake(areq, rsp, claimedIdentifier)) {
          return;
        }
      } else if (linkMode) {
        if (!authenticateWithLinkedIdentity(areq, rsp)) {
          return;
        }
      }
      areq.setUserName(user.getUserName());
      areq.setEmailAddress(user.getEmailAddress());
      areq.setDisplayName(user.getDisplayName());
      arsp = accountManager.authenticate(areq);

      accountId = arsp.getAccountId();
      tokenCache.put(accountId, token);
    } catch (AccountException e) {
      log.error("Unable to authenticate user \"" + user + "\"", e);
      rsp.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    webSession.get().login(arsp, true);
    String suffix = redirectToken.substring(OAuthWebFilter.GERRIT_LOGIN.length() + 1);
    StringBuilder rdr = new StringBuilder(urlProvider.get(req));
    rdr.append(Url.decode(suffix));
    rsp.sendRedirect(rdr.toString());
  }

  private boolean authenticateWithIdentityClaimedDuringHandshake(
      AuthRequest req, HttpServletResponse rsp, String claimedIdentifier)
      throws AccountException, IOException {
    Account.Id claimedId = accountManager.lookup(claimedIdentifier);
    Account.Id actualId = accountManager.lookup(user.getExternalId());
    if (claimedId != null && actualId != null) {
      if (claimedId.equals(actualId)) {
        // Both link to the same account, that's what we expected.
        log.debug("OAuth2: claimed identity equals current id");
      } else {
        // This is (for now) a fatal error. There are two records
        // for what might be the same user.
        //
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
        return false;
      }
    } else if (claimedId != null && actualId == null) {
      // Claimed account already exists: link to it.
      //
      log.info("OAuth2: linking claimed identity to {}", claimedId.toString());
      try {
        accountManager.link(claimedId, req);
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
        return false;
      }
    }
    return true;
  }

  private boolean authenticateWithLinkedIdentity(AuthRequest areq, HttpServletResponse rsp)
      throws AccountException, IOException {
    try {
      accountManager.link(identifiedUser.get().getAccountId(), areq);
    } catch (OrmException e) {
      log.error(
          "Cannot link: "
              + user.getExternalId()
              + " to user identity: "
              + identifiedUser.get().getAccountId());
      rsp.sendError(HttpServletResponse.SC_FORBIDDEN);
      return false;
    } finally {
      linkMode = false;
    }
    return true;
  }

  void logout() {
    if (accountId != null) {
      tokenCache.remove(accountId);
      accountId = null;
    }
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
    return "OAuthSession [token=" + tokenCache.get(accountId) + ", user=" + user + "]";
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
