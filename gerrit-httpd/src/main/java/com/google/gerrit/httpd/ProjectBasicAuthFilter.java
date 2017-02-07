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

package com.google.gerrit.httpd;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.client.GitBasicAuthPolicy;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.AuthResult;
import com.google.gerrit.server.account.AuthenticationFailedException;
import com.google.gerrit.server.auth.NoSuchUserException;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Locale;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authenticates the current user by HTTP basic authentication.
 *
 * <p>The current HTTP request is authenticated by looking up the username and password from the
 * Base64 encoded Authorization header and validating them against any username/password configured
 * authentication system in Gerrit. This filter is intended only to protect the {@link
 * GitOverHttpServlet} and its handled URLs, which provide remote repository access over HTTP.
 *
 * @see <a href="http://www.ietf.org/rfc/rfc2617.txt">RFC 2617</a>
 */
@Singleton
class ProjectBasicAuthFilter implements Filter {
  private static final Logger log = LoggerFactory.getLogger(ProjectBasicAuthFilter.class);

  public static final String REALM_NAME = "Gerrit Code Review";
  private static final String AUTHORIZATION = "Authorization";
  private static final String LIT_BASIC = "Basic ";

  private final DynamicItem<WebSession> session;
  private final AccountCache accountCache;
  private final AccountManager accountManager;
  private final AuthConfig authConfig;

  @Inject
  ProjectBasicAuthFilter(
      DynamicItem<WebSession> session,
      AccountCache accountCache,
      AccountManager accountManager,
      AuthConfig authConfig) {
    this.session = session;
    this.accountCache = accountCache;
    this.accountManager = accountManager;
    this.authConfig = authConfig;
  }

  @Override
  public void init(FilterConfig config) {}

  @Override
  public void destroy() {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    Response rsp = new Response((HttpServletResponse) response);

    if (verify(req, rsp)) {
      chain.doFilter(req, rsp);
    }
  }

  private boolean verify(HttpServletRequest req, Response rsp) throws IOException {
    final String hdr = req.getHeader(AUTHORIZATION);
    if (hdr == null || !hdr.startsWith(LIT_BASIC)) {
      // Allow an anonymous connection through, or it might be using a
      // session cookie instead of basic authentication.
      return true;
    }

    final byte[] decoded = Base64.decodeBase64(hdr.substring(LIT_BASIC.length()));
    String usernamePassword = new String(decoded, encoding(req));
    int splitPos = usernamePassword.indexOf(':');
    if (splitPos < 1) {
      rsp.sendError(SC_UNAUTHORIZED);
      return false;
    }

    String username = usernamePassword.substring(0, splitPos);
    String password = usernamePassword.substring(splitPos + 1);
    if (Strings.isNullOrEmpty(password)) {
      rsp.sendError(SC_UNAUTHORIZED);
      return false;
    }
    if (authConfig.isUserNameToLowerCase()) {
      username = username.toLowerCase(Locale.US);
    }

    final AccountState who = accountCache.getByUsername(username);
    if (who == null || !who.getAccount().isActive()) {
      log.warn(
          "Authentication failed for "
              + username
              + ": account inactive or not provisioned in Gerrit");
      rsp.sendError(SC_UNAUTHORIZED);
      return false;
    }

    GitBasicAuthPolicy gitBasicAuthPolicy = authConfig.getGitBasicAuthPolicy();
    if (gitBasicAuthPolicy == GitBasicAuthPolicy.HTTP
        || gitBasicAuthPolicy == GitBasicAuthPolicy.HTTP_LDAP) {
      if (passwordMatchesTheUserGeneratedOne(who, username, password)) {
        return succeedAuthentication(who);
      }
    }

    if (gitBasicAuthPolicy == GitBasicAuthPolicy.HTTP) {
      return failAuthentication(rsp, username);
    }

    AuthRequest whoAuth = AuthRequest.forUser(username);
    whoAuth.setPassword(password);

    try {
      AuthResult whoAuthResult = accountManager.authenticate(whoAuth);
      setUserIdentified(whoAuthResult.getAccountId());
      return true;
    } catch (NoSuchUserException e) {
      if (password.equals(who.getPassword(who.getUserName()))) {
        return succeedAuthentication(who);
      }
      log.warn("Authentication failed for " + username, e);
      rsp.sendError(SC_UNAUTHORIZED);
      return false;
    } catch (AuthenticationFailedException e) {
      log.warn("Authentication failed for " + username + ": " + e.getMessage());
      rsp.sendError(SC_UNAUTHORIZED);
      return false;
    } catch (AccountException e) {
      log.warn("Authentication failed for " + username, e);
      rsp.sendError(SC_UNAUTHORIZED);
      return false;
    }
  }

  private boolean succeedAuthentication(final AccountState who) {
    setUserIdentified(who.getAccount().getId());
    return true;
  }

  private boolean failAuthentication(Response rsp, String username) throws IOException {
    log.warn(
        "Authentication failed for {}: password does not match the one" + " stored in Gerrit",
        username);
    rsp.sendError(SC_UNAUTHORIZED);
    return false;
  }

  private void setUserIdentified(Account.Id id) {
    WebSession ws = session.get();
    ws.setUserAccountId(id);
    ws.setAccessPathOk(AccessPath.GIT, true);
    ws.setAccessPathOk(AccessPath.REST_API, true);
  }

  private boolean passwordMatchesTheUserGeneratedOne(
      AccountState who, String username, String password) {
    String accountPassword = who.getPassword(username);
    return accountPassword != null && password != null && accountPassword.equals(password);
  }

  private String encoding(HttpServletRequest req) {
    return MoreObjects.firstNonNull(req.getCharacterEncoding(), UTF_8.name());
  }

  static class Response extends HttpServletResponseWrapper {
    private static final String WWW_AUTHENTICATE = "WWW-Authenticate";

    Response(HttpServletResponse rsp) {
      super(rsp);
    }

    private void status(int sc) {
      if (sc == SC_UNAUTHORIZED) {
        StringBuilder v = new StringBuilder();
        v.append(LIT_BASIC);
        v.append("realm=\"").append(REALM_NAME).append("\"");
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
    @Deprecated
    public void setStatus(int sc, String sm) {
      status(sc);
      super.setStatus(sc, sm);
    }

    @Override
    public void setStatus(int sc) {
      status(sc);
      super.setStatus(sc);
    }
  }
}
