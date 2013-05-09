// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.httpd;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * Authenticates the current user by HTTP digest authentication.
 * <p>
 * The current HTTP request is authenticated by looking up the username from the
 * Authorization header and checking the digest response against the stored
 * password. This filter is intended only to protect the {@link GitOverHttpServlet}
 * and its handled URLs, which provide remote repository access over HTTP.
 *
 * @see <a href="http://www.ietf.org/rfc/rfc2617.txt">RFC 2617</a>
 */
@Singleton
class ProjectDigestFilter implements Filter {
  public static final String REALM_NAME = "Gerrit Code Review";
  private static final String AUTHORIZATION = "Authorization";

  private final Provider<String> urlProvider;
  private final Provider<WebSession> session;
  private final AccountCache accountCache;
  private final Config config;
  private final SignedToken tokens;
  private ServletContext context;

  @Inject
  ProjectDigestFilter(@CanonicalWebUrl @Nullable Provider<String> urlProvider,
      Provider<WebSession> session, AccountCache accountCache,
      @GerritServerConfig Config config) throws XsrfException {
    this.urlProvider = urlProvider;
    this.session = session;
    this.accountCache = accountCache;
    this.config = config;
    this.tokens = new SignedToken((int) SECONDS.convert(1, HOURS));
  }

  @Override
  public void init(FilterConfig config) {
    context = config.getServletContext();
  }

  @Override
  public void destroy() {
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response,
      FilterChain chain) throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    Response rsp = new Response(req, (HttpServletResponse) response);

    if (verify(req, rsp)) {
      chain.doFilter(req, rsp);
    }
  }

  private boolean verify(HttpServletRequest req, Response rsp)
      throws IOException {
    final String hdr = req.getHeader(AUTHORIZATION);
    if (hdr == null || !hdr.startsWith("Digest ")) {
      // Allow an anonymous connection through, or it might be using a
      // session cookie instead of digest authentication.
      return true;
    }

    final Map<String, String> p = parseAuthorization(hdr);
    final String user = p.get("username");
    final String realm = p.get("realm");
    final String nonce = p.get("nonce");
    final String uri = p.get("uri");
    final String response = p.get("response");
    final String qop = p.get("qop");
    final String nc = p.get("nc");
    final String cnonce = p.get("cnonce");
    final String method = req.getMethod();

    if (user == null //
        || realm == null //
        || nonce == null //
        || uri == null //
        || response == null //
        || !"auth".equals(qop) //
        || !REALM_NAME.equals(realm)) {
      context.log("Invalid header: " + AUTHORIZATION + ": " + hdr);
      rsp.sendError(SC_FORBIDDEN);
      return false;
    }

    String username = user;
    if (config.getBoolean("auth", "userNameToLowerCase", false)) {
      username = username.toLowerCase(Locale.US);
    }

    final AccountState who = accountCache.getByUsername(username);
    if (who == null || ! who.getAccount().isActive()) {
      rsp.sendError(SC_UNAUTHORIZED);
      return false;
    }

    final String passwd = who.getPassword(username);
    if (passwd == null) {
      rsp.sendError(SC_UNAUTHORIZED);
      return false;
    }

    final String A1 = user + ":" + realm + ":" + passwd;
    final String A2 = method + ":" + uri;
    final String expect =
        KD(H(A1), nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + H(A2));

    if (expect.equals(response)) {
      try {
        if (tokens.checkToken(nonce, "") != null) {
          WebSession ws = session.get();
          ws.setUserAccountId(who.getAccount().getId());
          ws.setAccessPathOk(AccessPath.GIT, true);
          ws.setAccessPathOk(AccessPath.REST_API, true);
          return true;

        } else {
          rsp.stale = true;
          rsp.sendError(SC_UNAUTHORIZED);
          return false;
        }
      } catch (XsrfException e) {
        context.log("Error validating nonce for digest authentication", e);
        rsp.sendError(SC_INTERNAL_SERVER_ERROR);
        return false;
      }

    } else {
      rsp.sendError(SC_UNAUTHORIZED);
      return false;
    }
  }

  private static String H(String data) {
    try {
      MessageDigest md = newMD5();
      md.update(data.getBytes("UTF-8"));
      return LHEX(md.digest());
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("UTF-8 encoding not available", e);
    }
  }

  private static String KD(String secret, String data) {
    try {
      MessageDigest md = newMD5();
      md.update(secret.getBytes("UTF-8"));
      md.update((byte) ':');
      md.update(data.getBytes("UTF-8"));
      return LHEX(md.digest());
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("UTF-8 encoding not available", e);
    }
  }

  private static MessageDigest newMD5() {
    try {
      return MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("No MD5 available", e);
    }
  }

  private static final char[] LHEX =
      {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', //
          'a', 'b', 'c', 'd', 'e', 'f'};

  private static String LHEX(byte[] bin) {
    StringBuilder r = new StringBuilder(bin.length * 2);
    for (int i = 0; i < bin.length; i++) {
      byte b = bin[i];
      r.append(LHEX[(b >>> 4) & 0x0f]);
      r.append(LHEX[b & 0x0f]);
    }
    return r.toString();
  }

  private Map<String, String> parseAuthorization(String auth) {
    Map<String, String> p = new HashMap<String, String>();
    int next = "Digest ".length();
    while (next < auth.length()) {
      if (next < auth.length() && auth.charAt(next) == ',') {
        next++;
      }
      while (next < auth.length() && Character.isWhitespace(auth.charAt(next))) {
        next++;
      }

      int eq = auth.indexOf('=', next);
      if (eq < 0 || eq + 1 == auth.length()) {
        return Collections.emptyMap();
      }

      final String name = auth.substring(next, eq);
      final String value;
      if (auth.charAt(eq + 1) == '"') {
        int dq = auth.indexOf('"', eq + 2);
        if (dq < 0) {
          return Collections.emptyMap();
        }
        value = auth.substring(eq + 2, dq);
        next = dq + 1;

      } else {
        int space = auth.indexOf(' ', eq + 1);
        int comma = auth.indexOf(',', eq + 1);
        if (space < 0) space = auth.length();
        if (comma < 0) comma = auth.length();

        final int e = Math.min(space, comma);
        value = auth.substring(eq + 1, e);
        next = e + 1;
      }
      p.put(name, value);
    }
    return p;
  }

  private String newNonce() {
    try {
      return tokens.newToken("");
    } catch (XsrfException e) {
      throw new RuntimeException("Cannot generate new nonce", e);
    }
  }

  class Response extends HttpServletResponseWrapper {
    private static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    private final HttpServletRequest req;
    Boolean stale;

    Response(HttpServletRequest req, HttpServletResponse rsp) {
      super(rsp);
      this.req = req;
    }

    private void status(int sc) {
      if (sc == SC_UNAUTHORIZED) {
        StringBuilder v = new StringBuilder();
        v.append("Digest");
        v.append(" realm=\"" + REALM_NAME + "\"");

        String url = urlProvider.get();
        if (url == null) {
          url = req.getContextPath();
          if (url != null && !url.isEmpty() && !url.endsWith("/")) {
            url += "/";
          }
        }
        if (url != null && !url.isEmpty()) {
          v.append(", domain=\"" + url + "\"");
        }

        v.append(", qop=\"auth\"");
        if (stale != null) {
          v.append(", stale=" + stale);
        }
        v.append(", nonce=\"" + newNonce() + "\"");
        setHeader(WWW_AUTHENTICATE, v.toString());

      } else if (containsHeader(WWW_AUTHENTICATE)) {
        setHeader(WWW_AUTHENTICATE, null);
      }
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
      status(sc);
      super.sendError(sc, msg);
    }

    @Override
    public void sendError(int sc) throws IOException {
      status(sc);
      super.sendError(sc);
    }

    @Override
    public void setStatus(int sc) {
      status(sc);
      super.setStatus(sc);
    }
  }
}
