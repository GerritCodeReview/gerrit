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
import com.google.gerrit.client.account.SignInResult;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountExternalId;
import com.google.gerrit.client.reviewdb.AccountExternalIdAccess;
import com.google.gerrit.client.reviewdb.ReviewDb;
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
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Handles the <code>/login</code> URL for web based single-sign-on. */
public class LoginServlet extends HttpServlet {
  private static final String CALLBACK_PARMETER = "callback";
  private static final Pattern SAFE_CALLBACK =
      Pattern.compile("^(parent\\.)?__gwtjsonrpc_callback[0-9]+$");

  private static final String AX_SCHEMA = "http://openid.net/srv/ax/1.0";
  private static final String GMODE_CHKCOOKIE = "gerrit_chkcookie";
  private static final String GMODE_SETCOOKIE = "gerrit_setcookie";

  private GerritServer server;
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
    }

    final OpenIdUser user = relyingParty.discover(req);
    if (user == null) {
      // User isn't known, no provider is known.
      //
      redirectChooseProvider(req, rsp);
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
        redirectChooseProvider(req, rsp);
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
      redirectChooseProvider(req, rsp);
      return;
    }

    // Authenticate user through his/her OpenID provider
    //
    final String realm = serverUrl(req);
    final StringBuilder retTo = new StringBuilder(req.getRequestURL());
    append(retTo, CALLBACK_PARMETER, req.getParameter(CALLBACK_PARMETER));
    final StringBuilder auth;

    auth = RelyingParty.getAuthUrlBuffer(user, realm, realm, retTo.toString());
    append(auth, "openid.ns.ext1", AX_SCHEMA);
    final String ext1 = "openid.ext1.";
    append(auth, ext1 + "mode", "fetch_request");
    append(auth, ext1 + "type.email", "http://schema.openid.net/contact/email");
    append(auth, ext1 + "required", "email");
    rsp.sendRedirect(auth.toString());
  }

  private void redirectChooseProvider(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    // Hard-code to use the Google Account service.
    //
    final StringBuilder url = new StringBuilder(req.getRequestURL());
    append(url, CALLBACK_PARMETER, req.getParameter(CALLBACK_PARMETER));
    append(url, RelyingParty.DEFAULT_PARAMETER,
        GoogleAccountDiscovery.GOOGLE_ACCOUNT);
    rsp.sendRedirect(url.toString());
  }

  private void initializeAccount(final HttpServletRequest req,
      final HttpServletResponse rsp, final OpenIdUser user, final String email)
      throws IOException {
    Account account = null;
    if (user != null) {
      try {
        final ReviewDb d = server.getDatabase().open();
        try {
          final AccountExternalIdAccess extAccess = d.accountExternalIds();
          AccountExternalId acctExt = lookup(extAccess, user.getIdentity());

          if (acctExt == null && email != null && isGoogleAccount(user)) {
            acctExt = lookup(extAccess, "GoogleAccount/" + email);
            if (acctExt != null) {
              // Legacy user from Gerrit 1? Upgrade their account.
              //
              final Transaction txn = d.beginTransaction();
              final AccountExternalId openidExt =
                  new AccountExternalId(new AccountExternalId.Key(acctExt
                      .getAccountId(), user.getIdentity()));
              extAccess.insert(Collections.singleton(openidExt), txn);
              extAccess.delete(Collections.singleton(acctExt), txn);
              txn.commit();
              acctExt = openidExt;
            }
          }

          if (acctExt != null) {
            account = d.accounts().byId(acctExt.getAccountId());
          } else {
            account = null;
          }

          if (account != null) {
            // Existing user; double check the email is current.
            //
            if (email != null && !email.equals(account.getPreferredEmail())) {
              account.setPreferredEmail(email);
              d.accounts().update(Collections.singleton(account));
            }
          } else {
            // New user; create an account entity for them.
            //
            final Transaction txn = d.beginTransaction();

            account = new Account(new Account.Id(d.nextAccountId()));
            account.setPreferredEmail(email);

            acctExt =
                new AccountExternalId(new AccountExternalId.Key(
                    account.getId(), user.getIdentity()));

            d.accounts().insert(Collections.singleton(account), txn);
            extAccess.insert(Collections.singleton(acctExt), txn);
            txn.commit();
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
      c.setMaxAge(0);
      rsp.addCookie(c);
      callback(req, rsp, SignInResult.CANCEL);
    } else {
      c.setMaxAge(server.getSessionAge());
      rsp.addCookie(c);

      final StringBuilder me = new StringBuilder(req.getRequestURL());
      append(me, Constants.OPENID_MODE, GMODE_CHKCOOKIE);
      append(me, CALLBACK_PARMETER, req.getParameter(CALLBACK_PARMETER));
      append(me, Gerrit.ACCOUNT_COOKIE, tok);
      rsp.sendRedirect(me.toString());
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

  private void modeChkSetCookie(final HttpServletRequest req,
      final HttpServletResponse rsp, final boolean isCheck) throws IOException {
    final String exp = req.getParameter(Gerrit.ACCOUNT_COOKIE);
    final ValidToken chk;
    try {
      chk = server.getAccountToken().checkToken(exp, null);
    } catch (XsrfException e) {
      getServletContext().log("Cannot validate cookie token", e);
      redirectChooseProvider(req, rsp);
      return;
    }

    final Account.Id id;
    try {
      id = new Account.Id(Integer.parseInt(chk.getData()));
    } catch (NumberFormatException e) {
      redirectChooseProvider(req, rsp);
      return;
    }

    Account account;
    try {
      final ReviewDb db = server.getDatabase().open();
      try {
        account = db.accounts().byId(id);
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      getServletContext().log("Account lookup failed for " + id, e);
      account = null;
    }
    if (account == null) {
      redirectChooseProvider(req, rsp);
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
    final StringWriter body = new StringWriter();
    body.write("<html>");
    if (SAFE_CALLBACK.matcher(cb).matches()) {
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
    rsp.setHeader("Cache-Control", "no-cache");
    rsp.setDateHeader("Expires", System.currentTimeMillis());
    rsp.setContentLength(tosend.length);
    final OutputStream out = rsp.getOutputStream();
    try {
      out.write(tosend);
    } finally {
      out.close();
    }
  }

  private static String serverUrl(final HttpServletRequest req) {
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
