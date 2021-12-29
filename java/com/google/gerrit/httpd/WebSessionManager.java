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
import static com.google.gerrit.server.ioutil.BasicSerialization.writeBytes;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeVarInt32;
import static com.google.gerrit.server.util.time.TimeUtil.nowMs;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.auto.value.AutoValue;
import com.google.common.cache.Cache;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.cache.proto.Cache.WebSessionValueProto;
import com.google.gerrit.server.cache.proto.Cache.WebSessionValueProto.ExternalIdKey;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import org.eclipse.jgit.lib.Config;

public class WebSessionManager {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final String CACHE_NAME = "web_sessions";

  private final long sessionMaxAgeMillis;
  private final SecureRandom prng;
  private final Cache<String, Val> self;

  @Inject
  WebSessionManager(@GerritServerConfig Config cfg, @Assisted Cache<String, Val> cache) {
    prng = new SecureRandom();
    self = cache;

    sessionMaxAgeMillis =
        SECONDS.toMillis(
            ConfigUtil.getTimeUnit(
                cfg,
                "cache",
                CACHE_NAME,
                "maxAge",
                SECONDS.convert(MAX_AGE_MINUTES, MINUTES),
                SECONDS));
    if (sessionMaxAgeMillis < MINUTES.toMillis(5)) {
      logger.atWarning().log(
          "cache.%s.maxAge is set to %d milliseconds; it should be at least 5 minutes.",
          CACHE_NAME, sessionMaxAgeMillis);
    }
  }

  Key createKey(Account.Id who) {
    return new Key(newUniqueToken(who));
  }

  private String newUniqueToken(Account.Id who) {
    try {
      final int nonceLen = 20;
      final ByteArrayOutputStream buf;
      final byte[] rnd = new byte[nonceLen];
      prng.nextBytes(rnd);

      buf = new ByteArrayOutputStream(3 + nonceLen);
      writeVarInt32(buf, (int) 1);
      writeVarInt32(buf, who.get());
      writeBytes(buf, rnd);

      return CookieBase64.encode(buf.toByteArray());
    } catch (IOException e) {
      throw new RuntimeException("Cannot produce new account cookie", e);
    }
  }

  Val createVal(Key key, Val val) {
    Account.Id who = val.accountId();
    boolean remember = val.persistentCookie();
    ExternalId.Key lastLogin = val.externalId();
    return createVal(key, who, remember, lastLogin, val.sessionId(), val.auth());
  }

  Val createVal(
      Key key,
      Account.Id who,
      boolean remember,
      ExternalId.Key lastLogin,
      String sid,
      String auth) {
    // Refresh the cookie every hour or when it is half-expired.
    // This reduces the odds that the user session will be kicked
    // early but also avoids us needing to refresh the cookie on
    // every single request.
    //
    final long halfAgeRefresh = sessionMaxAgeMillis >>> 1;
    final long minRefresh = MILLISECONDS.convert(1, HOURS);
    final long refresh = Math.min(halfAgeRefresh, minRefresh);
    final long now = nowMs();
    final long refreshCookieAt = now + refresh;
    final long expiresAt = now + sessionMaxAgeMillis;
    if (sid == null) {
      sid = newUniqueToken(who);
    }
    if (auth == null) {
      auth = newUniqueToken(who);
    }

    Val val =
        Val.builder()
            .accountId(who)
            .refreshCookieAt(refreshCookieAt)
            .persistentCookie(remember)
            .externalId(lastLogin)
            .expiresAt(expiresAt)
            .sessionId(sid)
            .auth(auth)
            .build();
    self.put(key.token, val);
    return val;
  }

  int getCookieAge(Val val) {
    if (val.persistentCookie()) {
      // Client may store the cookie until we would remove it from our
      // own cache, after which it will certainly be invalid.
      //
      return (int) MILLISECONDS.toSeconds(sessionMaxAgeMillis);
    }
    // Client should not store the cookie, as the user asked for us
    // to not remember them long-term. Sending -1 as the age will
    // cause the cookie to be only for this "browser session", which
    // is usually until the user exits their browser.
    //
    return -1;
  }

  Val get(Key key) {
    Val val = self.getIfPresent(key.token);
    if (val != null && val.expiresAt() <= nowMs()) {
      self.invalidate(key.token);
      return null;
    }
    return val;
  }

  void destroy(Key key) {
    self.invalidate(key.token);
  }

  static final class Key {
    private transient String token;

    Key(String t) {
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

  @AutoValue
  public abstract static class Val {
    public abstract long expiresAt();

    public abstract Account.Id accountId();

    @Nullable
    abstract ExternalId.Key externalId();

    @Nullable
    abstract String sessionId();

    @Nullable
    abstract String auth();

    abstract boolean persistentCookie();

    abstract long refreshCookieAt();

    boolean needsCookieRefresh() {
      return refreshCookieAt() <= nowMs();
    }

    static Val.Builder builder() {
      return new AutoValue_WebSessionManager_Val.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder expiresAt(long val);

      abstract Builder accountId(Account.Id val);

      abstract Builder externalId(@Nullable ExternalId.Key val);

      abstract Builder sessionId(@Nullable String val);

      abstract Builder auth(@Nullable String val);

      abstract Builder persistentCookie(boolean val);

      abstract Builder refreshCookieAt(long val);

      abstract Val build();
    }

    public enum Serializer implements CacheSerializer<Val> {
      INSTANCE;

      @Override
      public byte[] serialize(Val object) {
        WebSessionValueProto.Builder builder =
            WebSessionValueProto.newBuilder()
                .setExpiresAt(object.expiresAt())
                .setAccountId(object.accountId().get())
                .setPersistentCookie(object.persistentCookie())
                .setRefreshCookieAt(object.refreshCookieAt());
        if (object.externalId() != null) {
          builder.setExternalId(
              ExternalIdKey.newBuilder()
                  .setKey(object.externalId().get())
                  .setIsKeyInsensitive(object.externalId().isCaseInsensitive())
                  .build());
        }
        if (object.sessionId() != null) {
          builder.setSessionId(object.sessionId());
        }
        if (object.auth() != null) {
          builder.setAuth(object.auth());
        }
        return builder.build().toByteArray();
      }

      @Override
      public Val deserialize(byte[] in) {
        WebSessionValueProto proto = Protos.parseUnchecked(WebSessionValueProto.parser(), in);
        return Val.builder()
            .expiresAt(proto.getExpiresAt())
            .accountId(Account.id(proto.getAccountId()))
            .externalId(
                ExternalId.Key.parse(
                    proto.getExternalId().getKey(), proto.getExternalId().getIsKeyInsensitive()))
            .sessionId(proto.getSessionId())
            .auth(proto.getAuth())
            .persistentCookie(proto.getPersistentCookie())
            .refreshCookieAt(proto.getRefreshCookieAt())
            .build();
      }
    }
  }
}
