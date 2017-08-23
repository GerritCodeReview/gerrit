// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.auth.openid.OpenIdUrls;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.httpd.CanonicalWebUrl;
import com.google.gerrit.httpd.ProxyProperties;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.UrlEncoded;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.auth.openid.OpenIdProviderPattern;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gwtorm.client.KeyUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.lib.Config;
import org.openid4java.consumer.ConsumerException;
import org.openid4java.consumer.ConsumerManager;
import org.openid4java.consumer.VerificationResult;
import org.openid4java.discovery.DiscoveryException;
import org.openid4java.discovery.DiscoveryInformation;
import org.openid4java.message.AuthRequest;
import org.openid4java.message.Message;
import org.openid4java.message.MessageException;
import org.openid4java.message.MessageExtension;
import org.openid4java.message.ParameterList;
import org.openid4java.message.ax.AxMessage;
import org.openid4java.message.ax.FetchRequest;
import org.openid4java.message.ax.FetchResponse;
import org.openid4java.message.pape.PapeMessage;
import org.openid4java.message.pape.PapeRequest;
import org.openid4java.message.pape.PapeResponse;
import org.openid4java.message.sreg.SRegMessage;
import org.openid4java.message.sreg.SRegRequest;
import org.openid4java.message.sreg.SRegResponse;
import org.openid4java.util.HttpClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class OpenIdServiceImpl {
  private static final Logger log = LoggerFactory.getLogger(OpenIdServiceImpl.class);

  static final String RETURN_URL = "OpenID";

  private static final String P_MODE = "gerrit.mode";
  private static final String P_TOKEN = "gerrit.token";
  private static final String P_REMEMBER = "gerrit.remember";
  private static final String P_CLAIMED = "gerrit.claimed";
  private static final int LASTID_AGE = 365 * 24 * 60 * 60; // seconds

  private static final String OPENID_MODE = "openid.mode";
  private static final String OMODE_CANCEL = "cancel";

  private static final String SCHEMA_EMAIL = "http://schema.openid.net/contact/email";
  private static final String SCHEMA_FIRSTNAME = "http://schema.openid.net/namePerson/first";
  private static final String SCHEMA_LASTNAME = "http://schema.openid.net/namePerson/last";

  private final DynamicItem<WebSession> webSession;
  private final Provider<IdentifiedUser> identifiedUser;
  private final CanonicalWebUrl urlProvider;
  private final AccountManager accountManager;
  private final ConsumerManager manager;
  private final List<OpenIdProviderPattern> allowedOpenIDs;
  private final List<String> openIdDomains;

  /** Maximum age, in seconds, before forcing re-authentication of account. */
  private final int papeMaxAuthAge;

  @Inject
  OpenIdServiceImpl(
      DynamicItem<WebSession> cf,
      Provider<IdentifiedUser> iu,
      CanonicalWebUrl up,
      @GerritServerConfig Config config,
      AuthConfig ac,
      AccountManager am,
      ProxyProperties proxyProperties) {

    if (proxyProperties.getProxyUrl() != null) {
      final org.openid4java.util.ProxyProperties proxy = new org.openid4java.util.ProxyProperties();
      URL url = proxyProperties.getProxyUrl();
      proxy.setProxyHostName(url.getHost());
      proxy.setProxyPort(url.getPort());
      proxy.setUserName(proxyProperties.getUsername());
      proxy.setPassword(proxyProperties.getPassword());
      HttpClientFactory.setProxyProperties(proxy);
    }

    webSession = cf;
    identifiedUser = iu;
    urlProvider = up;
    accountManager = am;
    manager = new ConsumerManager();
    allowedOpenIDs = ac.getAllowedOpenIDs();
    openIdDomains = ac.getOpenIdDomains();
    papeMaxAuthAge =
        (int)
            ConfigUtil.getTimeUnit(
                config, //
                "auth",
                null,
                "maxOpenIdSessionAge",
                -1,
                TimeUnit.SECONDS);
  }

  @SuppressWarnings("unchecked")
  DiscoveryResult discover(
      HttpServletRequest req,
      String openidIdentifier,
      SignInMode mode,
      boolean remember,
      String returnToken) {
    final State state;
    state = init(req, openidIdentifier, mode, remember, returnToken);
    if (state == null) {
      return new DiscoveryResult(DiscoveryResult.Status.NO_PROVIDER);
    }

    final AuthRequest aReq;
    try {
      aReq = manager.authenticate(state.discovered, state.retTo.toString());
      log.debug("OpenID: openid-realm={}", state.contextUrl);
      aReq.setRealm(state.contextUrl);

      if (requestRegistration(aReq)) {
        final SRegRequest sregReq = SRegRequest.createFetchRequest();
        sregReq.addAttribute("fullname", true);
        sregReq.addAttribute("email", true);
        aReq.addExtension(sregReq);

        final FetchRequest fetch = FetchRequest.createFetchRequest();
        fetch.addAttribute("FirstName", SCHEMA_FIRSTNAME, true);
        fetch.addAttribute("LastName", SCHEMA_LASTNAME, true);
        fetch.addAttribute("Email", SCHEMA_EMAIL, true);
        aReq.addExtension(fetch);
      }

      if (0 <= papeMaxAuthAge) {
        final PapeRequest pape = PapeRequest.createPapeRequest();
        pape.setMaxAuthAge(papeMaxAuthAge);
        aReq.addExtension(pape);
      }
    } catch (MessageException e) {
      log.error("Cannot create OpenID redirect for " + openidIdentifier, e);
      return new DiscoveryResult(DiscoveryResult.Status.ERROR);
    } catch (ConsumerException e) {
      log.error("Cannot create OpenID redirect for " + openidIdentifier, e);
      return new DiscoveryResult(DiscoveryResult.Status.ERROR);
    }

    return new DiscoveryResult(aReq.getDestinationUrl(false), aReq.getParameterMap());
  }

  private boolean requestRegistration(AuthRequest aReq) {
    if (AuthRequest.SELECT_ID.equals(aReq.getIdentity())) {
      // We don't know anything about the identity, as the provider
      // will offer the user a way to indicate their identity. Skip
      // any database query operation and assume we must ask for the
      // registration information, in case the identity is new to us.
      //
      return true;
    }

    // We might already have this account on file. Look for it.
    //
    try {
      return accountManager.lookup(aReq.getIdentity()) == null;
    } catch (AccountException e) {
      log.warn("Cannot determine if user account exists", e);
      return true;
    }
  }

  /** Called by {@link OpenIdLoginServlet} doGet, doPost */
  void doAuth(HttpServletRequest req, HttpServletResponse rsp) throws Exception {
    if (OMODE_CANCEL.equals(req.getParameter(OPENID_MODE))) {
      cancel(req, rsp);
      return;
    }

    // Process the authentication response.
    //
    final SignInMode mode = signInMode(req);
    final String openidIdentifier = req.getParameter("openid.identity");
    final String claimedIdentifier = req.getParameter(P_CLAIMED);
    final String returnToken = req.getParameter(P_TOKEN);
    final boolean remember = "1".equals(req.getParameter(P_REMEMBER));
    final String rediscoverIdentifier =
        claimedIdentifier != null ? claimedIdentifier : openidIdentifier;
    final State state;

    if (!isAllowedOpenID(rediscoverIdentifier)
        || !isAllowedOpenID(openidIdentifier)
        || (claimedIdentifier != null && !isAllowedOpenID(claimedIdentifier))) {
      cancelWithError(req, rsp, "Provider not allowed");
      return;
    }

    state = init(req, rediscoverIdentifier, mode, remember, returnToken);
    if (state == null) {
      // Re-discovery must have failed, we can't run a login.
      //
      cancel(req, rsp);
      return;
    }

    final String returnTo = req.getParameter("openid.return_to");
    if (returnTo != null && returnTo.contains("openid.rpnonce=")) {
      // Some providers (claimid.com) seem to embed these request
      // parameters into our return_to URL, and then give us them
      // in the return_to request parameter. But not all.
      //
      state.retTo.put("openid.rpnonce", req.getParameter("openid.rpnonce"));
      state.retTo.put("openid.rpsig", req.getParameter("openid.rpsig"));
    }

    final VerificationResult result =
        manager.verify(
            state.retTo.toString(), new ParameterList(req.getParameterMap()), state.discovered);
    if (result.getVerifiedId() == null /* authentication failure */) {
      if ("Nonce verification failed.".equals(result.getStatusMsg())) {
        // We might be suffering from clock skew on this system.
        //
        log.error(
            "OpenID failure: "
                + result.getStatusMsg()
                + "  Likely caused by clock skew on this server,"
                + " install/configure NTP.");
        cancelWithError(req, rsp, result.getStatusMsg());

      } else if (result.getStatusMsg() != null) {
        // Authentication failed.
        //
        log.error("OpenID failure: " + result.getStatusMsg());
        cancelWithError(req, rsp, result.getStatusMsg());

      } else {
        // Assume authentication was canceled.
        //
        cancel(req, rsp);
      }
      return;
    }

    final Message authRsp = result.getAuthResponse();
    SRegResponse sregRsp = null;
    FetchResponse fetchRsp = null;

    if (0 <= papeMaxAuthAge) {
      PapeResponse ext;
      boolean unsupported = false;

      try {
        ext = (PapeResponse) authRsp.getExtension(PapeMessage.OPENID_NS_PAPE);
      } catch (MessageException err) {
        // Far too many providers are unable to provide PAPE extensions
        // right now. Instead of blocking all of them log the error and
        // let the authentication complete anyway.
        //
        log.error("Invalid PAPE response " + openidIdentifier + ": " + err);
        unsupported = true;
        ext = null;
      }
      if (!unsupported && ext == null) {
        log.error("No PAPE extension response from " + openidIdentifier);
        cancelWithError(req, rsp, "OpenID provider does not support PAPE.");
        return;
      }
    }

    if (authRsp.hasExtension(SRegMessage.OPENID_NS_SREG)) {
      final MessageExtension ext = authRsp.getExtension(SRegMessage.OPENID_NS_SREG);
      if (ext instanceof SRegResponse) {
        sregRsp = (SRegResponse) ext;
      }
    }

    if (authRsp.hasExtension(AxMessage.OPENID_NS_AX)) {
      final MessageExtension ext = authRsp.getExtension(AxMessage.OPENID_NS_AX);
      if (ext instanceof FetchResponse) {
        fetchRsp = (FetchResponse) ext;
      }
    }

    final com.google.gerrit.server.account.AuthRequest areq =
        new com.google.gerrit.server.account.AuthRequest(ExternalId.Key.parse(openidIdentifier));

    if (sregRsp != null) {
      areq.setDisplayName(sregRsp.getAttributeValue("fullname"));
      areq.setEmailAddress(sregRsp.getAttributeValue("email"));

    } else if (fetchRsp != null) {
      final String firstName = fetchRsp.getAttributeValue("FirstName");
      final String lastName = fetchRsp.getAttributeValue("LastName");
      final StringBuilder n = new StringBuilder();
      if (firstName != null && firstName.length() > 0) {
        n.append(firstName);
      }
      if (lastName != null && lastName.length() > 0) {
        if (n.length() > 0) {
          n.append(' ');
        }
        n.append(lastName);
      }
      areq.setDisplayName(n.length() > 0 ? n.toString() : null);
      areq.setEmailAddress(fetchRsp.getAttributeValue("Email"));
    }

    if (openIdDomains != null && openIdDomains.size() > 0) {
      // Administrator limited email domains, which can be used for OpenID.
      // Login process will only work if the passed email matches one
      // of these domains.
      //
      final String email = areq.getEmailAddress();
      int emailAtIndex = email.lastIndexOf("@");
      if (emailAtIndex >= 0 && emailAtIndex < email.length() - 1) {
        final String emailDomain = email.substring(emailAtIndex);

        boolean match = false;
        for (String domain : openIdDomains) {
          if (emailDomain.equalsIgnoreCase(domain)) {
            match = true;
            break;
          }
        }

        if (!match) {
          log.error("Domain disallowed: " + emailDomain);
          cancelWithError(req, rsp, "Domain disallowed");
          return;
        }
      }
    }

    if (claimedIdentifier != null) {
      // The user used a claimed identity which has delegated to the verified
      // identity we have in our AuthRequest above. We still should have a
      // link between the two, so set one up if not present.
      //
      Optional<Account.Id> claimedId = accountManager.lookup(claimedIdentifier);
      Optional<Account.Id> actualId = accountManager.lookup(areq.getExternalIdKey().get());

      if (claimedId.isPresent() && actualId.isPresent()) {
        if (claimedId.get().equals(actualId.get())) {
          // Both link to the same account, that's what we expected.
        } else {
          // This is (for now) a fatal error. There are two records
          // for what might be the same user.
          //
          log.error(
              "OpenID accounts disagree over user identity:\n"
                  + "  Claimed ID: "
                  + claimedId.get()
                  + " is "
                  + claimedIdentifier
                  + "\n"
                  + "  Delgate ID: "
                  + actualId.get()
                  + " is "
                  + areq.getExternalIdKey());
          cancelWithError(req, rsp, "Contact site administrator");
          return;
        }

      } else if (!claimedId.isPresent() && actualId.isPresent()) {
        // Older account, the actual was already created but the claimed
        // was missing due to a bug in Gerrit. Link the claimed.
        //
        final com.google.gerrit.server.account.AuthRequest linkReq =
            new com.google.gerrit.server.account.AuthRequest(
                ExternalId.Key.parse(claimedIdentifier));
        linkReq.setDisplayName(areq.getDisplayName());
        linkReq.setEmailAddress(areq.getEmailAddress());
        accountManager.link(actualId.get(), linkReq);

      } else if (claimedId.isPresent() && !actualId.isPresent()) {
        // Claimed account already exists, but it smells like the user has
        // changed their delegate to point to a different provider. Link
        // the new provider.
        //
        accountManager.link(claimedId.get(), areq);

      } else {
        // Both are null, we are going to create a new account below.
      }
    }

    try {
      final com.google.gerrit.server.account.AuthResult arsp;
      switch (mode) {
        case REGISTER:
        case SIGN_IN:
          arsp = accountManager.authenticate(areq);

          final Cookie lastId = new Cookie(OpenIdUrls.LASTID_COOKIE, "");
          lastId.setPath(req.getContextPath() + "/login/");
          if (remember) {
            lastId.setValue(rediscoverIdentifier);
            lastId.setMaxAge(LASTID_AGE);
          } else {
            lastId.setMaxAge(0);
          }
          rsp.addCookie(lastId);
          webSession.get().login(arsp, remember);
          if (arsp.isNew() && claimedIdentifier != null) {
            final com.google.gerrit.server.account.AuthRequest linkReq =
                new com.google.gerrit.server.account.AuthRequest(
                    ExternalId.Key.parse(claimedIdentifier));
            linkReq.setDisplayName(areq.getDisplayName());
            linkReq.setEmailAddress(areq.getEmailAddress());
            accountManager.link(arsp.getAccountId(), linkReq);
          }
          callback(arsp.isNew(), req, rsp);
          break;

        case LINK_IDENTIY:
          {
            arsp = accountManager.link(identifiedUser.get().getAccountId(), areq);
            webSession.get().login(arsp, remember);
            callback(false, req, rsp);
            break;
          }
      }
    } catch (AccountException e) {
      log.error("OpenID authentication failure", e);
      cancelWithError(req, rsp, "Contact site administrator");
    }
  }

  private boolean isSignIn(SignInMode mode) {
    switch (mode) {
      case SIGN_IN:
      case REGISTER:
        return true;
      case LINK_IDENTIY:
      default:
        return false;
    }
  }

  private static SignInMode signInMode(HttpServletRequest req) {
    try {
      return SignInMode.valueOf(req.getParameter(P_MODE));
    } catch (RuntimeException e) {
      return SignInMode.SIGN_IN;
    }
  }

  private void callback(final boolean isNew, HttpServletRequest req, HttpServletResponse rsp)
      throws IOException {
    String token = req.getParameter(P_TOKEN);
    if (token == null || token.isEmpty() || token.startsWith("/SignInFailure,")) {
      token = PageLinks.MINE;
    }

    final StringBuilder rdr = new StringBuilder();
    rdr.append(urlProvider.get(req));
    if (isNew && !token.startsWith(PageLinks.REGISTER + "/")) {
      rdr.append('#' + PageLinks.REGISTER);
    }
    rdr.append(Url.decode(token));
    rsp.sendRedirect(rdr.toString());
  }

  private void cancel(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    if (isSignIn(signInMode(req))) {
      webSession.get().logout();
    }
    callback(false, req, rsp);
  }

  private void cancelWithError(
      final HttpServletRequest req, HttpServletResponse rsp, String errorDetail)
      throws IOException {
    final SignInMode mode = signInMode(req);
    if (isSignIn(mode)) {
      webSession.get().logout();
    }
    final StringBuilder rdr = new StringBuilder();
    rdr.append(urlProvider.get(req));
    rdr.append('#');
    rdr.append("SignInFailure");
    rdr.append(',');
    rdr.append(mode.name());
    rdr.append(',');
    rdr.append(errorDetail != null ? KeyUtil.encode(errorDetail) : "");
    rsp.sendRedirect(rdr.toString());
  }

  private State init(
      HttpServletRequest req,
      final String openidIdentifier,
      final SignInMode mode,
      final boolean remember,
      final String returnToken) {
    final List<?> list;
    try {
      list = manager.discover(openidIdentifier);
    } catch (DiscoveryException e) {
      log.error("Cannot discover OpenID " + openidIdentifier, e);
      return null;
    }
    if (list == null || list.isEmpty()) {
      return null;
    }

    final String contextUrl = urlProvider.get(req);
    final DiscoveryInformation discovered = manager.associate(list);
    final UrlEncoded retTo = new UrlEncoded(contextUrl + RETURN_URL);
    retTo.put(P_MODE, mode.name());
    if (returnToken != null && returnToken.length() > 0) {
      retTo.put(P_TOKEN, returnToken);
    }
    if (remember) {
      retTo.put(P_REMEMBER, "1");
    }
    if (discovered.hasClaimedIdentifier()) {
      retTo.put(P_CLAIMED, discovered.getClaimedIdentifier().getIdentifier());
    }
    return new State(discovered, retTo, contextUrl);
  }

  boolean isAllowedOpenID(String id) {
    for (OpenIdProviderPattern pattern : allowedOpenIDs) {
      if (pattern.matches(id)) {
        return true;
      }
    }
    return false;
  }

  private static class State {
    final DiscoveryInformation discovered;
    final UrlEncoded retTo;
    final String contextUrl;

    State(DiscoveryInformation d, UrlEncoded r, String c) {
      discovered = d;
      retTo = r;
      contextUrl = c;
    }
  }
}
