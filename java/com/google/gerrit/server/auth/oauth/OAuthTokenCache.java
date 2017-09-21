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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.cache.Cache;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthTokenEncrypter;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.cache.CacheModule;
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
        persist(OAUTH_TOKENS, Account.Id.class, OAuthToken.class);
      }
    };
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
    cache.put(id, encrypt(checkNotNull(accessToken)));
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
