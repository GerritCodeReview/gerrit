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

import com.google.common.base.Optional;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/** Caches important (but small) account state to avoid database hits. */
@Singleton
public class AccountCacheImpl implements AccountCache {
  private static final Logger log = LoggerFactory
      .getLogger(AccountCacheImpl.class);

  private static final String BYID_NAME = "accounts";
  private static final String BYUSER_NAME = "accounts_byname";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(BYID_NAME, Account.Id.class, AccountState.class)
          .loader(ByIdLoader.class);

        cache(BYUSER_NAME,
            String.class,
            new TypeLiteral<Optional<Account.Id>>() {})
          .loader(ByNameLoader.class);

        bind(AccountCacheImpl.class);
        bind(AccountCache.class).to(AccountCacheImpl.class);
      }
    };
  }

  private final LoadingCache<Account.Id, AccountState> byId;
  private final LoadingCache<String, Optional<Account.Id>> byName;

  @Inject
  AccountCacheImpl(@Named(BYID_NAME) LoadingCache<Account.Id, AccountState> byId,
      @Named(BYUSER_NAME) LoadingCache<String, Optional<Account.Id>> byUsername) {
    this.byId = byId;
    this.byName = byUsername;
  }

  public AccountState get(Account.Id accountId) {
    try {
      return byId.get(accountId);
    } catch (ExecutionException e) {
      log.warn("Cannot load AccountState for " + accountId, e);
      return missing(accountId);
    }
  }

  @Override
  public AccountState getByUsername(String username) {
    try {
      Optional<Account.Id> id = byName.get(username);
      return id != null && id.isPresent() ? byId.get(id.get()) : null;
    } catch (ExecutionException e) {
      log.warn("Cannot load AccountState for " + username, e);
      return null;
    }
  }

  public void evict(Account.Id accountId) {
    if (accountId != null) {
      byId.invalidate(accountId);
    }
  }

  public void evictByUsername(String username) {
    if (username != null) {
      byName.invalidate(username);
    }
  }

  private static AccountState missing(Account.Id accountId) {
    Account account = new Account(accountId);
    Collection<AccountExternalId> ids = Collections.emptySet();
    Set<AccountGroup.UUID> anon = ImmutableSet.of(AccountGroup.ANONYMOUS_USERS);
    return new AccountState(account, anon, ids);
  }

  static class ByIdLoader extends CacheLoader<Account.Id, AccountState> {
    private final SchemaFactory<ReviewDb> schema;
    private final GroupCache groupCache;
    private final LoadingCache<String, Optional<Account.Id>> byName;

    @Inject
    ByIdLoader(SchemaFactory<ReviewDb> sf, GroupCache groupCache,
        @Named(BYUSER_NAME) LoadingCache<String, Optional<Account.Id>> byUsername) {
      this.schema = sf;
      this.groupCache = groupCache;
      this.byName = byUsername;
    }

    @Override
    public AccountState load(Account.Id key) throws Exception {
      final ReviewDb db = schema.open();
      try {
        final AccountState state = load(db, key);
        String user = state.getUserName();
        if (user != null) {
          byName.put(user, Optional.of(state.getAccount().getId()));
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

      Set<AccountGroup.UUID> internalGroups = new HashSet<AccountGroup.UUID>();
      for (AccountGroupMember g : db.accountGroupMembers().byAccount(who)) {
        final AccountGroup.Id groupId = g.getAccountGroupId();
        final AccountGroup group = groupCache.get(groupId);
        if (group != null && group.getType() == AccountGroup.Type.INTERNAL) {
          internalGroups.add(group.getGroupUUID());
        }
      }

      internalGroups.add(AccountGroup.REGISTERED_USERS);
      internalGroups.add(AccountGroup.ANONYMOUS_USERS);
      internalGroups = Collections.unmodifiableSet(internalGroups);

      return new AccountState(account, internalGroups, externalIds);
    }
  }

  static class ByNameLoader extends CacheLoader<String, Optional<Account.Id>> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByNameLoader(final SchemaFactory<ReviewDb> sf) {
      this.schema = sf;
    }

    @Override
    public Optional<Account.Id> load(String username) throws Exception {
      final ReviewDb db = schema.open();
      try {
        final AccountExternalId.Key key = new AccountExternalId.Key( //
            AccountExternalId.SCHEME_USERNAME, //
            username);
        final AccountExternalId id = db.accountExternalIds().get(key);
        if (id != null) {
          return Optional.of(id.getAccountId());
        }
        return Optional.absent();
      } finally {
        db.close();
      }
    }
  }
}
