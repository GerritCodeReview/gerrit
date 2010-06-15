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

import static com.google.gerrit.server.ioutil.BasicSerialization.writeBytes;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeVarInt32;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.reviewdb.ActiveSession;
import com.google.gerrit.reviewdb.ActiveSessionAccess;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AuthResult;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.EvictionPolicy;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.servlet.RequestScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RequestScoped
public final class WebSession {
  private static final Logger log = LoggerFactory.getLogger(WebSession.class);
  private static final String ACCOUNT_COOKIE = "GerritAccount";
  private static final String CACHE_NAME = "web_sessions";
  private static final long UPDATE_WAIT_MILLISECONDS =
      MILLISECONDS.convert(5, TimeUnit.MINUTES);

  static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<ActiveSession.Key, ActiveSession>> type =
            new TypeLiteral<Cache<ActiveSession.Key, ActiveSession>>() {};
        core(type, CACHE_NAME) //
            .memoryLimit(1024) // reasonable default for many sites
            .maxAge(12, HOURS) // expire sessions if they are inactive
            .evictionPolicy(EvictionPolicy.LRU) // keep most recently used
        ;
        bind(WebSession.class).in(RequestScoped.class);
      }
    };
  }

  // We want a singleton SecureRandom, but we don't want to make every
  // SecureRandom a singleton, so instead we have a KeyGenerator class that can
  // be used in its place.
  @Singleton
  static class KeyGenerator extends SecureRandom {
  }

  private static long now() {
    return System.currentTimeMillis();
  }

  private final KeyGenerator prng;
  private final Cache<ActiveSession.Key, ActiveSession> cache;
  private final HttpServletRequest request;
  private final HttpServletResponse response;
  private final AnonymousUser anonymous;
  private final IdentifiedUser.RequestFactory identified;
  private final ReviewDb schema;
  private AccessPath accessPath = AccessPath.WEB_UI;
  private Cookie outCookie;

  private ActiveSession.Key key;
  private ActiveSession session;

  @Inject
  WebSession(final HttpServletRequest request,
      final HttpServletResponse response, final AnonymousUser anonymous,
      final IdentifiedUser.RequestFactory identified, ReviewDb schema,
      @Named(CACHE_NAME) final Cache<ActiveSession.Key, ActiveSession> cache,
      KeyGenerator prng) throws OrmException {
    this.request = request;
    this.response = response;
    this.anonymous = anonymous;
    this.identified = identified;
    this.schema = schema;
    this.cache = cache;
    this.prng = prng;

    final String cookie = readCookie();
    if (cookie != null) {
      key = new ActiveSession.Key(cookie);
      session = get(key);
    } else {
      key = null;
      session = null;
    }

    if (isSignedIn() && session.needsCookieRefresh()) {
      // Cookie is more than half old. Send the cookie again to the
      // client with an updated expiration date. We don't dare to
      // change the key token here because there may be other RPCs
      // queued up in the browser whose xsrfKey would not get updated
      // with the new token, causing them to fail.
      //
      session = createSession(key, session);
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
    return session != null;
  }

  public String getToken() {
    return isSignedIn() ? session.getXsrfToken() : null;
  }

  public boolean isTokenValid(final String inputToken) {
    return isSignedIn() //
        && session.getXsrfToken() != null //
        && session.getXsrfToken().equals(inputToken);
  }

  public AccountExternalId.Key getLastLoginExternalId() {
    return session != null ? session.getExternalId() : null;
  }

  CurrentUser getCurrentUser() {
    if (isSignedIn()) {
      return identified.create(accessPath, session.getAccountId());
    }
    return anonymous;
  }

  public void login(final AuthResult res, final boolean rememberMe)
      throws OrmException {
    final Account.Id id = res.getAccountId();
    final AccountExternalId.Key identity = res.getExternalId();

    if (session != null) {
      destroy(key);
      key = null;
      session = null;
    }

    key = createKey(id);
    session = createSession(key, id, rememberMe, identity, null);
    saveCookie();
  }

  /** Change the access path from the default of {@link AccessPath#WEB_UI}. */
  void setAccessPath(AccessPath path) {
    accessPath = path;
  }

  /** Set the user account for this current request only. */
  void setUserAccountId(Account.Id id) {
    key = new ActiveSession.Key("id:" + id);
    session = new ActiveSession(key, id, new Timestamp(0), false, null, "");
  }

  public void logout() {
    if (session != null) {
      try {
        destroy(key);
      } catch (OrmException e) {
        log.error("Could not remove session key from cache", e);
      }
      key = null;
      session = null;
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
      token = key.get();
      ageSeconds = getCookieAge(session);
    }

    String path = request.getContextPath();
    if (path.equals("")) {
      path = "/";
    }

    if (outCookie != null) {
      throw new IllegalStateException("Cookie " + ACCOUNT_COOKIE + " was set");
    }

    outCookie = new Cookie(ACCOUNT_COOKIE, token);
    outCookie.setSecure(isSecure(request));
    outCookie.setPath(path);
    outCookie.setMaxAge(ageSeconds);
    response.addCookie(outCookie);
  }

  private static boolean isSecure(final HttpServletRequest req) {
    return req.isSecure() || "https".equals(req.getScheme());
  }

  private ActiveSession.Key createKey(final Account.Id who) {
    try {
      final int nonceLen = 20;
      final ByteArrayOutputStream buf;
      final byte[] rnd = new byte[nonceLen];
      prng.nextBytes(rnd);

      buf = new ByteArrayOutputStream(3 + nonceLen);
      writeVarInt32(buf, who.get());
      writeBytes(buf, rnd);

      return new ActiveSession.Key(CookieBase64.encode(buf.toByteArray()));
    } catch (IOException e) {
      throw new RuntimeException("Cannot produce new account cookie", e);
    }
  }

  private ActiveSession createSession(final ActiveSession.Key key,
      final ActiveSession session) throws OrmException {
    final Account.Id who = session.getAccountId();
    final boolean remember = session.isPersistentCookie();
    final AccountExternalId.Key lastLogin = session.getExternalId();
    final String xsrfToken = session.getXsrfToken();

    return createSession(key, who, remember, lastLogin, xsrfToken);
  }

  private ActiveSession createSession(final ActiveSession.Key key,
      final Account.Id who, final boolean remember,
      final AccountExternalId.Key lastLogin, String xsrfToken)
      throws OrmException {
    // Refresh the cookie every hour or when it is half-expired.
    // This reduces the odds that the user session will be kicked
    // early but also avoids us needing to refresh the cookie on
    // every single request.
    //
    final long halfAgeRefresh = cache.getTimeToLive(MILLISECONDS) >>> 1;
    final long minRefresh = MILLISECONDS.convert(1, HOURS);
    final long refresh = Math.min(halfAgeRefresh, minRefresh);
    final long refreshCookieAt = now() + refresh;

    if (xsrfToken == null) {
      // If we don't yet have a token for this session, establish one.
      //
      final int nonceLen = 20;
      final ByteArrayOutputStream buf;
      final byte[] rnd = new byte[nonceLen];
      prng.nextBytes(rnd);
      xsrfToken = CookieBase64.encode(rnd);
    }

    ActiveSession session =
        new ActiveSession(key, who, new Timestamp(refreshCookieAt), remember,
            lastLogin, xsrfToken);
    put(session);
    return session;
  }

  private int getCookieAge(final ActiveSession session) {
    if (session.isPersistentCookie()) {
      // Client may store the cookie until we would remove it from our
      // own cache, after which it will certainly be invalid.
      //
      return (int) cache.getTimeToLive(SECONDS);
    } else {
      // Client should not store the cookie, as the user asked for us
      // to not remember them long-term. Sending -1 as the age will
      // cause the cookie to be only for this "browser session", which
      // is usually until the user exits their browser.
      //
      return -1;
    }
  }

  private ActiveSession get(final ActiveSession.Key key) throws OrmException {
    ActiveSession as = cache.get(key);
    final ActiveSessionAccess activeSessions = schema.activeSessions();

    if (as == null) {
      as = activeSessions.get(key);

      if (as == null) {
        return null;
      } else {
        if (expiredFromCache(as)) {
          destroy(key);
          return null;
        } else if (needsCacheRefresh(as)) {
          as.updateLastSeen();
          put(as);
        }
        return as;
      }
    } else {
      if (needsCacheRefresh(as)) {
        as.updateLastSeen();
        put(as);
      }
      return as;
    }
  }

  private boolean needsCacheRefresh(ActiveSession as) {
    if (as.getLastSeen() == null) {
      return true;
    }
    Timestamp refreshAt =
        new Timestamp(as.getLastSeen().getTime() + UPDATE_WAIT_MILLISECONDS);
    Timestamp now = new Timestamp(now());

    return now.after(refreshAt);
  }

  private boolean expiredFromCache(ActiveSession as) {
    if (as.getLastSeen() == null) {
      return true;
    }
    Timestamp expireAt =
        new Timestamp(as.getLastSeen().getTime()
            + cache.getTimeToLive(MILLISECONDS));
    Timestamp now = new Timestamp(now());

    return now.after(expireAt);
  }

  private void destroy(final ActiveSession.Key key) throws OrmException {
    schema.activeSessions().deleteKeys(Arrays.asList(key));
    cache.remove(key);
  }

  private void put(final ActiveSession as) throws OrmException {
    schema.activeSessions().upsert(Arrays.asList(as));
    cache.put(schema.activeSessions().primaryKey(as), as);
  }
}
