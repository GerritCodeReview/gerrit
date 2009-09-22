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

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountExternalId;
import com.google.gerrit.client.reviewdb.UserDb;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.SelfPopulatingCache;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Translates an email address to a set of matching accounts. */
@Singleton
public class AccountByEmailCacheImpl implements AccountByEmailCache {
  private static final String CACHE_NAME = "accounts_byemail";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<String, Set<Account.Id>>> type =
            new TypeLiteral<Cache<String, Set<Account.Id>>>() {};
        core(type, CACHE_NAME);
        bind(AccountByEmailCacheImpl.class);
        bind(AccountByEmailCache.class).to(AccountByEmailCacheImpl.class);
      }
    };
  }

  private final UserDb userDb;
  private final SelfPopulatingCache<String, Set<Account.Id>> self;

  @Inject
  AccountByEmailCacheImpl(final UserDb userDb,
      @Named(CACHE_NAME) final Cache<String, Set<Account.Id>> rawCache) {
    this.userDb = userDb;
    this.self = new SelfPopulatingCache<String, Set<Account.Id>>(rawCache) {
      @Override
      protected Set<Account.Id> createEntry(final String key) throws Exception {
        return lookup(key);
      }

      @Override
      protected Set<Account.Id> missing(final String key) {
        return Collections.emptySet();
      }
    };
  }

  private Set<Account.Id> lookup(final String email) throws OrmException {
    final HashSet<Account.Id> r = new HashSet<Account.Id>();
    for (Account a : userDb.byPreferredEmail(email, userDb.latestSnapshot())) {
      r.add(a.getId());
    }
    for (AccountExternalId a : userDb.byEmail(email, userDb.latestSnapshot())) {
      r.add(a.getAccountId());
    }
    return pack(r);
  }

  public Set<Account.Id> get(final String email) {
    return self.get(email);
  }

  public void evict(final String email) {
    self.remove(email);
  }

  private static Set<Account.Id> pack(final Set<Account.Id> c) {
    switch (c.size()) {
      case 0:
        return Collections.emptySet();
      case 1:
        return one(c);
      default:
        return Collections.unmodifiableSet(new HashSet<Account.Id>(c));
    }
  }

  private static <T> Set<T> one(final Set<T> c) {
    return Collections.singleton(c.iterator().next());
  }
}
