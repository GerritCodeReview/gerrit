// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.httpd.auth;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.common.net.HttpHeaders.WWW_AUTHENTICATE;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.gerrit.httpd.GitOverHttpServlet;
import com.google.gerrit.server.auth.AuthException;
import com.google.gerrit.server.auth.Credentials;
import com.google.gerrit.server.auth.InvalidCredentialsException;
import com.google.gerrit.server.auth.PasswordCredentials;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Authenticates the current user by HTTP digest authentication.
 *
 * The current HTTP request is authenticated by looking up the username from the
 * Authorization header and checking the digest response against the stored
 * password. This filter is intended only to protect the {@link GitOverHttpServlet}
 * and its handled URLs, which provide remote repository access over HTTP.
 *
 * @see <a href="http://www.ietf.org/rfc/rfc2617.txt">RFC 2617</a>
 */
@Singleton
public class DigestHttpAuthProtocol
    implements HttpAuthProtocol.CredentialsExtractor<PasswordCredentials>,
      HttpAuthProtocol.ProgrammaticLoginHandler {
  public static final String QOP_VALUE = "auth";
  public static final String REALM_NAME = "Gerrit Code Review";
  private static final String DIGEST_AUTH_PREFIX = "Digest ";
  private static final BaseEncoding LHEX = BaseEncoding.base16().lowerCase();

  private final Provider<String> urlProvider;
  private final SignedToken tokens;

  @Inject
  DigestHttpAuthProtocol(
      @CanonicalWebUrl @Nullable Provider<String> urlProvider)
      throws XsrfException {
    this.urlProvider = urlProvider;
    this.tokens = new SignedToken((int) SECONDS.convert(1, HOURS));
  }

  @Override
  public PasswordCredentials extractCredentials(HttpServletRequest req)
      throws AuthProtocolException {
    final String hdr = req.getHeader(AUTHORIZATION);
    if (hdr == null || !hdr.startsWith(DIGEST_AUTH_PREFIX)) {
      return null;
    }

    final Map<String, String> p = parseAuthorization(hdr);
    final String user = p.get("username");
    final String nonce = p.get("nonce");
    final String uri = p.get("uri");
    final String response = p.get("response");
    final String nc = p.get("nc");
    final String cnonce = p.get("cnonce");
    if (user == null //
        || nonce == null //
        || uri == null //
        || response == null //
        || !QOP_VALUE.equals(p.get("qop")) //
        || !REALM_NAME.equals(p.get("realm"))) {
      throw new AuthProtocolException("Invalid header: " + AUTHORIZATION + ": " + hdr);
    }

    final String A2 = req.getMethod() + ":" + uri;
    return new DigestCredentials(user, response) {
      @Override
      public void checkPassword(String password) throws AuthException {
        final String A1 = getUsername() + ":" + REALM_NAME + ":" + password;
        final String expect =
            KD(H(A1), nonce + ":" + nc + ":" + cnonce + ":" + QOP_VALUE + ":" + H(A2));

        if (!expect.equals(getPassword())) {
          throw new InvalidCredentialsException();
        }

        try {
          if (tokens.checkToken(nonce, "") == null) {
            stale = true;
            throw new InvalidCredentialsException("nonce is stale");
          }
        } catch (XsrfException e) {
          throw new InvalidCredentialsException("Error validating nonce for digest authentication", e);
        }
      }
    };
  }

  @Override
  public void loginProgrammatic(HttpServletRequest req,
      HttpServletResponse resp, @Nullable Credentials creds)
      throws IOException {
    StringBuilder v = new StringBuilder();
    v.append(DIGEST_AUTH_PREFIX + "realm=\"" + REALM_NAME + "\"");

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

    v.append(", qop=\"" + QOP_VALUE + "\"");
    if (creds instanceof DigestCredentials) {
      v.append(", stale=" + ((DigestCredentials) creds).stale);
    }
    v.append(", nonce=\"" + newNonce() + "\"");
    resp.setHeader(WWW_AUTHENTICATE, v.toString());
  }

  private static String H(String data) {
    return LHEX.encode(Hashing.md5()
        .hashString(data, Charsets.UTF_8)
        .asBytes());
  }

  private static String KD(String secret, String data) {
    return LHEX.encode(Hashing.md5()
        .newHasher(secret.length() + data.length() + 1)
        .putString(secret, Charsets.UTF_8)
        .putByte((byte) ':')
        .putString(data, Charsets.UTF_8)
        .hash()
        .asBytes());
  }

  private Map<String, String> parseAuthorization(String auth) {
    Map<String, String> p = new HashMap<String, String>();
    int next = DIGEST_AUTH_PREFIX.length();
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

  private String newNonce() throws IOException {
    try {
      return tokens.newToken("");
    } catch (XsrfException e) {
      throw new IOException("Cannot generate new nonce", e);
    }
  }

  private static class DigestCredentials extends PasswordCredentials {
    boolean stale;

    DigestCredentials(String username, String password) {
      super(username, password);
    }
  }
}
