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

package com.google.gerrit.server.openid;

import com.google.gerrit.client.Link;
import com.google.gerrit.client.SignInDialog;
import com.google.gerrit.client.SignInDialog.Mode;
import com.google.gerrit.client.openid.DiscoveryResult;
import com.google.gerrit.client.openid.OpenIdService;
import com.google.gerrit.client.openid.OpenIdUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.UrlEncoded;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.SelfPopulatingCache;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.Nullable;
import com.google.gerrit.server.http.WebSession;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.KeyUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.openid4java.consumer.ConsumerException;
import org.openid4java.consumer.ConsumerManager;
import org.openid4java.consumer.VerificationResult;
import org.openid4java.discovery.DiscoveryException;
import org.openid4java.discovery.DiscoveryInformation;
import org.openid4java.discovery.Identifier;
import org.openid4java.message.AuthRequest;
import org.openid4java.message.Message;
import org.openid4java.message.MessageException;
import org.openid4java.message.MessageExtension;
import org.openid4java.message.ParameterList;
import org.openid4java.message.ax.AxMessage;
import org.openid4java.message.ax.FetchRequest;
import org.openid4java.message.ax.FetchResponse;
import org.openid4java.message.sreg.SRegMessage;
import org.openid4java.message.sreg.SRegRequest;
import org.openid4java.message.sreg.SRegResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
class OpenIdServiceImpl implements OpenIdService {
  private static final Logger log =
      LoggerFactory.getLogger(OpenIdServiceImpl.class);

  static final String RETURN_URL = "OpenID";

  private static final String P_MODE = "gerrit.mode";
  private static final String P_TOKEN = "gerrit.token";
  private static final String P_REMEMBER = "gerrit.remember";
  private static final int LASTID_AGE = 365 * 24 * 60 * 60; // seconds

  private static final String OPENID_MODE = "openid.mode";
  private static final String OMODE_CANCEL = "cancel";

  private static final String SCHEMA_EMAIL =
      "http://schema.openid.net/contact/email";
  private static final String SCHEMA_FIRSTNAME =
      "http://schema.openid.net/namePerson/first";
  private static final String SCHEMA_LASTNAME =
      "http://schema.openid.net/namePerson/last";

  private final Provider<WebSession> webSession;
  private final Provider<IdentifiedUser> identifiedUser;
  private final Provider<String> urlProvider;
  private final AccountManager accountManager;
  private final ConsumerManager manager;
  private final SelfPopulatingCache<String, List> discoveryCache;

  @Inject
  OpenIdServiceImpl(final Provider<WebSession> cf,
      final Provider<IdentifiedUser> iu,
      @CanonicalWebUrl @Nullable final Provider<String> up,
      @Named("openid") final Cache<String, List> openidCache,
      final AccountManager am) throws ConsumerException {
    webSession = cf;
    identifiedUser = iu;
    urlProvider = up;
    accountManager = am;
    manager = new ConsumerManager();

    discoveryCache = new SelfPopulatingCache<String, List>(openidCache) {
      @Override
      protected List createEntry(final String url) throws Exception {
        try {
          final List<?> list = manager.discover(url);
          return list != null && !list.isEmpty() ? list : null;
        } catch (DiscoveryException e) {
          return null;
        }
      }
    };
  }

  public void discover(final String openidIdentifier,
      final SignInDialog.Mode mode, final boolean remember,
      final String returnToken, final AsyncCallback<DiscoveryResult> callback) {
    final State state;
    state = init(openidIdentifier, mode, remember, returnToken);
    if (state == null) {
      callback.onSuccess(new DiscoveryResult(false));
      return;
    }

    final AuthRequest aReq;
    try {
      aReq = manager.authenticate(state.discovered, state.retTo.toString());
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
    } catch (MessageException e) {
      callback.onSuccess(new DiscoveryResult(false));
      return;
    } catch (ConsumerException e) {
      callback.onSuccess(new DiscoveryResult(false));
      return;
    }

    callback.onSuccess(new DiscoveryResult(true, aReq.getDestinationUrl(false),
        aReq.getParameterMap()));
  }

  private boolean requestRegistration(final AuthRequest aReq) {
    if (AuthRequest.SELECT_ID.equals(aReq.getIdentity())) {
      // We don't know anything about the identity, as the provider
      // will offer the user a way to indicate their identity. Skip
      // any database query operation and assume we must ask for the
      // registration information, in case the identity is new to us.
      //
      return true;

    } else {
      // We might already have this account on file. Look for it.
      //
      return accountManager.equals(aReq.getIdentity());
    }
  }

  /** Called by {@link OpenIdLoginServlet} doGet, doPost */
  void doAuth(final HttpServletRequest req, final HttpServletResponse rsp)
      throws Exception {
    if (OMODE_CANCEL.equals(req.getParameter(OPENID_MODE))) {
      cancel(req, rsp);
      return;
    }

    // Process the authentication response.
    //
    final SignInDialog.Mode mode = signInMode(req);
    final String openidIdentifier = req.getParameter("openid.identity");
    final String returnToken = req.getParameter(P_TOKEN);
    final boolean remember = "1".equals(req.getParameter(P_REMEMBER));
    final State state;

    state = init(openidIdentifier, mode, remember, returnToken);
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
        manager.verify(state.retTo.toString(), new ParameterList(req
            .getParameterMap()), state.discovered);
    final Identifier user = result.getVerifiedId();
    if (user == null /* authentication failure */) {
      if ("Nonce verification failed.".equals(result.getStatusMsg())) {
        // We might be suffering from clock skew on this system.
        //
        log.error("OpenID failure: " + result.getStatusMsg()
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

    if (authRsp.hasExtension(SRegMessage.OPENID_NS_SREG)) {
      final MessageExtension ext =
          authRsp.getExtension(SRegMessage.OPENID_NS_SREG);
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
        new com.google.gerrit.server.account.AuthRequest(user.getIdentifier());

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

    try {
      switch (mode) {
        case REGISTER:
        case SIGN_IN:
          final com.google.gerrit.server.account.AuthResult arsp;
          arsp = accountManager.authenticate(areq);

          final Cookie lastId = new Cookie(OpenIdUtil.LASTID_COOKIE, "");
          lastId.setPath(req.getContextPath() + "/");
          if (remember) {
            lastId.setValue(user.getIdentifier());
            lastId.setMaxAge(LASTID_AGE);
          } else {
            lastId.setMaxAge(0);
          }
          rsp.addCookie(lastId);
          webSession.get().login(arsp.getAccountId(), remember);
          callback(arsp.isNew(), req, rsp);
          break;

        case LINK_IDENTIY:
          accountManager.link(identifiedUser.get().getAccountId(), areq);
          callback(false, req, rsp);
          break;
      }
    } catch (AccountException e) {
      log.error("OpenID authentication failure", e);
      cancelWithError(req, rsp, "Contact site administrator");
    }
  }

  private boolean isSignIn(final SignInDialog.Mode mode) {
    switch (mode) {
      case SIGN_IN:
      case REGISTER:
        return true;
      default:
        return false;
    }
  }

  private static Mode signInMode(final HttpServletRequest req) {
    try {
      return SignInDialog.Mode.valueOf(req.getParameter(P_MODE));
    } catch (RuntimeException e) {
      return SignInDialog.Mode.SIGN_IN;
    }
  }

  private void callback(final boolean isNew, final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    String token = req.getParameter(P_TOKEN);
    if (token == null || token.isEmpty() || token.startsWith("SignInFailure,")) {
      token = Link.MINE;
    }

    final StringBuilder rdr = new StringBuilder();
    rdr.append(urlProvider.get());
    rdr.append('#');
    if (isNew) {
      rdr.append(Link.REGISTER);
      rdr.append(',');
    }
    rdr.append(token);
    rsp.sendRedirect(rdr.toString());
  }

  private void cancel(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    if (isSignIn(signInMode(req))) {
      webSession.get().logout();
    }
    callback(false, req, rsp);
  }

  private void cancelWithError(final HttpServletRequest req,
      final HttpServletResponse rsp, final String errorDetail)
      throws IOException {
    final SignInDialog.Mode mode = signInMode(req);
    if (isSignIn(mode)) {
      webSession.get().logout();
    }
    final StringBuilder rdr = new StringBuilder();
    rdr.append(urlProvider.get());
    rdr.append('#');
    rdr.append("SignInFailure");
    rdr.append(',');
    rdr.append(mode.name());
    rdr.append(',');
    rdr.append(errorDetail != null ? KeyUtil.encode(errorDetail) : "");
    rsp.sendRedirect(rdr.toString());
  }

  private State init(final String openidIdentifier,
      final SignInDialog.Mode mode, final boolean remember,
      final String returnToken) {
    final List<?> list = discoveryCache.get(openidIdentifier);
    if (list == null || list.isEmpty()) {
      return null;
    }

    final String contextUrl = urlProvider.get();
    final DiscoveryInformation discovered = manager.associate(list);
    final UrlEncoded retTo = new UrlEncoded(contextUrl + RETURN_URL);
    retTo.put(P_MODE, mode.name());
    if (returnToken != null && returnToken.length() > 0) {
      retTo.put(P_TOKEN, returnToken);
    }
    if (remember) {
      retTo.put(P_REMEMBER, "1");
    }
    return new State(discovered, retTo, contextUrl);
  }

  private static class State {
    final DiscoveryInformation discovered;
    final UrlEncoded retTo;
    final String contextUrl;

    State(final DiscoveryInformation d, final UrlEncoded r, final String c) {
      discovered = d;
      retTo = r;
      contextUrl = c;
    }
  }
}
