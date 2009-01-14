// Copyright 2008 Google Inc.
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
import com.google.gerrit.client.account.SignInResult;
import com.google.gerrit.client.openid.DiscoveryResult;
import com.google.gerrit.client.openid.OpenIdUtil;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountExternalId;
import com.google.gerrit.client.reviewdb.AccountExternalIdAccess;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gwt.user.server.rpc.RPCServletUtils;
import com.google.gwtjsonrpc.server.JsonServlet;
import com.google.gwtjsonrpc.server.ValidToken;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Transaction;

import com.dyuproject.openid.Constants;
import com.dyuproject.openid.DefaultDiscovery;
import com.dyuproject.openid.DiffieHellmanAssociation;
import com.dyuproject.openid.OpenIdContext;
import com.dyuproject.openid.OpenIdUser;
import com.dyuproject.openid.RelyingParty;
import com.dyuproject.openid.SimpleHttpConnector;

import org.mortbay.util.UrlEncoded;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Handles the <code>/login</code> URL for web based single-sign-on. */
public class OpenIdLoginServlet extends HttpServlet {
  private static final int LASTID_AGE = 365 * 24 * 60 * 60; // seconds
  private static final String AX_SCHEMA = "http://openid.net/srv/ax/1.0";
  private static final String OMODE_CANCEL = "cancel";
  private static final String GMODE_CHKCOOKIE = "gerrit.chkcookie";
  private static final String GMODE_SETCOOKIE = "gerrit.setcookie";

  private RelyingParty relyingParty;
  private GerritServer server;
  private Document pleaseSetCookieDoc;

  @Override
  public void init(final ServletConfig config) throws ServletException {
    super.init(config);

    try {
      server = GerritServer.getInstance();
    } catch (OrmException e) {
      e.printStackTrace();
      throw new ServletException("Cannot configure GerritServer", e);
    } catch (XsrfException e) {
      e.printStackTrace();
      throw new ServletException("Cannot configure GerritServer", e);
    }

    try {
      final OpenIdContext context = new OpenIdContext();
      context.setAssociation(new DiffieHellmanAssociation());
      context.setDiscovery(new GoogleAccountDiscovery(new DefaultDiscovery()));
      context.setHttpConnector(new SimpleHttpConnector());
      relyingParty = new RelyingParty(context, new GerritOpenIdUserManager());
    } catch (Throwable e) {
      e.printStackTrace();
      throw new RuntimeException("Cannot setup RelyingParty", e);
    }

    final String scHtmlName = "com/google/gerrit/public/SetCookie.html";
    pleaseSetCookieDoc = HtmlDomUtil.parseFile(scHtmlName);
    if (pleaseSetCookieDoc == null) {
      throw new ServletException("No " + scHtmlName + " in CLASSPATH");
    }
  }

  @Override
  public void doGet(final HttpServletRequest req, final HttpServletResponse rsp)
      throws IOException {
    doPost(req, rsp);
  }

  @Override
  public void doPost(final HttpServletRequest req, final HttpServletResponse rsp)
      throws IOException {
    try {
      doAuth(req, rsp);
    } catch (Exception e) {
      getServletContext().log("Unexpected error during authentication", e);
      callback(req, rsp, SignInResult.CANCEL);
    }
  }

  private void doAuth(final HttpServletRequest req,
      final HttpServletResponse rsp) throws Exception {
    if (false) {
      System.out.println(req.getMethod() + " /login");
      for (final Enumeration e = req.getParameterNames(); e.hasMoreElements();) {
        final String n = (String) e.nextElement();
        for (final String v : req.getParameterValues(n)) {
          System.out.println("  " + n + "=" + v);
        }
      }
      System.out.println();
    }

    final String mode = req.getParameter(Constants.OPENID_MODE);
    if (OMODE_CANCEL.equals(mode)) {
      // Provider wants us to cancel the attempt.
      //
      callback(req, rsp, SignInResult.CANCEL);
      return;
    } else if (GMODE_CHKCOOKIE.equals(mode)) {
      modeChkSetCookie(req, rsp, true);
      return;
    } else if (GMODE_SETCOOKIE.equals(mode)) {
      modeChkSetCookie(req, rsp, false);
      return;
    }

    final OpenIdUser user = relyingParty.discover(req);
    if (user == null) {
      // User isn't known, no provider is known.
      //
      callback(req, rsp, SignInResult.CANCEL);
      return;
    }

    if (user.isAssociated() && RelyingParty.isAuthResponse(req)) {
      if (!relyingParty.verifyAuth(user, req, rsp)) {
        // Failed verification... re-authenticate.
        //
        callback(req, rsp, SignInResult.CANCEL);
        return;
      }

      // Authentication was successful.
      //
      String fullname = req.getParameter("openid.sreg.fullname");
      String email = req.getParameter("openid.sreg.email");
      for (int i = 1;; i++) {
        final String nskey = "openid.ns.ext" + i;
        final String nsval = req.getParameter(nskey);
        if (nsval == null) {
          break;
        }

        final String ext = "openid.ext" + i + ".";
        if (AX_SCHEMA.equals(nsval)
            && "fetch_response".equals(req.getParameter(ext + "mode"))) {
          final String ax_fname = req.getParameter(ext + "value.fname");
          final String ax_email = req.getParameter(ext + "value.email");
          if (fullname == null && ax_fname != null) {
            fullname = ax_fname;
          }
          if (email == null && ax_email != null) {
            email = ax_email;
          }
          break;
        }
      }

      initializeAccount(req, rsp, user, fullname, email);
      return;
    }

    if (!relyingParty.associate(user, req, rsp)) {
      // Failed association. Try again.
      //
      callback(req, rsp, SignInResult.CANCEL);
      return;
    }

    // Authenticate user through his/her OpenID provider
    //
    final String realm = serverUrl(req);
    final StringBuilder retTo = new StringBuilder(req.getRequestURL());
    save(retTo, req, OpenIdUtil.P_SIGNIN_CB);
    save(retTo, req, OpenIdUtil.P_SIGNIN_MODE);
    save(retTo, req, OpenIdUtil.P_REMEMBERID);
    final StringBuilder auth;

    auth = RelyingParty.getAuthUrlBuffer(user, realm, realm, retTo.toString());
    append(auth, "openid.ns.ext1", AX_SCHEMA);
    final String ext1 = "openid.ext1.";
    append(auth, ext1 + "mode", "fetch_request");
    append(auth, ext1 + "type.fname", "http://example.com/schema/fullname");
    append(auth, ext1 + "type.email", "http://schema.openid.net/contact/email");
    append(auth, ext1 + "required", "email");
    append(auth, ext1 + "if_available", "fname");

    append(auth, "openid.sreg.optional", "fullname,email");
    append(auth, "openid.ns.sreg", "http://openid.net/extensions/sreg/1.1");

    sendJson(req, rsp, new DiscoveryResult(true, auth.toString()), req
        .getParameter(OpenIdUtil.P_DISCOVERY_CB));
  }

  private static void save(StringBuilder b, HttpServletRequest r, String n) {
    final String v = r.getParameter(n);
    if (v != null) {
      append(b, n, v);
    }
  }

  private void initializeAccount(final HttpServletRequest req,
      final HttpServletResponse rsp, final OpenIdUser user,
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
        getServletContext().log("Account lookup failed", e);
        account = null;
      }
    }

    rsp.reset();

    String tok;
    try {
      final String idstr = String.valueOf(account.getId().get());
      tok = server.getAccountToken().newToken(idstr);
    } catch (XsrfException e) {
      getServletContext().log("Account cookie signature impossible", e);
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
      callback(req, rsp, SignInResult.CANCEL);
    } else {
      c.setMaxAge(server.getSessionAge());
      rsp.addCookie(c);

      final StringBuilder me = new StringBuilder(req.getRequestURL());
      append(me, Constants.OPENID_MODE, GMODE_CHKCOOKIE);
      append(me, Gerrit.ACCOUNT_COOKIE, tok);
      save(me, req, OpenIdUtil.P_SIGNIN_CB);
      save(me, req, OpenIdUtil.P_SIGNIN_MODE);
      if ("on".equals(req.getParameter(OpenIdUtil.P_REMEMBERID))) {
        final String ident = saveLastId(req, rsp, user.getIdentity());
        append(me, OpenIdUtil.LASTID_COOKIE, ident);
        save(me, req, OpenIdUtil.P_REMEMBERID);
      } else {
        c = new Cookie(OpenIdUtil.LASTID_COOKIE, "");
        c.setPath(req.getContextPath() + "/");
        c.setMaxAge(0);
        rsp.addCookie(c);
      }
      rsp.sendRedirect(me.toString());
    }
  }

  private String saveLastId(final HttpServletRequest req,
      final HttpServletResponse rsp, String ident) {
    final Cookie c = new Cookie(OpenIdUtil.LASTID_COOKIE, ident);
    c.setPath(req.getContextPath() + "/");
    c.setMaxAge(LASTID_AGE);
    rsp.addCookie(c);
    return ident;
  }

  private Account openAccount(final ReviewDb db, final OpenIdUser user,
      final String fullname, final String email) throws OrmException {
    Account account;
    final AccountExternalIdAccess extAccess = db.accountExternalIds();
    AccountExternalId acctExt = lookup(extAccess, user.getIdentity());

    if (acctExt == null && email != null
        && server.isAllowGoogleAccountUpgrade() && isGoogleAccount(user)) {
      acctExt = lookupGoogleAccount(extAccess, email);
      if (acctExt != null) {
        // Legacy user from Gerrit 1? Attach the OpenID identity.
        //
        final AccountExternalId openidExt =
            new AccountExternalId(new AccountExternalId.Key(acctExt
                .getAccountId(), user.getIdentity()));
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
              .getIdentity()));
      acctExt.setLastUsedOn();
      acctExt.setEmailAddress(email);

      db.accounts().insert(Collections.singleton(account), txn);
      extAccess.insert(Collections.singleton(acctExt), txn);
      txn.commit();
    }
    return account;
  }

  private Account linkAccount(final HttpServletRequest req, final ReviewDb db,
      final OpenIdUser user, final String email) throws OrmException {
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
        new AccountExternalId.Key(account.getId(), user.getIdentity());
    AccountExternalId id = db.accountExternalIds().get(idKey);
    if (id == null) {
      id = new AccountExternalId(idKey);
      id.setLastUsedOn();
      id.setEmailAddress(email);
      db.accountExternalIds().insert(Collections.singleton(id));
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
    final String p = req.getParameter(OpenIdUtil.P_SIGNIN_MODE);
    if (p == null || p.length() == 0) {
      return SignInDialog.Mode.SIGN_IN;
    }
    try {
      return SignInDialog.Mode.valueOf(p);
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

  private static boolean isGoogleAccount(final OpenIdUser user) {
    return user.getIdentity().startsWith(OpenIdUtil.URL_GOOGLE + "?");
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
    // for this email using the generic Google URL.
    //
    final List<AccountExternalId> m = new ArrayList<AccountExternalId>();
    for (final AccountExternalId e : extAccess.byEmailAddress(email)) {
      if (e.getExternalId().equals(OpenIdUtil.URL_GOOGLE)) {
        m.add(e);
      }
    }
    return m.size() == 1 ? m.get(0) : null;
  }

  private void modeChkSetCookie(final HttpServletRequest req,
      final HttpServletResponse rsp, final boolean isCheck) throws IOException {
    final String exp = req.getParameter(Gerrit.ACCOUNT_COOKIE);
    final ValidToken chk;
    try {
      chk = server.getAccountToken().checkToken(exp, null);
    } catch (XsrfException e) {
      getServletContext().log("Cannot validate cookie token", e);
      callback(req, rsp, SignInResult.CANCEL);
      return;
    }

    final Account.Id id;
    try {
      id = new Account.Id(Integer.parseInt(chk.getData()));
    } catch (NumberFormatException e) {
      callback(req, rsp, SignInResult.CANCEL);
      return;
    }

    final String act = getCookie(req, Gerrit.ACCOUNT_COOKIE);
    if (isCheck && !exp.equals(act)) {
      // Cookie won't set without "user interaction" (thanks Safari). Lets
      // send an HTML page to the browser and ask the user to click to let
      // us set the cookie.
      //
      sendSetCookieHtml(req, rsp, exp);
      return;
    }

    Cookie c = new Cookie(Gerrit.ACCOUNT_COOKIE, exp);
    c.setPath(req.getContextPath() + "/");
    c.setMaxAge(server.getSessionAge());
    rsp.addCookie(c);

    if ("on".equals(req.getParameter(OpenIdUtil.P_REMEMBERID))) {
      saveLastId(req, rsp, req.getParameter(OpenIdUtil.LASTID_COOKIE));
    } else {
      c = new Cookie(OpenIdUtil.LASTID_COOKIE, "");
      c.setPath(req.getContextPath() + "/");
      c.setMaxAge(0);
      rsp.addCookie(c);
    }
    callback(req, rsp, new SignInResult(SignInResult.Status.SUCCESS));
  }

  private void sendSetCookieHtml(final HttpServletRequest req,
      final HttpServletResponse rsp, final String exp) throws IOException {
    final Document doc = HtmlDomUtil.clone(pleaseSetCookieDoc);
    final Element set_form = HtmlDomUtil.find(doc, "set_form");
    set_form.setAttribute("action", req.getRequestURL().toString());
    HtmlDomUtil.addHidden(set_form, Constants.OPENID_MODE, GMODE_SETCOOKIE);
    HtmlDomUtil.addHidden(set_form, Gerrit.ACCOUNT_COOKIE, exp);
    save(set_form, req, OpenIdUtil.LASTID_COOKIE);
    save(set_form, req, OpenIdUtil.P_REMEMBERID);
    save(set_form, req, OpenIdUtil.P_SIGNIN_CB);
    save(set_form, req, OpenIdUtil.P_SIGNIN_MODE);
    sendHtml(req, rsp, HtmlDomUtil.toString(doc));
  }

  private static void save(Element f, HttpServletRequest r, String n) {
    final String v = r.getParameter(n);
    if (v != null) {
      HtmlDomUtil.addHidden(f, n, v);
    }
  }

  private static String getCookie(final HttpServletRequest req,
      final String name) {
    final Cookie[] allCookies = req.getCookies();
    if (allCookies != null) {
      for (final Cookie c : allCookies) {
        if (name.equals(c.getName())) {
          return c.getValue();
        }
      }
    }
    return null;
  }

  private void callback(final HttpServletRequest req,
      final HttpServletResponse rsp, final SignInResult result)
      throws IOException {
    final String dcb = req.getParameter(OpenIdUtil.P_DISCOVERY_CB);
    if (dcb != null) {
      // We're in the middle of a discovery request; we need to use
      // the discovery request callback and not the sign in callback.
      //
      sendJson(req, rsp, new DiscoveryResult(false), dcb);
      return;
    }

    // We're not in an OpenID transaction so try to clear out the
    // OpenID management cookie.
    //
    relyingParty.getOpenIdUserManager().invalidate(rsp);

    final String cb = req.getParameter(OpenIdUtil.P_SIGNIN_CB);
    if (cb != null && cb.startsWith("history:")) {
      final StringBuffer rdr = req.getRequestURL();
      rdr.setLength(rdr.lastIndexOf("/"));
      rdr.append("/Gerrit");
      rdr.append('#');
      rdr.append(cb.substring("history:".length()));
      rsp.sendRedirect(rdr.toString());
      return;
    }
    sendJson(req, rsp, result, cb);
  }

  private void sendJson(final HttpServletRequest req,
      final HttpServletResponse rsp, final Object result, final String cb)
      throws IOException {
    final StringWriter body = new StringWriter();
    body.write("<html>");
    body.append("<body>");
    if (JsonServlet.SAFE_CALLBACK.matcher(cb).matches()) {
      body.write("<script><!--\n");
      body.write(cb);
      body.write("(");
      JsonServlet.defaultGsonBuilder().create().toJson(result, body);
      body.write(");\n");
      body.write("// -->\n");
      body.write("</script>");
      body.write("<p>Loading ...</p>");
    } else {
      body.append("Unsafe JSON callback requested; refusing to execute it.");
    }
    body.append("</body>");
    body.write("</html>");
    sendHtml(req, rsp, body.toString());
  }

  private void sendHtml(final HttpServletRequest req,
      final HttpServletResponse rsp, final String bodystr) throws IOException {
    final byte[] raw = bodystr.getBytes(HtmlDomUtil.ENC);
    final byte[] tosend;
    if (RPCServletUtils.acceptsGzipEncoding(req)) {
      rsp.setHeader("Content-Encoding", "gzip");
      final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
      final GZIPOutputStream gz = new GZIPOutputStream(compressed);
      gz.write(raw);
      gz.finish();
      gz.flush();
      tosend = compressed.toByteArray();
    } else {
      tosend = raw;
    }

    rsp.setCharacterEncoding(HtmlDomUtil.ENC);
    rsp.setContentType("text/html");
    rsp.setHeader("Expires", "Fri, 01 Jan 1980 00:00:00 GMT");
    rsp.setHeader("Pragma", "no-cache");
    rsp.setHeader("Cache-Control", "no-cache, must-revalidate");
    rsp.setContentLength(tosend.length);
    final OutputStream out = rsp.getOutputStream();
    try {
      out.write(tosend);
    } finally {
      out.close();
    }
  }

  static String serverUrl(final HttpServletRequest req) {
    // Assume this servlet is in the context with a simple name like "login"
    // and we were accessed without any path info. Clipping the last part of
    // the name from the URL should generate the web application's root path.
    //
    final String uri = req.getRequestURL().toString();
    final int s = uri.lastIndexOf('/');
    return s >= 0 ? uri.substring(0, s + 1) : uri;
  }

  private static void append(final StringBuilder buffer, final String name,
      final String value) {
    if (buffer.indexOf("?") >= 0) {
      buffer.append('&');
    } else {
      buffer.append('?');
    }
    buffer.append(name);
    buffer.append('=');
    buffer.append(UrlEncoded.encodeString(value));
  }
}
