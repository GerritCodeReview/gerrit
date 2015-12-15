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

import com.google.common.cache.Cache;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

@Singleton
public class OAuthTokenCache {
  public static final String OAUTH_TOKENS = "OAUTH_TOKENS";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(OAUTH_TOKENS, String.class, OAuthToken.class);
      }
    };
  }

  private final Cache<String, OAuthToken> cache;

  @Inject
  OAuthTokenCache(@Named(OAUTH_TOKENS) Cache<String, OAuthToken> cache) {
    this.cache = cache;
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
    if (accessToken != null && accessToken.isExpired()) {
      cache.invalidate(userName);
      return null;
    }
    return accessToken;
  }

  public void put(OAuthUserInfo user, OAuthToken accessToken) {
    if (user != null) {
      cache.put(user.getUserName(), accessToken);
    }
  }

  public void remove(OAuthUserInfo user) {
    if (user != null) {
      cache.invalidate(user.getUserName());
    }
  }
}
