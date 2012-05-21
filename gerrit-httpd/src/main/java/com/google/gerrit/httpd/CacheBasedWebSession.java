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

package com.google.gerrit.httpd;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.gerrit.httpd.WebSessionManager.Key;
import com.google.gerrit.httpd.WebSessionManager.Val;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AuthResult;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.servlet.RequestScoped;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RequestScoped
public final class CacheBasedWebSession implements WebSession {
  private static final String ACCOUNT_COOKIE = "GerritAccount";
  static final long MAX_AGE_MINUTES = HOURS.toMinutes(12);

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(WebSessionManager.CACHE_NAME, String.class, Val.class)
            .maximumWeight(1024) // reasonable default for many sites
            .expireAfterWrite(MAX_AGE_MINUTES, MINUTES) // expire sessions if they are inactive
        ;
        bind(WebSessionManager.class);
        bind(WebSession.class)
          .to(CacheBasedWebSession.class)
          .in(RequestScoped.class);
      }
    };
  }

  private final HttpServletRequest request;
  private final HttpServletResponse response;
  private final WebSessionManager manager;
  private final AuthConfig authConfig;
  private final Provider<AnonymousUser> anonymousProvider;
  private final IdentifiedUser.RequestFactory identified;
  private AccessPath accessPath = AccessPath.WEB_UI;
  private Cookie outCookie;

  private Key key;
  private Val val;

  @Inject
  CacheBasedWebSession(final HttpServletRequest request,
      final HttpServletResponse response, final WebSessionManager manager,
      final AuthConfig authConfig,
      final Provider<AnonymousUser> anonymousProvider,
      final IdentifiedUser.RequestFactory identified) {
    this.request = request;
    this.response = response;
    this.manager = manager;
    this.authConfig = authConfig;
    this.anonymousProvider = anonymousProvider;
    this.identified = identified;

    final String cookie = readCookie();
    if (cookie != null) {
      key = new Key(cookie);
      val = manager.get(key);
    } else {
      key = null;
      val = null;
    }

    if (isSignedIn() && val.needsCookieRefresh()) {
      // Cookie is more than half old. Send the cookie again to the
      // client with an updated expiration date. We don't dare to
      // change the key token here because there may be other RPCs
      // queued up in the browser whose xsrfKey would not get updated
      // with the new token, causing them to fail.
      //
      val = manager.createVal(key, val);
      saveCookie();
    }
  }

  private String readCookie() {
    final Cookie[] all = request.getCookies();
    if (all != null) {
      for (final Cookie c : all) {
        if (ACCOUNT_COOKIE.equals(c.getName())) {
          final String v = c.getValue();
          return v != null && !"".equals(v) ? v : null;
        }
      }
    }
    return null;
  }

  public boolean isSignedIn() {
    return val != null;
  }

  public String getToken() {
    return isSignedIn() ? val.getXsrfToken() : null;
  }

  public boolean isTokenValid(final String inputToken) {
    return isSignedIn() //
        && val.getXsrfToken() != null //
        && val.getXsrfToken().equals(inputToken);
  }

  public AccountExternalId.Key getLastLoginExternalId() {
    return val != null ? val.getExternalId() : null;
  }

  public CurrentUser getCurrentUser() {
    if (isSignedIn()) {
      return identified.create(accessPath, val.getAccountId());
    }
    return anonymousProvider.get();
  }

  public void login(final AuthResult res, final boolean rememberMe) {
    final Account.Id id = res.getAccountId();
    final AccountExternalId.Key identity = res.getExternalId();

    if (val != null) {
      manager.destroy(key);
    }

    key = manager.createKey(id);
    val = manager.createVal(key, id, rememberMe, identity, null);
    saveCookie();
  }

  /** Change the access path from the default of {@link AccessPath#WEB_UI}. */
  public void setAccessPath(AccessPath path) {
    accessPath = path;
  }

  /** Set the user account for this current request only. */
  public void setUserAccountId(Account.Id id) {
    key = new Key("id:" + id);
    val = new Val(id, 0, false, null, "", 0);
  }

  public void logout() {
    if (val != null) {
      manager.destroy(key);
      key = null;
      val = null;
      saveCookie();
    }
  }

  private void saveCookie() {
    final String token;
    final int ageSeconds;

    if (key == null) {
      token = "";
      ageSeconds = 0 /* erase at client */;
    } else {
      token = key.getToken();
      ageSeconds = manager.getCookieAge(val);
    }

    String path = authConfig.getCookiePath();
    if (path == null || path.isEmpty()) {
      path = request.getContextPath();
      if (path == null || path.isEmpty()) {
        path = "/";
      }
    }

    if (outCookie != null) {
      throw new IllegalStateException("Cookie " + ACCOUNT_COOKIE + " was set");
    }

    outCookie = new Cookie(ACCOUNT_COOKIE, token);
    outCookie.setSecure(isSecure(request));
    outCookie.setPath(path);
    outCookie.setMaxAge(ageSeconds);
    outCookie.setSecure(authConfig.getCookieSecure());
    response.addCookie(outCookie);
  }

  private static boolean isSecure(final HttpServletRequest req) {
    return req.isSecure() || "https".equals(req.getScheme());
  }
}
