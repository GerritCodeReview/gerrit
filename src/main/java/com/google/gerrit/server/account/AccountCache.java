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
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.AccountGroupMember;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.SelfPopulatingCache;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Caches important (but small) account state to avoid database hits. */
@Singleton
public class AccountCache {
  private static final String CACHE_NAME = "accounts";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<Account.Id, AccountState>> type =
            new TypeLiteral<Cache<Account.Id, AccountState>>() {};
        core(type, CACHE_NAME);
        bind(AccountCache.class);
      }
    };
  }

  private final SchemaFactory<ReviewDb> schema;
  private final AuthConfig authConfig;
  private final SelfPopulatingCache<Account.Id, AccountState> self;

  private final Set<AccountGroup.Id> registered;
  private final Set<AccountGroup.Id> anonymous;

  @Inject
  AccountCache(final SchemaFactory<ReviewDb> sf, final SystemConfig cfg,
      final AuthConfig ac,
      @Named(CACHE_NAME) final Cache<Account.Id, AccountState> rawCache) {
    schema = sf;
    authConfig = ac;

    final HashSet<AccountGroup.Id> r = new HashSet<AccountGroup.Id>(2);
    r.add(cfg.anonymousGroupId);
    r.add(cfg.registeredGroupId);
    registered = Collections.unmodifiableSet(r);
    anonymous = Collections.singleton(cfg.anonymousGroupId);

    self = new SelfPopulatingCache<Account.Id, AccountState>(rawCache) {
      @Override
      protected AccountState createEntry(Account.Id key) throws Exception {
        return lookup(key);
      }

      @Override
      protected AccountState missing(final Account.Id key) {
        return missingAccount(key);
      }
    };
  }

  private AccountState lookup(final Account.Id who) throws OrmException {
    final ReviewDb db = schema.open();
    try {
      final Account account = db.accounts().get(who);
      if (account == null) {
        // Account no longer exists? They are anonymous.
        //
        return missingAccount(who);
      }

      final List<AccountExternalId> ids =
          db.accountExternalIds().byAccount(who).toList();
      Set<String> emails = new HashSet<String>();
      for (AccountExternalId id : ids) {
        if (id.getEmailAddress() != null && !id.getEmailAddress().isEmpty()) {
          emails.add(id.getEmailAddress());
        }
      }

      Set<AccountGroup.Id> actual = new HashSet<AccountGroup.Id>();
      for (AccountGroupMember g : db.accountGroupMembers().byAccount(who)) {
        actual.add(g.getAccountGroupId());
      }

      if (actual.isEmpty()) {
        actual = registered;
      } else {
        actual.addAll(registered);
        actual = Collections.unmodifiableSet(actual);
      }

      final Set<AccountGroup.Id> effective;
      if (authConfig.isIdentityTrustable(ids)) {
        effective = actual;
      } else {
        effective = registered;
      }

      return new AccountState(account, actual, effective, emails);
    } finally {
      db.close();
    }
  }

  private AccountState missingAccount(final Account.Id accountId) {
    final Account account = new Account(accountId);
    final Set<String> emails = Collections.emptySet();
    return new AccountState(account, anonymous, anonymous, emails);
  }

  public AccountState get(final Account.Id accountId) {
    return self.get(accountId);
  }

  public void evict(final Account.Id accountId) {
    self.remove(accountId);
  }
}
