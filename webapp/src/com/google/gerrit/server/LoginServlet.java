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
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gwt.user.server.rpc.RPCServletUtils;
import com.google.gwtjsonrpc.server.JsonServlet;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;

import com.dyuproject.openid.Constants;
import com.dyuproject.openid.OpenIdContext;
import com.dyuproject.openid.OpenIdUser;
import com.dyuproject.openid.RelyingParty;

import org.mortbay.util.UrlEncoded;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.Collections;
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
  private static final String DEF_ENC = "UTF-8";
  private static final String CALLBACK_PARMETER = "callback";
  private static final String AX_SCHEMA = "http://openid.net/srv/ax/1.0";

  private GerritServer server;
  private RelyingParty relyingParty;

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
      finishLogin(req, rsp, null, null);
    }
  }

  private void doAuth(final HttpServletRequest req,
      final HttpServletResponse rsp) throws Exception {
    if ("cancel".equals(req.getParameter(Constants.OPENID_MODE))) {
      // Provider wants us to cancel the attempt.
      //
      finishLogin(req, rsp, null, null);
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
      finishLogin(req, rsp, user, null);
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

      finishLogin(req, rsp, user, email);
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
    final StringBuffer retTo = req.getRequestURL();
    retTo.append("?");
    appendCallbackParameter(req, retTo);
    final StringBuilder auth;

    auth = RelyingParty.getAuthUrlBuffer(user, realm, realm, retTo.toString());
    append(auth, "openid.ns.ext1", AX_SCHEMA);
    final String ext1 = "openid.ext1.";
    append(auth, ext1 + "mode", "fetch_request");
    append(auth, ext1 + "type.email", "http://schema.openid.net/contact/email");
    append(auth, ext1 + "required", "email");
    rsp.sendRedirect(auth.toString());
  }

  private static void appendCallbackParameter(final HttpServletRequest req,
      final StringBuffer url) {
    url.append(CALLBACK_PARMETER);
    url.append("=");
    url.append(UrlEncoded.encodeString(req.getParameter(CALLBACK_PARMETER)));
  }

  private void redirectChooseProvider(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    forceLogout(rsp);

    // Hard-code to use the Google Account service.
    //
    final StringBuffer url = req.getRequestURL();
    url.append("?");
    url.append(RelyingParty.DEFAULT_PARAMETER);
    url.append("=");
    url.append(UrlEncoded.encodeString(GoogleAccountDiscovery.GOOGLE_ACCOUNT));
    url.append("&");
    appendCallbackParameter(req, url);
    rsp.sendRedirect(url.toString());
  }

  private void finishLogin(final HttpServletRequest req,
      final HttpServletResponse rsp, final OpenIdUser user, final String email)
      throws IOException {
    Account account = null;
    if (user != null) {
      final Account.OpenId provId = new Account.OpenId(user.getIdentity());
      try {
        final ReviewDb d = server.getDatabase().open();
        try {
          account = d.accounts().byOpenId(provId);
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
            account = new Account(provId, new Account.Id(d.nextAccountId()));
            account.setPreferredEmail(email);
            d.accounts().insert(Collections.singleton(account));
          }
        } finally {
          d.close();
        }
      } catch (OrmException e) {
        getServletContext().log("Account lookup failed", e);
        account = null;
      }
    }

    rsp.setCharacterEncoding(DEF_ENC);
    rsp.setContentType("text/html");
    rsp.setHeader("Cache-Control", "no-cache");
    rsp.setDateHeader("Expires", System.currentTimeMillis());

    if (account != null) {
      try {
        final String idstr = String.valueOf(account.getId().get());
        final String tok = server.getAccountToken().newToken(idstr);
        final Cookie c = new Cookie(Gerrit.ACCOUNT_COOKIE, tok);
        c.setMaxAge(server.getSessionAge());
        c.setPath(req.getContextPath());
        rsp.addCookie(c);
      } catch (XsrfException e) {
        getServletContext().log("Account cookie signature impossible", e);
        account = null;
      }
    }

    if (account != null) {
      final Cookie c = new Cookie(Gerrit.OPENIDUSER_COOKIE, "");
      c.setMaxAge(0);
      rsp.addCookie(c);
    } else {
      forceLogout(rsp);
    }

    final StringWriter body = new StringWriter();
    body.write("<html>");
    body.write("<script><!--\n");
    body.write(req.getParameter(CALLBACK_PARMETER));
    body.write("(");
    if (account != null) {
      JsonServlet.defaultGsonBuilder().create().toJson(account, body);
    } else {
      body.write("null");
    }
    body.write(");\n");
    body.write("// -->\n");
    body.write("</script>");
    body.write("</html>");
    
    final byte[] raw = body.toString().getBytes(DEF_ENC);
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

    rsp.setContentLength(tosend.length);
    final OutputStream out = rsp.getOutputStream();
    try {
      out.write(tosend);
    } finally {
      out.close();
    }
  }

  public static void forceLogout(final HttpServletResponse rsp) {
    Cookie c;

    c = new Cookie(Gerrit.ACCOUNT_COOKIE, "");
    c.setMaxAge(0);
    rsp.addCookie(c);

    c = new Cookie(Gerrit.OPENIDUSER_COOKIE, "");
    c.setMaxAge(0);
    rsp.addCookie(c);
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
    buffer.append('&');
    buffer.append(name);
    buffer.append('=');
    buffer.append(UrlEncoded.encodeString(value));
  }
}
