// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.gerrit.entities.Account;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class NoAuthTokenCache implements AuthTokenCache {
  private final VersionedAuthTokens.Factory authTokensFactory;

  @Inject
  public NoAuthTokenCache(VersionedAuthTokens.Factory authTokensFactory) {
    this.authTokensFactory = authTokensFactory;
  }

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(AuthTokenCache.class).to(NoAuthTokenCache.class);
      }
    };
  }

  @Override
  public void evict(Account.Id accountId) {}

  @Override
  public List<AuthToken> get(Account.Id accountId) {
    return authTokensFactory.create(accountId).getTokens();
  }
}
