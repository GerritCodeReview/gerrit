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

package com.google.gerrit.server.auth.oauth;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Converter;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthTokenEncrypter;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.proto.Cache.OAuthTokenProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.IntegerCacheSerializer;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

@Singleton
public class OAuthTokenCache {
  public static final String OAUTH_TOKENS = "oauth_tokens";

  private final DynamicItem<OAuthTokenEncrypter> encrypter;

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(OAUTH_TOKENS, Account.Id.class, OAuthToken.class)
            .version(1)
            .keySerializer(
                CacheSerializer.convert(
                    IntegerCacheSerializer.INSTANCE,
                    Converter.from(Account.Id::get, Account.Id::new)))
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
              .setExpiresAt(object.getExpiresAt())
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
          proto.getExpiresAt(),
          Strings.emptyToNull(proto.getProviderId()));
    }
  }

  private final Cache<Account.Id, OAuthToken> cache;

  @Inject
  OAuthTokenCache(
      @Named(OAUTH_TOKENS) Cache<Account.Id, OAuthToken> cache,
      DynamicItem<OAuthTokenEncrypter> encrypter) {
    this.cache = cache;
    this.encrypter = encrypter;
  }

  public OAuthToken get(Account.Id id) {
    OAuthToken accessToken = cache.getIfPresent(id);
    if (accessToken == null) {
      return null;
    }
    accessToken = decrypt(accessToken);
    if (accessToken.isExpired()) {
      cache.invalidate(id);
      return null;
    }
    return accessToken;
  }

  public void put(Account.Id id, OAuthToken accessToken) {
    cache.put(id, encrypt(requireNonNull(accessToken)));
  }

  public void remove(Account.Id id) {
    cache.invalidate(id);
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
