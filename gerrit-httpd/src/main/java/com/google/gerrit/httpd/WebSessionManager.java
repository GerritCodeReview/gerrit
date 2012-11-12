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

import static com.google.gerrit.httpd.CacheBasedWebSession.MAX_AGE_MINUTES;
import static com.google.gerrit.server.ioutil.BasicSerialization.readFixInt64;
import static com.google.gerrit.server.ioutil.BasicSerialization.readString;
import static com.google.gerrit.server.ioutil.BasicSerialization.readVarInt32;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeBytes;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeFixInt64;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeString;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeVarInt32;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.common.cache.Cache;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.eclipse.jgit.lib.Config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Singleton
class WebSessionManager {
  static final String CACHE_NAME = "web_sessions";

  static long now() {
    return System.currentTimeMillis();
  }

  private final long sessionMaxAgeMillis;
  private final SecureRandom prng;
  private final Cache<String, Val> self;

  @Inject
  WebSessionManager(@GerritServerConfig Config cfg,
      @Named(CACHE_NAME) final Cache<String, Val> cache) {
    prng = new SecureRandom();
    self = cache;

    sessionMaxAgeMillis = MINUTES.toMillis(ConfigUtil.getTimeUnit(cfg,
        "cache", CACHE_NAME, "maxAge",
        MAX_AGE_MINUTES, MINUTES));
  }

  Key createKey(final Account.Id who) {
    try {
      final int nonceLen = 20;
      final ByteArrayOutputStream buf;
      final byte[] rnd = new byte[nonceLen];
      prng.nextBytes(rnd);

      buf = new ByteArrayOutputStream(3 + nonceLen);
      writeVarInt32(buf, (int) Val.serialVersionUID);
      writeVarInt32(buf, who.get());
      writeBytes(buf, rnd);

      return new Key(CookieBase64.encode(buf.toByteArray()));
    } catch (IOException e) {
      throw new RuntimeException("Cannot produce new account cookie", e);
    }
  }

  Val createVal(final Key key, final Val val) {
    final Account.Id who = val.getAccountId();
    final boolean remember = val.isPersistentCookie();
    final AccountExternalId.Key lastLogin = val.getExternalId();
    return createVal(key, who, remember, lastLogin);
  }

  Val createVal(final Key key, final Account.Id who, final boolean remember,
      final AccountExternalId.Key lastLogin) {
    // Refresh the cookie every hour or when it is half-expired.
    // This reduces the odds that the user session will be kicked
    // early but also avoids us needing to refresh the cookie on
    // every single request.
    //
    final long halfAgeRefresh = sessionMaxAgeMillis >>> 1;
    final long minRefresh = MILLISECONDS.convert(1, HOURS);
    final long refresh = Math.min(halfAgeRefresh, minRefresh);
    final long now = now();
    final long refreshCookieAt = now + refresh;
    final long expiresAt = now + sessionMaxAgeMillis;

    Val val = new Val(who, refreshCookieAt, remember, lastLogin, expiresAt);
    self.put(key.token, val);
    return val;
  }

  int getCookieAge(final Val val) {
    if (val.isPersistentCookie()) {
      // Client may store the cookie until we would remove it from our
      // own cache, after which it will certainly be invalid.
      //
      return (int) MILLISECONDS.toSeconds(sessionMaxAgeMillis);
    } else {
      // Client should not store the cookie, as the user asked for us
      // to not remember them long-term. Sending -1 as the age will
      // cause the cookie to be only for this "browser session", which
      // is usually until the user exits their browser.
      //
      return -1;
    }
  }

  Val get(final Key key) {
    Val val = self.getIfPresent(key.token);
    if (val != null && val.expiresAt <= now()) {
      self.invalidate(key.token);
      return null;
    }
    return val;
  }

  void destroy(final Key key) {
    self.invalidate(key.token);
  }

  static final class Key  {
    private transient String token;

    Key(final String t) {
      token = t;
    }

    String getToken() {
      return token;
    }

    @Override
    public int hashCode() {
      return token.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Key && token.equals(((Key) obj).token);
    }
  }

  static final class Val implements Serializable {
    static final long serialVersionUID = 2L;

    private transient Account.Id accountId;
    private transient long refreshCookieAt;
    private transient boolean persistentCookie;
    private transient AccountExternalId.Key externalId;
    private transient long expiresAt;

    Val(final Account.Id accountId, final long refreshCookieAt,
        final boolean persistentCookie, final AccountExternalId.Key externalId,
        final long expiresAt) {
      this.accountId = accountId;
      this.refreshCookieAt = refreshCookieAt;
      this.persistentCookie = persistentCookie;
      this.externalId = externalId;
      this.expiresAt = expiresAt;
    }

    Account.Id getAccountId() {
      return accountId;
    }

    AccountExternalId.Key getExternalId() {
      return externalId;
    }

    boolean needsCookieRefresh() {
      return refreshCookieAt <= now();
    }

    boolean isPersistentCookie() {
      return persistentCookie;
    }

    private void writeObject(final ObjectOutputStream out) throws IOException {
      writeVarInt32(out, 1);
      writeVarInt32(out, accountId.get());

      writeVarInt32(out, 2);
      writeFixInt64(out, refreshCookieAt);

      writeVarInt32(out, 3);
      writeVarInt32(out, persistentCookie ? 1 : 0);

      if (externalId != null) {
        writeVarInt32(out, 4);
        writeString(out, externalId.get());
      }

      writeVarInt32(out, 6);
      writeFixInt64(out, expiresAt);

      writeVarInt32(out, 0);
    }

    private void readObject(final ObjectInputStream in) throws IOException {
      PARSE: for (;;) {
        final int tag = readVarInt32(in);
        switch (tag) {
          case 0:
            break PARSE;
          case 1:
            accountId = new Account.Id(readVarInt32(in));
            continue;
          case 2:
            refreshCookieAt = readFixInt64(in);
            continue;
          case 3:
            persistentCookie = readVarInt32(in) != 0;
            continue;
          case 4:
            externalId = new AccountExternalId.Key(readString(in));
            continue;
          case 5:
            readString(in);
            continue;
          case 6:
            expiresAt = readFixInt64(in);
            continue;
          default:
            throw new IOException("Unknown tag found in object: " + tag);
        }
      }
      if (expiresAt == 0) {
        expiresAt = refreshCookieAt + TimeUnit.HOURS.toMillis(2);
      }
    }
  }
}
