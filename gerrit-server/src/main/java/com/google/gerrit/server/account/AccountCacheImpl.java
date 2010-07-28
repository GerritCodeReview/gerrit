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
import com.google.gerrit.server.cache.EntryCreator;
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
  private static final String BYID_NAME = "accounts";
  private static final String BYUSER_NAME = "accounts_byname";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<Account.Id, AccountState>> byIdType =
            new TypeLiteral<Cache<Account.Id, AccountState>>() {};
        core(byIdType, BYID_NAME).populateWith(ByIdLoader.class);

        final TypeLiteral<Cache<Account.Username, Account.Id>> byUsernameType =
            new TypeLiteral<Cache<Account.Username, Account.Id>>() {};
        core(byUsernameType, BYUSER_NAME).populateWith(ByNameLoader.class);

        bind(AccountCacheImpl.class);
        bind(AccountCache.class).to(AccountCacheImpl.class);
      }
    };
  }

  private final Cache<Account.Id, AccountState> byId;
  private final Cache<Account.Username, Account.Id> byName;

  @Inject
  AccountCacheImpl(@Named(BYID_NAME) Cache<Account.Id, AccountState> byId,
      @Named(BYUSER_NAME) Cache<Account.Username, Account.Id> byUsername) {
    this.byId = byId;
    this.byName = byUsername;
  }

  public AccountState get(final Account.Id accountId) {
    return byId.get(accountId);
  }

  @Override
  public AccountState getByUsername(String username) {
    Account.Id id = byName.get(new Account.Username(username));
    return id != null ? byId.get(id) : null;
  }

  public void evict(final Account.Id accountId) {
    byId.remove(accountId);
  }

  public void evictByUsername(String username) {
    byName.remove(new Account.Username(username));
  }

  static class ByIdLoader extends EntryCreator<Account.Id, AccountState> {
    private final SchemaFactory<ReviewDb> schema;
    private final Set<AccountGroup.Id> registered;
    private final Set<AccountGroup.Id> anonymous;
    private final GroupCache groupCache;
    private final Cache<Account.Username, Account.Id> byName;

    @Inject
    ByIdLoader(SchemaFactory<ReviewDb> sf, AuthConfig auth,
        GroupCache groupCache,
        @Named(BYUSER_NAME) Cache<Account.Username, Account.Id> byUsername) {
      this.schema = sf;
      this.registered = auth.getRegisteredGroups();
      this.anonymous = auth.getAnonymousGroups();
      this.groupCache = groupCache;
      this.byName = byUsername;
    }

    @Override
    public AccountState createEntry(final Account.Id key) throws Exception {
      final ReviewDb db = schema.open();
      try {
        final AccountState state = load(db, key);
        if (state.getUserName() != null) {
          byName.put(new Account.Username(state.getUserName()), state.getAccount()
              .getId());
        }
        return state;
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
        return missing(who);
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

    @Override
    public AccountState missing(final Account.Id accountId) {
      final Account account = new Account(accountId);
      final Collection<AccountExternalId> ids = Collections.emptySet();
      return new AccountState(account, anonymous, ids);
    }
  }

  static class ByNameLoader extends EntryCreator<Account.Username, Account.Id> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByNameLoader(final SchemaFactory<ReviewDb> sf) {
      this.schema = sf;
    }

    @Override
    public Account.Id createEntry(final Account.Username username) throws Exception {
      final ReviewDb db = schema.open();
      try {
        final AccountExternalId.Key key = new AccountExternalId.Key( //
            AccountExternalId.SCHEME_USERNAME, //
            username.get());
        final AccountExternalId id = db.accountExternalIds().get(key);
        return id != null ? id.getAccountId() : null;
      } finally {
        db.close();
      }
    }
  }
}
