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

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.EntryCreator;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

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
        final TypeLiteral<Cache<Account.Email, AccountIdSet>> type =
            new TypeLiteral<Cache<Account.Email, AccountIdSet>>() {};
        core(type, CACHE_NAME).populateWith(Loader.class);
        bind(AccountByEmailCacheImpl.class);
        bind(AccountByEmailCache.class).to(AccountByEmailCacheImpl.class);
      }
    };
  }

  private final Cache<Account.Email, AccountIdSet> cache;

  @Inject
  AccountByEmailCacheImpl(
      @Named(CACHE_NAME) final Cache<Account.Email, AccountIdSet> cache) {
    this.cache = cache;
  }

  public AccountIdSet get(final String email) {
    return cache.get(new Account.Email(email));
  }

  public void evict(final String email) {
    cache.remove(new Account.Email(email));
  }

  static class Loader extends EntryCreator<Account.Email, AccountIdSet> {
    private final SchemaFactory<ReviewDb> schema;
    private final AccountExternalIdCache accountExternalIdCache;

    @Inject
    Loader(final SchemaFactory<ReviewDb> schema,
        final AccountExternalIdCache accountExternalIdCache) {
      this.schema = schema;
      this.accountExternalIdCache = accountExternalIdCache;
    }

    @Override
    public AccountIdSet createEntry(final Account.Email email) throws Exception {
      final ReviewDb db = schema.open();
      try {
        final HashSet<Account.Id> r = new HashSet<Account.Id>();
        for (Account a : db.accounts().byPreferredEmail(email.get())) {
          r.add(a.getId());
        }
        for (AccountExternalId a : accountExternalIdCache.byEmailAddress(email
            .get())) {
          r.add(a.getAccountId());
        }
        return pack(r);
      } finally {
        db.close();
      }
    }

    @Override
    public AccountIdSet missing(final Account.Email key) {
      return AccountIdSet.EMPTY_SET;
    }

    private static AccountIdSet pack(final Set<Account.Id> c) {
      switch (c.size()) {
        case 0:
          return AccountIdSet.EMPTY_SET;
        case 1:
          return new AccountIdSet(c.iterator().next());
        default:
          return new AccountIdSet(new HashSet<Account.Id>(c));
      }
    }
  }
}
