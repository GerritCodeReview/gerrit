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

package com.google.gerrit.server.account;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Translates an email address to a set of matching accounts. */
@Singleton
public class AccountByEmailCacheImpl implements AccountByEmailCache {
  private static final Logger log = LoggerFactory.getLogger(AccountByEmailCacheImpl.class);
  private static final String CACHE_NAME = "accounts_byemail";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(CACHE_NAME, String.class, new TypeLiteral<Set<Account.Id>>() {}).loader(Loader.class);
        bind(AccountByEmailCacheImpl.class);
        bind(AccountByEmailCache.class).to(AccountByEmailCacheImpl.class);
      }
    };
  }

  private final LoadingCache<String, Set<Account.Id>> cache;

  @Inject
  AccountByEmailCacheImpl(@Named(CACHE_NAME) LoadingCache<String, Set<Account.Id>> cache) {
    this.cache = cache;
  }

  @Override
  public Set<Account.Id> get(String email) {
    try {
      return cache.get(email);
    } catch (ExecutionException e) {
      log.warn("Cannot resolve accounts by email", e);
      return Collections.emptySet();
    }
  }

  @Override
  public void evict(String email) {
    if (email != null) {
      cache.invalidate(email);
    }
  }

  static class Loader extends CacheLoader<String, Set<Account.Id>> {
    // This must be a provider to prevent a cyclic dependency within Google-internal glue code.
    private final Provider<ExternalIds> externalIds;

    @Inject
    Loader(Provider<ExternalIds> externalIds) {
      this.externalIds = externalIds;
    }

    @Override
    public Set<Account.Id> load(String email) throws Exception {
      return externalIds
          .get()
          .byEmail(email)
          .stream()
          .map(e -> e.accountId())
          .collect(toImmutableSet());
    }
  }
}
