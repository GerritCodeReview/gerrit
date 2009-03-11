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

package com.google.gerrit.server;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.SignInDialog;
import com.google.gerrit.client.SignInDialog.Mode;
import com.google.gerrit.client.openid.DiscoveryResult;
import com.google.gerrit.client.openid.OpenIdService;
import com.google.gerrit.client.openid.OpenIdUtil;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountExternalId;
import com.google.gerrit.client.reviewdb.AccountExternalIdAccess;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.gerrit.client.rpc.Common;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.server.ValidToken;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Transaction;

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
import org.w3c.dom.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class OpenIdServiceImpl implements OpenIdService {
  private static final Logger log =
      LoggerFactory.getLogger(OpenIdServiceImpl.class);

  private static final String P_IDENT = "gerrit.ident";
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

  private static OpenIdServiceImpl INSTANCE;

  static synchronized OpenIdServiceImpl getInstance() throws ConsumerException,
      OrmException, XsrfException, ServletException {
    if (INSTANCE == null) {
      INSTANCE = new OpenIdServiceImpl();
    }
    return INSTANCE;
  }

  private final GerritServer server;
  private final ConsumerManager manager;
  private final Document pleaseSetCookieDoc;

  private OpenIdServiceImpl() throws ConsumerException, OrmException,
      XsrfException, ServletException {
    server = GerritServer.getInstance();
    manager = new ConsumerManager();

    final String scHtmlName = "com/google/gerrit/public/SetCookie.html";
    pleaseSetCookieDoc = HtmlDomUtil.parseFile(scHtmlName);
    if (pleaseSetCookieDoc == null) {
      log.error("No " + scHtmlName + " in CLASSPATH");
      throw new ServletException("No " + scHtmlName + " in CLASSPATH");
    }
  }

  public void discover(final String openidIdentifier,
      final SignInDialog.Mode mode, final boolean remember,
      final String returnToken, final AsyncCallback<DiscoveryResult> callback) {
    if (Common.getGerritConfig().getLoginType() != SystemConfig.LoginType.OPENID) {
      callback.onFailure(new IllegalStateException("OpenID not enabled"));
      return;
    }

    final HttpServletRequest httpReq =
        GerritJsonServlet.getCurrentCall().getHttpServletRequest();
    final State state;
    state = init(httpReq, openidIdentifier, mode, remember, returnToken);
    if (state == null) {
      callback.onSuccess(new DiscoveryResult(false));
      return;
    }

    final AuthRequest aReq;
    try {
      aReq = manager.authenticate(state.discovered, state.retTo.toString());
      aReq.setRealm(state.contextUrl);

      final SRegRequest sregReq = SRegRequest.createFetchRequest();
      sregReq.addAttribute("fullname", true);
      sregReq.addAttribute("email", true);
      aReq.addExtension(sregReq);

      final FetchRequest fetch = FetchRequest.createFetchRequest();
      fetch.addAttribute("FirstName", SCHEMA_FIRSTNAME, true);
      fetch.addAttribute("LastName", SCHEMA_LASTNAME, true);
      fetch.addAttribute("Email", SCHEMA_EMAIL, true);
      aReq.addExtension(fetch);

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

  /** Called by {@link OpenIdLoginServlet} doGet, doPost */
  void doAuth(final HttpServletRequest req, final HttpServletResponse rsp)
      throws Exception {
    if (false) {
      System.err.println(req.getMethod() + " /login");
      for (final String n : new TreeMap<String, Object>(req.getParameterMap())
          .keySet()) {
        for (final String v : req.getParameterValues(n)) {
          System.err.println("  " + n + "=" + v);
        }
      }
      System.err.println();
    }

    final String openidMode = req.getParameter(OPENID_MODE);
    if (OMODE_CANCEL.equals(openidMode)) {
      cancel(req, rsp);

    } else {
      // Process the authentication response.
      //
      final SignInDialog.Mode mode = signInMode(req);
      final String openidIdentifier = req.getParameter(P_IDENT);
      final String returnToken = req.getParameter(P_TOKEN);
      final boolean remember = "1".equals(req.getParameter(P_REMEMBER));
      final State state;

      state = init(req, openidIdentifier, mode, remember, returnToken);
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

      if (user == null) {
        // Authentication failed.
        //
        cancel(req, rsp);

      } else {
        // Authentication was successful.
        //
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
          final MessageExtension ext =
              authRsp.getExtension(AxMessage.OPENID_NS_AX);
          if (ext instanceof FetchResponse) {
            fetchRsp = (FetchResponse) ext;
          }
        }

        String fullname = null;
        String email = null;

        if (sregRsp != null) {
          fullname = sregRsp.getAttributeValue("fullname");
          email = sregRsp.getAttributeValue("email");

        } else if (fetchRsp != null) {
          final String firstName = fetchRsp.getAttributeValue("FirstName");
          final String lastName = fetchRsp.getAttributeValue("LastName");
          fullname = firstName + " " + lastName;
          email = fetchRsp.getAttributeValue("Email");
        }

        initializeAccount(req, rsp, user, fullname, email);
      }
    }
  }

  private void initializeAccount(final HttpServletRequest req,
      final HttpServletResponse rsp, final Identifier user,
      final String fullname, final String email) throws IOException {
    final SignInDialog.Mode mode = signInMode(req);
    Account account = null;
    if (user != null) {
      try {
        final ReviewDb d = Common.getSchemaFactory().open();
        try {
          switch (mode) {
            case SIGN_IN:
              account = openAccount(d, user, fullname, email);
              break;
            case LINK_IDENTIY:
              account = linkAccount(req, d, user, email);
              break;
          }
        } finally {
          d.close();
        }
      } catch (OrmException e) {
        log.error("Account lookup failed", e);
        account = null;
      }
    }

    String tok;
    try {
      final String idstr = String.valueOf(account.getId().get());
      tok = server.getAccountToken().newToken(idstr);
    } catch (XsrfException e) {
      log.error("Account cookie signature impossible", e);
      account = null;
      tok = "";
    }

    Cookie c = new Cookie(Gerrit.ACCOUNT_COOKIE, tok);
    c.setPath(req.getContextPath() + "/");

    if (account == null) {
      if (mode == SignInDialog.Mode.SIGN_IN) {
        c.setMaxAge(0);
        rsp.addCookie(c);
      }
      cancel(req, rsp);

    } else if (mode == SignInDialog.Mode.SIGN_IN) {
      c.setMaxAge(server.getSessionAge());
      rsp.addCookie(c);

      final boolean remember = "1".equals(req.getParameter(P_REMEMBER));
      c = new Cookie(OpenIdUtil.LASTID_COOKIE, "");
      c.setPath(req.getContextPath() + "/");
      if (remember) {
        c.setMaxAge(LASTID_AGE);
        c.setValue(user.getIdentifier());
      } else {
        c.setMaxAge(0);
      }
      rsp.addCookie(c);

      callback(req, rsp);

    } else {
      callback(req, rsp);
    }
  }

  private Account openAccount(final ReviewDb db, final Identifier user,
      final String fullname, final String email) throws OrmException {
    Account account;
    final AccountExternalIdAccess extAccess = db.accountExternalIds();
    AccountExternalId acctExt = lookup(extAccess, user.getIdentifier());

    if (acctExt == null && email != null
        && server.isAllowGoogleAccountUpgrade() && isGoogleAccount(user)) {
      acctExt = lookupGoogleAccount(extAccess, email);
      if (acctExt != null) {
        // Legacy user from Gerrit 1? Attach the OpenID identity.
        //
        final AccountExternalId openidExt =
            new AccountExternalId(new AccountExternalId.Key(acctExt
                .getAccountId(), user.getIdentifier()));
        extAccess.insert(Collections.singleton(openidExt));
        acctExt = openidExt;
      }
    }

    if (acctExt != null) {
      // Existing user; double check the email is current.
      //
      if (email != null && !email.equals(acctExt.getEmailAddress())) {
        acctExt.setEmailAddress(email);
      }
      acctExt.setLastUsedOn();
      extAccess.update(Collections.singleton(acctExt));
      account = Common.getAccountCache().get(acctExt.getAccountId(), db);
    } else {
      account = null;
    }

    if (account == null) {
      // New user; create an account entity for them.
      //
      final Transaction txn = db.beginTransaction();

      account = new Account(new Account.Id(db.nextAccountId()));
      account.setFullName(fullname);
      account.setPreferredEmail(email);

      acctExt =
          new AccountExternalId(new AccountExternalId.Key(account.getId(), user
              .getIdentifier()));
      acctExt.setLastUsedOn();
      acctExt.setEmailAddress(email);

      db.accounts().insert(Collections.singleton(account), txn);
      extAccess.insert(Collections.singleton(acctExt), txn);
      txn.commit();
    }
    return account;
  }

  private Account linkAccount(final HttpServletRequest req, final ReviewDb db,
      final Identifier user, final String email) throws OrmException {
    final Cookie[] cookies = req.getCookies();
    if (cookies == null) {
      return null;
    }
    Account.Id me = null;
    for (final Cookie c : cookies) {
      if (Gerrit.ACCOUNT_COOKIE.equals(c.getName())) {
        try {
          final ValidToken tok =
              server.getAccountToken().checkToken(c.getValue(), null);
          if (tok == null) {
            return null;
          }
          me = Account.Id.parse(tok.getData());
          break;
        } catch (XsrfException e) {
          return null;
        } catch (RuntimeException e) {
          return null;
        }
      }
    }
    if (me == null) {
      return null;
    }

    final Account account = Common.getAccountCache().get(me, db);
    if (account == null) {
      return null;
    }

    final AccountExternalId.Key idKey =
        new AccountExternalId.Key(account.getId(), user.getIdentifier());
    AccountExternalId id = db.accountExternalIds().get(idKey);
    if (id == null) {
      id = new AccountExternalId(idKey);
      id.setLastUsedOn();
      id.setEmailAddress(email);
      db.accountExternalIds().insert(Collections.singleton(id));
      Common.getGroupCache().invalidate(account.getId());
    } else {
      if (email != null && !email.equals(id.getEmailAddress())) {
        id.setEmailAddress(email);
      }
      id.setLastUsedOn();
      db.accountExternalIds().update(Collections.singleton(id));
    }
    return account;
  }

  private static Mode signInMode(final HttpServletRequest req) {
    try {
      return SignInDialog.Mode.valueOf(req.getParameter(P_MODE));
    } catch (RuntimeException e) {
      return SignInDialog.Mode.SIGN_IN;
    }
  }

  private static AccountExternalId lookup(
      final AccountExternalIdAccess extAccess, final String id)
      throws OrmException {
    final List<AccountExternalId> extRes = extAccess.byExternal(id).toList();
    switch (extRes.size()) {
      case 0:
        return null;
      case 1:
        return extRes.get(0);
      default:
        throw new OrmException("More than one account matches: " + id);
    }
  }

  private static boolean isGoogleAccount(final Identifier user) {
    return user.getIdentifier().startsWith(OpenIdUtil.URL_GOOGLE + "?");
  }

  private static AccountExternalId lookupGoogleAccount(
      final AccountExternalIdAccess extAccess, final String email)
      throws OrmException {
    // We may have multiple records which match the email address, but
    // all under the same account. This happens when the user does a
    // login through different server hostnames, as Google issues
    // unique OpenID tokens per server.
    //
    // Match to an existing account only if there is exactly one record
    // for this email using the generic Google identity.
    //
    final List<AccountExternalId> m = new ArrayList<AccountExternalId>();
    for (final AccountExternalId e : extAccess.byEmailAddress(email)) {
      if (e.getExternalId().equals("Google Account " + email)) {
        m.add(e);
      }
    }
    return m.size() == 1 ? m.get(0) : null;
  }

  private static void callback(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    final StringBuilder rdr = new StringBuilder();
    rdr.append(GerritServer.serverUrl(req));
    rdr.append("Gerrit");
    final String token = req.getParameter(P_TOKEN);
    if (token != null) {
      rdr.append('#');
      rdr.append(token);
    }
    rsp.sendRedirect(rdr.toString());
  }

  private static void cancel(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    callback(req, rsp);
  }

  private State init(final HttpServletRequest httpReq,
      final String openidIdentifier, final SignInDialog.Mode mode,
      final boolean remember, final String returnToken) {
    List<?> servers;
    try {
      servers = manager.discover(openidIdentifier);
    } catch (DiscoveryException de) {
      servers = null;
    }
    if (servers == null || servers.isEmpty()) {
      return null;
    }

    final String contextUrl = GerritServer.serverUrl(httpReq);
    final DiscoveryInformation discovered = manager.associate(servers);
    final UrlEncoded retTo = new UrlEncoded(contextUrl + "login");
    retTo.put(P_IDENT, openidIdentifier);
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
