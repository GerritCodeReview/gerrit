// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.auth.oauth;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Converter;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthTokenEncrypter;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.proto.Cache.OAuthTokenProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.IntegerCacheSerializer;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Set;
import org.eclipse.jgit.lib.Config;

@Singleton
public class OAuthTokenCache {
  public static final String OAUTH_TOKENS = "oauth_tokens";
  private static final long DEFAULT_MEMORY_LIMIT = 1024;

  private final DynamicItem<OAuthTokenEncrypter> encrypter;

  public enum AccountIdSerializer implements CacheSerializer<Account.Id> {
    INSTANCE;

    private final Converter<Account.Id, Integer> converter =
        Converter.from(Account.Id::get, Account::id);

    private final Converter<Integer, Account.Id> reverse = converter.reverse();

    @Override
    public byte[] serialize(Account.Id object) {
      return IntegerCacheSerializer.INSTANCE.serialize(converter.convert(object));
    }

    @Override
    public Account.Id deserialize(byte[] in) {
      return reverse.convert(IntegerCacheSerializer.INSTANCE.deserialize(in));
    }
  }

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(OAUTH_TOKENS, Account.Id.class, OAuthToken.class)
            .version(1)
            .keySerializer(AccountIdSerializer.INSTANCE)
            .valueSerializer(new Serializer());
      }
    };
  }

  // Defined outside of OAuthToken class, since that is in the extensions package which doesn't have
  // access to the serializer code.
  @VisibleForTesting
  static class Serializer implements CacheSerializer<OAuthToken> {
    @Override
    public byte[] serialize(OAuthToken object) {
      return Protos.toByteArray(
          OAuthTokenProto.newBuilder()
              .setToken(object.getToken())
              .setSecret(object.getSecret())
              .setRaw(object.getRaw())
              .setExpiresAtMillis(object.getExpiresAt())
              .setProviderId(Strings.nullToEmpty(object.getProviderId()))
              .build());
    }

    @Override
    public OAuthToken deserialize(byte[] in) {
      OAuthTokenProto proto = Protos.parseUnchecked(OAuthTokenProto.parser(), in);
      return new OAuthToken(
          proto.getToken(),
          proto.getSecret(),
          proto.getRaw(),
          proto.getExpiresAtMillis(),
          Strings.emptyToNull(proto.getProviderId()));
    }
  }

  private final Cache<Account.Id, OAuthToken> cache;
  private final boolean disabled;

  @Inject
  OAuthTokenCache(
      @Named(OAUTH_TOKENS) Cache<Account.Id, OAuthToken> cache,
      DynamicItem<OAuthTokenEncrypter> encrypter,
      @GerritServerConfig Config cfg) {
    this.cache = cache;
    this.encrypter = encrypter;
    this.disabled = cfg.getLong("cache", OAUTH_TOKENS, "memoryLimit", DEFAULT_MEMORY_LIMIT) == 0;
  }

  /**
   * Returns the decrypted token even if it has expired, without evicting it. Refresh-aware callers
   * use this so an expired token's {@code raw} (which may carry a refresh token) stays reachable;
   * ordinary callers should use {@link #getOrEvictIfExpired(Account.Id)}, which evicts expired
   * tokens.
   */
  @Nullable
  public OAuthToken getEvenIfExpired(Account.Id id) {
    if (disabled) {
      return null;
    }
    OAuthToken accessToken = cache.getIfPresent(id);
    if (accessToken == null) {
      return null;
    }
    return decrypt(accessToken);
  }

  @Nullable
  public OAuthToken getOrEvictIfExpired(Account.Id id) {
    OAuthToken accessToken = getEvenIfExpired(id);
    if (accessToken == null) {
      return null;
    }
    if (accessToken.isExpired()) {
      cache.invalidate(id);
      return null;
    }
    return accessToken;
  }

  /**
   * True if a token is cached for the account and expired (checks cleartext {@code expiresAt}, no
   * decrypt).
   */
  public boolean hasExpiredToken(Account.Id id) {
    OAuthToken accessToken = cache.getIfPresent(id);
    return accessToken != null && accessToken.isExpired();
  }

  public void put(Account.Id id, OAuthToken accessToken) {
    requireNonNull(accessToken);
    if (disabled) {
      return;
    }
    cache.put(id, encrypt(accessToken));
  }

  public void remove(Account.Id id) {
    cache.invalidate(id);
  }

  public boolean isDisabled() {
    return disabled;
  }

  /** Purges every cached OAuth token (e.g. after a suspected site compromise or key theft). */
  public void removeAll() {
    cache.invalidateAll();
  }

  /**
   * Account ids with a token in the in-memory cache; used by bulk revoke. Disk-only entries are not
   * listed. {@link #removeAll()} still purges them, but they cannot be individually revoked
   * upstream (their token is not loaded).
   */
  public Set<Account.Id> accountsWithCachedToken() {
    return ImmutableSet.copyOf(cache.asMap().keySet());
  }

  private OAuthToken encrypt(OAuthToken token) {
    OAuthTokenEncrypter enc = encrypter.get();
    if (enc == null) {
      return token;
    }
    return enc.encrypt(token);
  }

  private OAuthToken decrypt(OAuthToken token) {
    OAuthTokenEncrypter enc = encrypter.get();
    if (enc == null) {
      return token;
    }
    return enc.decrypt(token);
  }
}
