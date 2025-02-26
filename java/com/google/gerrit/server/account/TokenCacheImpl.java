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
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.account.externalids.ExternalIds;
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

public class TokenCacheImpl implements TokenCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String CACHE_NAME = "tokens";
  static final Iterable<TokenCacheEntry> NO_SUCH_USER = none();
  static final Iterable<TokenCacheEntry> NO_TOKENS = none();

  private final LoadingCache<String, Iterable<TokenCacheEntry>> cache;

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(CACHE_NAME, String.class, new TypeLiteral<Iterable<TokenCacheEntry>>() {})
            .loader(Loader.class);
        bind(TokenCache.class).to(TokenCacheImpl.class);
      }
    };
  }

  private static Iterable<TokenCacheEntry> none() {
    return Collections.unmodifiableCollection(Arrays.asList(new TokenCacheEntry[0]));
  }

  @Inject
  TokenCacheImpl(@Named(CACHE_NAME) LoadingCache<String, Iterable<TokenCacheEntry>> cache) {
    this.cache = cache;
  }

  @Override
  public Iterable<TokenCacheEntry> get(String username) {
    try {
      return cache.get(username);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot load authentication tokens for %s", username);
      return Collections.emptyList();
    }
  }

  @Override
  public void evict(String username) {
    if (username != null) {
      logger.atFine().log("Evict authentication token for username %s", username);
      cache.invalidate(username);
    }
  }

  static class Loader extends CacheLoader<String, Iterable<TokenCacheEntry>> {
    private final ExternalIds externalIds;
    private final ExternalIdKeyFactory externalIdKeyFactory;

    @Inject
    Loader(ExternalIds externalIds, ExternalIdKeyFactory externalIdKeyFactory) {
      this.externalIds = externalIds;
      this.externalIdKeyFactory = externalIdKeyFactory;
    }

    @Override
    public Iterable<TokenCacheEntry> load(String username) throws Exception {
      try (TraceTimer timer =
          TraceContext.newTimer(
              "Loading authentication tokens for account with username",
              Metadata.builder().username(username).build())) {
        Optional<ExternalId> optUser =
            externalIds.get(externalIdKeyFactory.create(SCHEME_USERNAME, username));
        if (!optUser.isPresent()) {
          return NO_SUCH_USER;
        }

        List<TokenCacheEntry> tokens = new ArrayList<>(1);
        ExternalId user = optUser.get();
        String password = user.password();
        if (password != null) {
          tokens.add(new TokenCacheEntry(user.accountId(), new Token(password)));
        }

        if (tokens.isEmpty()) {
          return NO_TOKENS;
        }
        return Collections.unmodifiableList(tokens);
      }
    }
  }
}
