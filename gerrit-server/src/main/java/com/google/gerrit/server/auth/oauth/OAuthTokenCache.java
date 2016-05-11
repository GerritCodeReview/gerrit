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
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.extensions.registration.DynamicItem;
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
        persist(OAUTH_TOKENS, String.class, OAuthToken.class);
      }
    };
  }

  private final Cache<String, OAuthToken> cache;

  @Inject
  OAuthTokenCache(@Named(OAUTH_TOKENS) Cache<String, OAuthToken> cache,
      DynamicItem<OAuthTokenEncrypter> encrypter) {
    this.cache = cache;
    this.encrypter = encrypter;
  }

  public boolean has(OAuthUserInfo user) {
    return user != null
      ? cache.getIfPresent(user.getUserName()) != null
      : false;
  }

  public OAuthToken get(OAuthUserInfo user) {
    return user != null
      ? get(user.getUserName())
      : null;
  }

  public OAuthToken get(String userName) {
    OAuthToken accessToken = cache.getIfPresent(userName);
    if (accessToken == null) {
      return null;
    }
    accessToken = decrypt(accessToken);
    if (accessToken.isExpired()) {
      cache.invalidate(userName);
      return null;
    }
    return accessToken;
  }

  public void put(OAuthUserInfo user, OAuthToken accessToken) {
    cache.put(checkNotNull(user.getUserName()),
        encrypt(checkNotNull(accessToken)));
  }

  public void remove(OAuthUserInfo user) {
    if (user != null) {
      cache.invalidate(user.getUserName());
    }
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
