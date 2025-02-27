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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class AuthTokenCacheImpl implements AuthTokenCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String CACHE_NAME = "tokens";
  static final Iterable<AuthTokenCacheEntry> NO_TOKENS = none();

  private final LoadingCache<Account.Id, Iterable<AuthTokenCacheEntry>> cache;

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(CACHE_NAME, Account.Id.class, new TypeLiteral<Iterable<AuthTokenCacheEntry>>() {})
            .loader(Loader.class);
        bind(AuthTokenCache.class).to(AuthTokenCacheImpl.class);
      }
    };
  }

  private static Iterable<AuthTokenCacheEntry> none() {
    return Collections.unmodifiableCollection(Arrays.asList(new AuthTokenCacheEntry[0]));
  }

  @Inject
  AuthTokenCacheImpl(
      @Named(CACHE_NAME) LoadingCache<Account.Id, Iterable<AuthTokenCacheEntry>> cache) {
    this.cache = cache;
  }

  @Override
  public Iterable<AuthTokenCacheEntry> get(Account.Id accountId) {
    try {
      return cache.get(accountId);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log(
          "Cannot load authentication tokens for %d", accountId.get());
      return Collections.emptyList();
    }
  }

  @Override
  public void evict(Account.Id accountId) {
    if (accountId != null) {
      logger.atFine().log("Evict authentication token for account %d", accountId.get());
      cache.invalidate(accountId);
    }
  }

  static class Loader extends CacheLoader<Account.Id, Iterable<AuthTokenCacheEntry>> {
    private final AccountCache accountCache;
    private final VersionedAuthTokens.Accessor versionedTokens;

    @Inject
    Loader(AccountCache accountCache, VersionedAuthTokens.Accessor versionedTokens) {
      this.accountCache = accountCache;
      this.versionedTokens = versionedTokens;
    }

    @Override
    public Iterable<AuthTokenCacheEntry> load(Account.Id accountId) throws Exception {
      try (TraceTimer timer =
          TraceContext.newTimer(
              "Loading authentication tokens for account with username",
              Metadata.builder().accountId(accountId.get()).build())) {

        List<AuthTokenCacheEntry> tokens = new ArrayList<>(1);

        for (AuthToken token : versionedTokens.getTokens(accountId)) {
          tokens.add(new AuthTokenCacheEntry(accountId, token));
        }

        // Fall back to legacy HTTP password if no tokens are present.
        if (tokens.isEmpty()) {
          Optional<AuthTokenCacheEntry> legacyHttpPassword = getLegacyHttpPassword(accountId);
          if (legacyHttpPassword.isPresent()) {
            tokens.add(legacyHttpPassword.get());
          } else {
            return NO_TOKENS;
          }
        }

        return Collections.unmodifiableList(tokens);
      }
    }

    @Deprecated
    private Optional<AuthTokenCacheEntry> getLegacyHttpPassword(Account.Id accountId) {
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
        return Optional.of(
            new AuthTokenCacheEntry(user.accountId(), AuthToken.create("legacy", password)));
      }
      return Optional.empty();
    }
  }
}
