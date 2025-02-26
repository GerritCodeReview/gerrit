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

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Singleton
public class AuthTokenCacheImpl implements AuthTokenCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String CACHE_NAME = "tokens";

  private final LoadingCache<Account.Id, List<AuthToken>> cache;

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(CACHE_NAME, Account.Id.class, new TypeLiteral<List<AuthToken>>() {})
            .loader(Loader.class);
        bind(AuthTokenCache.class).to(AuthTokenCacheImpl.class);
      }
    };
  }

  @Inject
  AuthTokenCacheImpl(@Named(CACHE_NAME) LoadingCache<Account.Id, List<AuthToken>> cache) {
    this.cache = cache;
  }

  @Override
  public List<AuthToken> get(Account.Id accountId) {
    try {
      return cache.get(accountId);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log(
          "Cannot load authentication tokens for %d", accountId.get());
      throw new StorageException(e);
    }
  }

  @Override
  public void evict(Account.Id accountId) {
    if (accountId != null) {
      logger.atFine().log("Evict authentication token for username %d", accountId.get());
      cache.invalidate(accountId);
    }
  }

  static class Loader extends CacheLoader<Account.Id, List<AuthToken>> {
    private final AccountCache accountCache;
    private final VersionedAuthTokens.Factory authTokenFactory;

    @Inject
    Loader(AccountCache accountCache, VersionedAuthTokens.Factory authTokenFactory) {
      this.accountCache = accountCache;
      this.authTokenFactory = authTokenFactory;
    }

    @Override
    public ImmutableList<AuthToken> load(Account.Id accountId) throws Exception {
      try (TraceTimer timer =
          TraceContext.newTimer(
              "Loading authentication tokens for account with username",
              Metadata.builder().accountId(accountId.get()).build())) {
        AccountState accountState = accountCache.getEvenIfMissing(accountId);
        Optional<ExternalId> optUser =
            accountState.externalIds().stream()
                .filter(e -> e.key().scheme().equals(SCHEME_USERNAME))
                .findFirst();
        if (!optUser.isPresent()) {
          return ImmutableList.of();
        }

        ImmutableList<AuthToken> tokens = authTokenFactory.create(accountId).load().getTokens();

        // Fall back to legacy HTTP password if no tokens are present.
        if (tokens.isEmpty()) {
          Optional<AuthToken> legacyHttpPassword = getLegacyHttpPassword(accountId);
          tokens =
              legacyHttpPassword.isPresent()
                  ? ImmutableList.of(legacyHttpPassword.get())
                  : ImmutableList.of();
        }

        return tokens;
      }
    }

    @Deprecated
    private Optional<AuthToken> getLegacyHttpPassword(Account.Id accountId) {
      AccountState accountState = accountCache.getEvenIfMissing(accountId);
      Optional<ExternalId> optUser =
          accountState.externalIds().stream()
              .filter(e -> e.key().scheme().equals(SCHEME_USERNAME))
              .findFirst();
      if (optUser.isEmpty()) {
        return Optional.empty();
      }
      ExternalId user = optUser.get();
      String password = user.password();
      if (password != null) {
        return Optional.of(AuthToken.create("legacy", password));
      }
      return Optional.empty();
    }
  }
}
