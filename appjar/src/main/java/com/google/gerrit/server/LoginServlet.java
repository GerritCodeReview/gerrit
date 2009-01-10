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
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountExternalId;
import com.google.gerrit.client.reviewdb.AccountExternalIdAccess;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.google.gwt.user.server.rpc.RPCServletUtils;
import com.google.gwtjsonrpc.server.JsonServlet;
import com.google.gwtjsonrpc.server.ValidToken;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Transaction;

import com.dyuproject.openid.Constants;
import com.dyuproject.openid.OpenIdContext;
import com.dyuproject.openid.OpenIdUser;
import com.dyuproject.openid.RelyingParty;

import org.mortbay.util.UrlEncoded;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Handles the <code>/login</code> URL for web based single-sign-on. */
public class LoginServlet extends HttpServlet {
  private static final String SIGNIN_MODE_PARAMETER =
      SignInDialog.SIGNIN_MODE_PARAM;
  private static final String CALLBACK_PARMETER = "callback";
  private static final String AX_SCHEMA = "http://openid.net/srv/ax/1.0";
  private static final String GMODE_CHKCOOKIE = "gerrit_chkcookie";
  private static final String GMODE_SETCOOKIE = "gerrit_setcookie";

  private GerritServer server;
  private String canonicalUrl;
  private RelyingParty relyingParty;
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

    canonicalUrl = server.getCanonicalURL();
    String cookieKey = server.getAccountCookieKey();
    if (cookieKey.length() > 24) {
      cookieKey = cookieKey.substring(0, 24);
    }

    try {
      final int sessionAge = server.getSessionAge();
      final Properties p = new Properties();
      p.setProperty("openid.cookie.name", Gerrit.OPENIDUSER_COOKIE);
      p.setProperty("openid.cookie.security.secretKey", cookieKey);
      p.setProperty("openid.cookie.maxAge", String.valueOf(sessionAge));

      relyingParty = RelyingParty.newInstance(p);

      final OpenIdContext ctx = relyingParty.getOpenIdContext();
      ctx.setDiscovery(new GoogleAccountDiscovery(ctx.getDiscovery()));
    } catch (IOException e) {
      throw new ServletException("Cannot setup RelyingParty", e);
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
    if ("cancel".equals(mode)) {
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
    } else if ("gerrit.chooseProvider".equals(mode)) {
      chooseProvider(req, rsp);
      return;
    }

    final OpenIdUser user = relyingParty.discover(req);
    if (user == null) {
      // User isn't known, no provider is known.
      //
      chooseProvider(req, rsp);
      return;
    }

    if (user.isAuthenticated()) {
      // User already authenticated.
      //
      initializeAccount(req, rsp, user, null);
      return;
    }

    if (user.isAssociated() && RelyingParty.isAuthResponse(req)) {
      if (!relyingParty.verifyAuth(user, req, rsp)) {
        // Failed verification... re-authenticate.
        //
        chooseProvider(req, rsp);
        return;
      }

      // Authentication was successful.
      //
      String email = null;
      for (int i = 1;; i++) {
        final String nskey = "openid.ns.ext" + i;
        final String nsval = req.getParameter(nskey);
        if (nsval == null) {
          break;
        }

        final String ext = "openid.ext" + i + ".";
        if (AX_SCHEMA.equals(nsval)
            && "fetch_response".equals(req.getParameter(ext + "mode"))) {
          email = req.getParameter(ext + "value.email");
        }
      }

      initializeAccount(req, rsp, user, email);
      return;
    }

    if (!relyingParty.associate(user, req, rsp)) {
      // Failed association. Try again.
      //
      chooseProvider(req, rsp);
      return;
    }

    // Authenticate user through his/her OpenID provider
    //
    final String realm = serverUrl(req);
    final StringBuilder retTo = new StringBuilder(req.getRequestURL());
    append(retTo, CALLBACK_PARMETER, req.getParameter(CALLBACK_PARMETER));
    append(retTo, SIGNIN_MODE_PARAMETER, signInMode(req).name());
    final StringBuilder auth;

    auth = RelyingParty.getAuthUrlBuffer(user, realm, realm, retTo.toString());
    append(auth, "openid.ns.ext1", AX_SCHEMA);
    final String ext1 = "openid.ext1.";
    append(auth, ext1 + "mode", "fetch_request");
    append(auth, ext1 + "type.email", "http://schema.openid.net/contact/email");
    append(auth, ext1 + "required", "email");
    rsp.sendRedirect(auth.toString());
  }

  private void chooseProvider(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    Cookie c;
    c = new Cookie(Gerrit.OPENIDUSER_COOKIE, "");
    c.setMaxAge(0);
    rsp.addCookie(c);

    if (canonicalUrl != null && !canonicalUrl.equals(serverUrl(req))) {
      // Try to make the entry URL match what we expect it to be, in
      // case the OpenID provider relies on this to generate tokens
      // (some do, like Google Accounts).
      //
      rsp.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
      rsp.setHeader("Location", canonicalUrl + "login");
      return;
    }

    // TODO Should be in init so we can cache.
    final Document loginDoc;
    try {
      final String loginPageName = "com/google/gerrit/public/Login.html";
      loginDoc = HtmlDomUtil.parseFile(loginPageName);
      if (loginDoc == null) {
        throw new ServletException("No " + loginPageName + " in CLASSPATH");
      }
    } catch (ServletException e) {
      throw new IOException("bad");
    }

    final Document doc = HtmlDomUtil.clone(loginDoc);

    final Element scriptNode = HtmlDomUtil.find(doc, "gerrit_oldLoginData");
    final StringWriter w = new StringWriter();
    w.write("<!--\n");
    w.write("var gerrit_openid_identifier=");
    final String idUrl = req.getParameter(RelyingParty.DEFAULT_PARAMETER);
    GerritJsonServlet.defaultGsonBuilder().create().toJson(
        idUrl != null ? new JsonPrimitive(idUrl) : new JsonNull(), w);
    w.write(";\n// -->\n");
    scriptNode.removeAttribute("id");
    scriptNode.setAttribute("type", "text/javascript");
    scriptNode.setAttribute("language", "javascript");
    scriptNode.appendChild(doc.createCDATASection(w.toString()));

    final Element set_form = HtmlDomUtil.find(doc, "login_form");
    set_form.setAttribute("action", req.getRequestURL().toString());

    final Element in_callback = HtmlDomUtil.find(doc, "in_callback");
    in_callback.setAttribute("name", CALLBACK_PARMETER);
    in_callback.setAttribute("value", req.getParameter(CALLBACK_PARMETER));

    final Element in_token = HtmlDomUtil.find(doc, "in_token");
    final String token = req.getParameter("gerrit.token");
    in_token.setAttribute("value", token != null ? token : "");

    HtmlDomUtil.addHidden(set_form, SIGNIN_MODE_PARAMETER, signInMode(req)
        .name());
    sendHtml(req, rsp, HtmlDomUtil.toString(doc));
  }

  private void initializeAccount(final HttpServletRequest req,
      final HttpServletResponse rsp, final OpenIdUser user, final String email)
      throws IOException {
    final SignInDialog.Mode mode = signInMode(req);
    Account account = null;
    if (user != null) {
      try {
        final ReviewDb d = Common.getSchemaFactory().open();
        try {
          switch (mode) {
            case SIGN_IN:
              account = openAccount(d, user, email);
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

    Cookie c;
    c = new Cookie(Gerrit.OPENIDUSER_COOKIE, "");
    c.setMaxAge(0);
    rsp.addCookie(c);

    String tok;
    try {
      final String idstr = String.valueOf(account.getId().get());
      tok = server.getAccountToken().newToken(idstr);
    } catch (XsrfException e) {
      getServletContext().log("Account cookie signature impossible", e);
      account = null;
      tok = "";
    }

    c = new Cookie(Gerrit.ACCOUNT_COOKIE, tok);
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
      append(me, CALLBACK_PARMETER, req.getParameter(CALLBACK_PARMETER));
      append(me, Gerrit.ACCOUNT_COOKIE, tok);
      append(me, SIGNIN_MODE_PARAMETER, mode.name());
      rsp.sendRedirect(me.toString());
    }
  }

  private Account openAccount(final ReviewDb db, final OpenIdUser user,
      final String email) throws OrmException {
    Account account;
    final AccountExternalIdAccess extAccess = db.accountExternalIds();
    AccountExternalId acctExt = lookup(extAccess, user.getIdentity());

    if (acctExt == null && email != null && isGoogleAccount(user)) {
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
    final String p = req.getParameter(SIGNIN_MODE_PARAMETER);
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
    return user.getIdentity().startsWith(GoogleAccountDiscovery.GOOGLE_ACCOUNT);
  }

  private static boolean isGoogleAccount(final AccountExternalId user) {
    return user.getExternalId().startsWith(
        GoogleAccountDiscovery.GOOGLE_ACCOUNT);
  }

  private static AccountExternalId lookupGoogleAccount(
      final AccountExternalIdAccess extAccess, final String email)
      throws OrmException {
    for (final AccountExternalId e : extAccess.byEmailAddress(email)) {
      if (isGoogleAccount(e)) {
        return e;
      }
    }
    return null;
  }

  private void modeChkSetCookie(final HttpServletRequest req,
      final HttpServletResponse rsp, final boolean isCheck) throws IOException {
    final String exp = req.getParameter(Gerrit.ACCOUNT_COOKIE);
    final ValidToken chk;
    try {
      chk = server.getAccountToken().checkToken(exp, null);
    } catch (XsrfException e) {
      getServletContext().log("Cannot validate cookie token", e);
      chooseProvider(req, rsp);
      return;
    }

    final Account.Id id;
    try {
      id = new Account.Id(Integer.parseInt(chk.getData()));
    } catch (NumberFormatException e) {
      chooseProvider(req, rsp);
      return;
    }

    final Account account = Common.getAccountCache().get(id);
    if (account == null) {
      chooseProvider(req, rsp);
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

    final Cookie c = new Cookie(Gerrit.ACCOUNT_COOKIE, exp);
    c.setPath(req.getContextPath() + "/");
    c.setMaxAge(server.getSessionAge());
    rsp.addCookie(c);
    callback(req, rsp, new SignInResult(SignInResult.Status.SUCCESS, account));
  }

  private void sendSetCookieHtml(final HttpServletRequest req,
      final HttpServletResponse rsp, final String exp) throws IOException {
    final Document doc = HtmlDomUtil.clone(pleaseSetCookieDoc);
    final Element set_form = HtmlDomUtil.find(doc, "set_form");
    set_form.setAttribute("action", req.getRequestURL().toString());
    HtmlDomUtil.addHidden(set_form, Constants.OPENID_MODE, GMODE_SETCOOKIE);
    HtmlDomUtil.addHidden(set_form, Gerrit.ACCOUNT_COOKIE, exp);
    HtmlDomUtil.addHidden(set_form, CALLBACK_PARMETER, req
        .getParameter(CALLBACK_PARMETER));
    HtmlDomUtil.addHidden(set_form, SIGNIN_MODE_PARAMETER, signInMode(req)
        .name());
    sendHtml(req, rsp, HtmlDomUtil.toString(doc));
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
    final String cb = req.getParameter(CALLBACK_PARMETER);
    if (cb != null && cb.startsWith("history:")) {
      final StringBuffer rdr = req.getRequestURL();
      rdr.setLength(rdr.lastIndexOf("/"));
      rdr.append("/Gerrit");
      rdr.append('#');
      rdr.append(cb.substring("history:".length()));
      rsp.sendRedirect(rdr.toString());
      return;
    }

    final StringWriter body = new StringWriter();
    body.write("<html>");
    if (JsonServlet.SAFE_CALLBACK.matcher(cb).matches()) {
      body.write("<script><!--\n");
      body.write(cb);
      body.write("(");
      JsonServlet.defaultGsonBuilder().create().toJson(result, body);
      body.write(");\n");
      body.write("// -->\n");
      body.write("</script>");
    } else {
      body.append("<body>");
      body.append("Unsafe JSON callback requested; refusing to execute it.");
      body.append("</body>");
    }
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
