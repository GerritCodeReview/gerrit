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

import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.HostPageData;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.httpd.WebSessionManager.Key;
import com.google.gerrit.httpd.WebSessionManager.Val;
import com.google.gerrit.httpd.restapi.ParameterParser;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AuthResult;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.Provider;
import com.google.inject.servlet.RequestScoped;
import java.util.EnumSet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.http.server.GitSmartHttpTools;

@RequestScoped
public abstract class CacheBasedWebSession implements WebSession {
  private static final String ACCOUNT_COOKIE = "GerritAccount";
  protected static final long MAX_AGE_MINUTES = HOURS.toMinutes(12);

  private final HttpServletRequest request;
  private final HttpServletResponse response;
  private final WebSessionManager manager;
  private final AuthConfig authConfig;
  private final Provider<AnonymousUser> anonymousProvider;
  private final IdentifiedUser.RequestFactory identified;
  private final EnumSet<AccessPath> okPaths = EnumSet.of(AccessPath.UNKNOWN);
  private Cookie outCookie;

  private Key key;
  private Val val;
  private CurrentUser user;

  protected CacheBasedWebSession(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final WebSessionManager manager,
      final AuthConfig authConfig,
      final Provider<AnonymousUser> anonymousProvider,
      final IdentifiedUser.RequestFactory identified) {
    this.request = request;
    this.response = response;
    this.manager = manager;
    this.authConfig = authConfig;
    this.anonymousProvider = anonymousProvider;
    this.identified = identified;

    if (request.getRequestURI() == null || !GitSmartHttpTools.isGitClient(request)) {
      String cookie = readCookie(request);
      if (cookie != null) {
        authFromCookie(cookie);
      } else {
        String token;
        try {
          token = ParameterParser.getQueryParams(request).accessToken();
        } catch (BadRequestException e) {
          token = null;
        }
        if (token != null) {
          authFromQueryParameter(token);
        }
      }
      if (val != null && val.needsCookieRefresh()) {
        // Session is more than half old; update cache entry with new expiration date.
        val = manager.createVal(key, val);
      }
    }
  }

  private void authFromCookie(String cookie) {
    key = new Key(cookie);
    val = manager.get(key);
    String token = request.getHeader(HostPageData.XSRF_HEADER_NAME);
    if (val != null && token != null && token.equals(val.getAuth())) {
      okPaths.add(AccessPath.REST_API);
    }
  }

  private void authFromQueryParameter(String accessToken) {
    key = new Key(accessToken);
    val = manager.get(key);
    if (val != null) {
      okPaths.add(AccessPath.REST_API);
    }
  }

  private static String readCookie(HttpServletRequest request) {
    Cookie[] all = request.getCookies();
    if (all != null) {
      for (Cookie c : all) {
        if (ACCOUNT_COOKIE.equals(c.getName())) {
          return Strings.emptyToNull(c.getValue());
        }
      }
    }
    return null;
  }

  @Override
  public boolean isSignedIn() {
    return val != null;
  }

  @Override
  @Nullable
  public String getXGerritAuth() {
    return isSignedIn() ? val.getAuth() : null;
  }

  @Override
  public boolean isValidXGerritAuth(String keyIn) {
    return keyIn.equals(getXGerritAuth());
  }

  @Override
  public boolean isAccessPathOk(AccessPath path) {
    return okPaths.contains(path);
  }

  @Override
  public void setAccessPathOk(AccessPath path, boolean ok) {
    if (ok) {
      okPaths.add(path);
    } else {
      okPaths.remove(path);
    }
  }

  @Override
  public ExternalId.Key getLastLoginExternalId() {
    return val != null ? val.getExternalId() : null;
  }

  @Override
  public CurrentUser getUser() {
    if (user == null) {
      if (isSignedIn()) {
        user = identified.create(val.getAccountId());
      } else {
        user = anonymousProvider.get();
      }
    }
    return user;
  }

  @Override
  public void login(AuthResult res, boolean rememberMe) {
    Account.Id id = res.getAccountId();
    ExternalId.Key identity = res.getExternalId();

    if (val != null) {
      manager.destroy(key);
    }

    key = manager.createKey(id);
    val = manager.createVal(key, id, rememberMe, identity, null, null);
    saveCookie();
    user = identified.create(val.getAccountId());
  }

  /** Set the user account for this current request only. */
  @Override
  public void setUserAccountId(Account.Id id) {
    key = new Key("id:" + id);
    val = new Val(id, 0, false, null, 0, null, null);
    user = identified.runAs(id, user);
  }

  @Override
  public void logout() {
    if (val != null) {
      manager.destroy(key);
      key = null;
      val = null;
      saveCookie();
      user = anonymousProvider.get();
    }
  }

  @Override
  public String getSessionId() {
    return val != null ? val.getSessionId() : null;
  }

  private void saveCookie() {
    if (response == null) {
      return;
    }

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
    if (Strings.isNullOrEmpty(path)) {
      path = request.getContextPath();
      if (Strings.isNullOrEmpty(path)) {
        path = "/";
      }
    }

    if (outCookie != null) {
      throw new IllegalStateException("Cookie " + ACCOUNT_COOKIE + " was set");
    }

    outCookie = new Cookie(ACCOUNT_COOKIE, token);

    String domain = authConfig.getCookieDomain();
    if (!Strings.isNullOrEmpty(domain)) {
      outCookie.setDomain(domain);
    }

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
