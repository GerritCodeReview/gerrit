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
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupMember;
import com.google.gerrit.reviewdb.ReviewDb;
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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Caches important (but small) account state to avoid database hits. */
@Singleton
public class AccountCacheImpl implements AccountCache {
  private static final String CACHE_NAME = "accounts";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<Object, AccountState>> type =
            new TypeLiteral<Cache<Object, AccountState>>() {};
        core(type, CACHE_NAME);
        bind(AccountCacheImpl.class);
        bind(AccountCache.class).to(AccountCacheImpl.class);
      }
    };
  }

  private final SchemaFactory<ReviewDb> schema;
  private final GroupCache groupCache;
  private final SelfPopulatingCache<Account.Id, AccountState> byId;
  private final SelfPopulatingCache<String, AccountState> byName;

  private final Set<AccountGroup.Id> registered;
  private final Set<AccountGroup.Id> anonymous;

  @Inject
  AccountCacheImpl(final SchemaFactory<ReviewDb> sf, final AuthConfig auth,
      final GroupCache groupCache,
      @Named(CACHE_NAME) final Cache<Object, AccountState> rawCache) {
    schema = sf;
    registered = auth.getRegisteredGroups();
    anonymous = auth.getAnonymousGroups();
    this.groupCache = groupCache;

    byId = new SelfPopulatingCache<Account.Id, AccountState>((Cache) rawCache) {
      @Override
      protected AccountState createEntry(Account.Id key) throws Exception {
        return lookup(key);
      }

      @Override
      protected AccountState missing(final Account.Id key) {
        return missingAccount(key);
      }
    };

    byName = new SelfPopulatingCache<String, AccountState>((Cache) rawCache) {
      @Override
      protected AccountState createEntry(String username) throws Exception {
        return lookup(username);
      }
    };
  }

  private AccountState lookup(final Account.Id who) throws OrmException {
    final ReviewDb db = schema.open();
    try {
      return load(db, who);
    } finally {
      db.close();
    }
  }

  private AccountState lookup(final String username) throws OrmException {
    final ReviewDb db = schema.open();
    try {
      AccountExternalId.Key key =
          new AccountExternalId.Key(AccountExternalId.SCHEME_USERNAME, username);
      AccountExternalId id = db.accountExternalIds().get(key);
      if (id == null) {
        return null;
      }
      return load(db, id.getAccountId());
    } finally {
      db.close();
    }
  }

  private AccountState load(final ReviewDb db, final Account.Id who)
      throws OrmException {
    final Account account = db.accounts().get(who);
    if (account == null) {
      // Account no longer exists? They are anonymous.
      //
      return missingAccount(who);
    }

    final Collection<AccountExternalId> externalIds =
        Collections.unmodifiableCollection(db.accountExternalIds().byAccount(
            who).toList());

    Set<AccountGroup.Id> internalGroups = new HashSet<AccountGroup.Id>();
    for (AccountGroupMember g : db.accountGroupMembers().byAccount(who)) {
      final AccountGroup.Id groupId = g.getAccountGroupId();
      final AccountGroup group = groupCache.get(groupId);
      if (group != null && group.getType() == AccountGroup.Type.INTERNAL) {
        internalGroups.add(groupId);
      }
    }

    if (internalGroups.isEmpty()) {
      internalGroups = registered;
    } else {
      internalGroups.addAll(registered);
      internalGroups = Collections.unmodifiableSet(internalGroups);
    }

    return new AccountState(account, internalGroups, externalIds);
  }

  private AccountState missingAccount(final Account.Id accountId) {
    final Account account = new Account(accountId);
    final Collection<AccountExternalId> ids = Collections.emptySet();
    return new AccountState(account, anonymous, ids);
  }

  public AccountState get(final Account.Id accountId) {
    return byId.get(accountId);
  }

  @Override
  public AccountState getByUsername(String username) {
    return byName.get(username);
  }

  public void evict(final Account.Id accountId) {
    byId.remove(accountId);
  }

  public void evictByUsername(String username) {
    byName.remove(username);
  }
}
