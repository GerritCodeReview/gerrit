// Copyright (C) 2015 The Android Open Source Project
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
import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.auth.oauth.OAuthLoginProvider;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicMap.Entry;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.AuthResult;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.commons.codec.binary.Base64;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Locale;
import java.util.NoSuchElementException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * Authenticates the current user with an OAuth2 server.
 *
 * @see <a href="https://tools.ietf.org/rfc/rfc6750.txt">RFC 6750</a>
 */
@Singleton
class ProjectOAuthFilter implements Filter {

  private static final Logger log = LoggerFactory
      .getLogger(ProjectOAuthFilter.class);

  private static final String REALM_NAME = "Gerrit Code Review";
  private static final String AUTHORIZATION = "Authorization";
  private static final String BASIC = "Basic ";
  private static final String GIT_COOKIE_PREFIX = "git-";

  private final DynamicItem<WebSession> session;
  private final DynamicMap<OAuthLoginProvider> loginProviders;
  private final AccountCache accountCache;
  private final AccountManager accountManager;
  private final String gitOAuthProvider;
  private final boolean userNameToLowerCase;

  private String defaultAuthPlugin;
  private String defaultAuthProvider;

  @Inject
  ProjectOAuthFilter(DynamicItem<WebSession> session,
      DynamicMap<OAuthLoginProvider> pluginsProvider,
      AccountCache accountCache,
      AccountManager accountManager,
      @GerritServerConfig Config gerritConfig) {
    this.session = session;
    this.loginProviders = pluginsProvider;
    this.accountCache = accountCache;
    this.accountManager = accountManager;
    this.gitOAuthProvider =
        gerritConfig.getString("auth", null, "gitOAuthProvider");
    this.userNameToLowerCase =
        gerritConfig.getBoolean("auth", null, "userNameToLowerCase", false);
  }

  @Override
  public void init(FilterConfig config) throws ServletException {
    if (Strings.isNullOrEmpty(gitOAuthProvider)) {
      pickOnlyProvider();
    } else {
      pickConfiguredProvider();
    }
  }

  @Override
  public void destroy() {
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response,
      FilterChain chain) throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    Response rsp = new Response((HttpServletResponse) response);
    if (verify(req, rsp)) {
      chain.doFilter(req, rsp);
    }
  }

  private boolean verify(HttpServletRequest req, Response rsp)
      throws IOException {
    AuthInfo authInfo = null;

    // first check if there is a Git cookie; such cookies are expected
    // to have names of the form "git-providerId-username", where providerId
    // matches one of the installed OAuth providers
    if (req.getCookies() != null) {
      for (Cookie cookie: req.getCookies()) {
        if (cookie.getName().startsWith(GIT_COOKIE_PREFIX)) {
          authInfo = extractAuthInfo(cookie);
          if (authInfo == null) {
            // Git cookie does not match installed provider
            rsp.sendError(SC_UNAUTHORIZED);
            return false;
          }
          break;
        }
      }
    }

    // if there is no Git cookie fall back to the "Authorization" header
    // and the default OAuth provider, if any
    if (authInfo == null) {
      String hdr = req.getHeader(AUTHORIZATION);
      if (hdr == null || !hdr.startsWith(BASIC)) {
        // Allow an anonymous connection through, or it might be using a
        // session cookie instead of basic authentication.
        return true;
      }

      byte[] decoded = Base64.decodeBase64(hdr.substring(BASIC.length()));
      String usernamePassword = new String(decoded, encoding(req));
      int splitPos = usernamePassword.indexOf(':');
      if (splitPos < 1) {
        rsp.sendError(SC_UNAUTHORIZED);
        return false;
      }

      authInfo = new AuthInfo(usernamePassword.substring(0, splitPos),
          usernamePassword.substring(splitPos + 1),
          defaultAuthPlugin, defaultAuthProvider);
    }

    if (Strings.isNullOrEmpty(authInfo.getTokenOrSecret())) {
      rsp.sendError(SC_UNAUTHORIZED);
      return false;
    }

    AccountState who = accountCache.getByUsername(authInfo.getUsername());
    if (who == null || !who.getAccount().isActive()) {
      log.warn("Authentication failed for " + authInfo.getUsername()
          + ": account inactive or not provisioned in Gerrit");
      rsp.sendError(SC_UNAUTHORIZED);
      return false;
    }

    AuthRequest authRequest = AuthRequest.forExternalUser(
        authInfo.getUsername());
    authRequest.setEmailAddress(who.getAccount().getPreferredEmail());
    authRequest.setDisplayName(who.getAccount().getFullName());
    authRequest.setPassword(authInfo.getTokenOrSecret());
    authRequest.setAuthPlugin(authInfo.getPluginName());
    authRequest.setAuthProvider(authInfo.getExportName());

    try {
      AuthResult authResult = accountManager.authenticate(authRequest);
      WebSession ws = session.get();
      ws.setUserAccountId(authResult.getAccountId());
      ws.setAccessPathOk(AccessPath.GIT, true);
      ws.setAccessPathOk(AccessPath.REST_API, true);
      return true;
    } catch (AccountException e) {
      log.warn("Authentication failed for " + authInfo.getUsername(), e);
      rsp.sendError(SC_UNAUTHORIZED);
      return false;
    }
  }

  /**
   * Picks the only installed OAuth provider. If there is a multiude
   * of providers available, the actual provider must be determined
   * from the authentication request.
   *
   * @throws ServletException if there is no {@code OAuthLoginProvider}
   * installed at all.
   */
  private void pickOnlyProvider() throws ServletException {
    try {
      Entry<OAuthLoginProvider> loginProvider =
          Iterables.getOnlyElement(loginProviders);
      defaultAuthPlugin = loginProvider.getPluginName();
      defaultAuthProvider = loginProvider.getExportName();
    } catch (NoSuchElementException e) {
      throw new ServletException("No OAuth login provider installed");
    } catch (IllegalArgumentException e) {
      // multiple providers found => do not pick any
    }
  }

  /**
   * Picks the {@code OAuthLoginProvider} configured with
   * <tt>auth.gitOAuthProvider</tt>.
   *
   * @throws ServletException if the configured provider was not found.
   */
  private void pickConfiguredProvider() throws ServletException {
    Entry<OAuthLoginProvider> provider = findProvider(gitOAuthProvider);
    if (provider == null) {
      throw new ServletException("Configured OAuth login provider "
          + gitOAuthProvider + " wasn't installed");
    }
    defaultAuthPlugin = provider.getPluginName();
    defaultAuthProvider = provider.getExportName();
  }

  private Entry<OAuthLoginProvider> findProvider(String providerId) {
    for (Entry<OAuthLoginProvider> e: loginProviders) {
      if (providerId.equals(e.getProvider().get().getProviderId())) {
        return e;
      }
    }
    return null;
  }

  private AuthInfo extractAuthInfo(Cookie cookie) {
    String username = cookie.getName().substring(GIT_COOKIE_PREFIX.length());
    int splitPos = username.indexOf('-');
    if (splitPos < 0) {
      // no providerId in the cookie name => assume default provider
      return new AuthInfo(username, cookie.getValue(),
          defaultAuthPlugin, defaultAuthProvider);
    }
    String providerId = username.substring(0, splitPos);
    username = username.substring(splitPos + 1);
    for (Entry<OAuthLoginProvider> e: loginProviders) {
      if (providerId.equals((e.getProvider().get().getProviderId()))) {
        return new AuthInfo(username, cookie.getValue(),
            e.getPluginName(), e.getExportName());
      }
    }
    return null;
  }

  private static String encoding(HttpServletRequest req) {
    return MoreObjects.firstNonNull(req.getCharacterEncoding(), UTF_8.name());
  }

  class AuthInfo {
    private String username;
    private String tokenOrSecret;
    private String pluginName;
    private String exportName;

    AuthInfo(String username, String tokenOrSecret,
        String pluginName, String exportName) {
      this.username = userNameToLowerCase
          ? username.toLowerCase(Locale.US)
          : username;
      this.tokenOrSecret = tokenOrSecret;
      this.pluginName = pluginName;
      this.exportName = exportName;
    }

    String getUsername() {
      return username;
    }

    String getTokenOrSecret() {
      return tokenOrSecret;
    }

    String getPluginName() {
      return pluginName;
    }

    String getExportName() {
      return exportName;
    }
  }

  class Response extends HttpServletResponseWrapper {
    private static final String WWW_AUTHENTICATE = "WWW-Authenticate";

    Response(HttpServletResponse rsp) {
      super(rsp);
    }

    private void status(int sc) {
      if (sc == SC_UNAUTHORIZED) {
        StringBuilder v = new StringBuilder();
        v.append(BASIC);
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
