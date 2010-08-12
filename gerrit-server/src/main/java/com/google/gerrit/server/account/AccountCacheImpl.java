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

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupMember;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.EntryCreator;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.util.FutureUtil;
import com.google.gwtorm.client.Column;
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
import java.util.List;
import java.util.Set;

/** Caches important (but small) account state to avoid database hits. */
@Singleton
public class AccountCacheImpl implements AccountCache {
  private static final String BYID_NAME = "accounts";
  private static final String BYEXT_NAME = "accounts_byext";
  private static final String BYEMAIL_NAME = "accounts_byemail";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<Account.Id, AccountState>> byIdType =
            new TypeLiteral<Cache<Account.Id, AccountState>>() {};
        core(byIdType, BYID_NAME).populateWith(StateLoader.class);

        final TypeLiteral<Cache<AccountExternalId.Key, AccountExternalId>> byKeyType =
            new TypeLiteral<Cache<AccountExternalId.Key, AccountExternalId>>() {};
        core(byKeyType, BYEXT_NAME).populateWith(ExtLoader.class);

        final TypeLiteral<Cache<Email, AccountIdSet>> type =
            new TypeLiteral<Cache<Email, AccountIdSet>>() {};
        core(type, BYEMAIL_NAME).populateWith(EmailLoader.class);

        bind(AccountCacheImpl.class);
        bind(AccountCache.class).to(AccountCacheImpl.class);
      }
    };
  }

  private final Cache<Account.Id, AccountState> byId;
  private final Cache<AccountExternalId.Key, AccountExternalId> byExt;
  private final Cache<Email, AccountIdSet> byEmail;

  @Inject
  AccountCacheImpl(@Named(BYID_NAME) Cache<Account.Id, AccountState> byId,
      @Named(BYEXT_NAME) Cache<AccountExternalId.Key, AccountExternalId> byExt,
      @Named(BYEMAIL_NAME) final Cache<Email, AccountIdSet> byEmail) {
    this.byId = byId;
    this.byExt = byExt;
    this.byEmail = byEmail;
  }

  public ListenableFuture<AccountState> get(Account.Id accountId) {
    return byId.get(accountId);
  }

  public ListenableFuture<Account> getAccount(Account.Id accountId) {
    return Futures.compose(get(accountId), AccountState.GET_ACCOUNT);
  }

  public ListenableFuture<Void> evictAsync(Account.Id accountId) {
    return byId.removeAsync(accountId);
  }

  @Override
  public ListenableFuture<AccountExternalId> get(AccountExternalId.Key key) {
    return byExt.get(key);
  }

  @Override
  public ListenableFuture<Void> evictAsync(AccountExternalId.Key key) {
    return byExt.removeAsync(key);
  }

  @Override
  public ListenableFuture<Set<Account.Id>> byEmail(String email) {
    if (email == null) {
      return Futures.immediateFuture(Collections.<Account.Id> emptySet());
    }
    return Futures.compose(byEmail.get(new Email(email)), AccountIdSet.unpack);
  }

  @Override
  public ListenableFuture<Void> evictEmailAsync(String email) {
    if (email == null) {
      return Futures.immediateFuture(null);
    }
    return byEmail.removeAsync(new Email(email));
  }

  static class StateLoader extends EntryCreator<Account.Id, AccountState> {
    private final SchemaFactory<ReviewDb> schema;
    private final Set<AccountGroup.Id> registered;
    private final Set<AccountGroup.Id> anonymous;
    private final GroupCache groupCache;

    @Inject
    StateLoader(SchemaFactory<ReviewDb> sf, AuthConfig auth,
        GroupCache groupCache) {
      this.schema = sf;
      this.registered = auth.getRegisteredGroups();
      this.anonymous = auth.getAnonymousGroups();
      this.groupCache = groupCache;
    }

    @Override
    public AccountState createEntry(final Account.Id key) throws Exception {
      final ReviewDb db = schema.open();
      try {
        return load(db, key);
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

      List<ListenableFuture<AccountGroup>> myGroups = Lists.newArrayList();
      for (AccountGroupMember g : db.accountGroupMembers().byAccount(who)) {
        myGroups.add(groupCache.get(g.getAccountGroupId()));
      }

      Set<AccountGroup.Id> internalGroups = new HashSet<AccountGroup.Id>();
      for (ListenableFuture<AccountGroup> f : myGroups) {
        AccountGroup group = FutureUtil.get(f);
        if (group.getType() == AccountGroup.Type.INTERNAL) {
          internalGroups.add(group.getId());
        }
      }

      if (internalGroups.isEmpty()) {
        internalGroups = registered;
      } else {
        internalGroups.addAll(registered);
        internalGroups = Collections.unmodifiableSet(internalGroups);
      }

      final Collection<AccountExternalId> externalIds =
          Collections.unmodifiableCollection(db.accountExternalIds() //
              .byAccount(who).toList());
      return new AccountState(account, internalGroups, externalIds);
    }

    @Override
    public AccountState missing(final Account.Id accountId) {
      final Account account = new Account(accountId);
      final Collection<AccountExternalId> ids = Collections.emptySet();
      return new AccountState(account, anonymous, ids);
    }
  }

  static class ExtLoader extends
      EntryCreator<AccountExternalId.Key, AccountExternalId> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ExtLoader(SchemaFactory<ReviewDb> schema) {
      this.schema = schema;
    }

    @Override
    public AccountExternalId createEntry(AccountExternalId.Key key)
        throws Exception {
      final ReviewDb db = schema.open();
      try {
        return db.accountExternalIds().get(key);
      } finally {
        db.close();
      }
    }
  }

  static class Email {
    @Column(id = 1)
    String email;

    Email() {
    }

    Email(String email) {
      this.email = email;
    }
  }

  static class AccountIdSet {
    static final Function<AccountIdSet, Set<Account.Id>> unpack =
        new Function<AccountIdSet, Set<Account.Id>>() {
          @Override
          public Set<Account.Id> apply(AccountIdSet from) {
            return from.ids;
          }
        };

    @Column(id = 1)
    Set<Account.Id> ids;

    AccountIdSet() {
    }

    AccountIdSet(Set<Account.Id> ids) {
      this.ids = ids;
    }
  }

  static class EmailLoader extends EntryCreator<Email, AccountIdSet> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    EmailLoader(final SchemaFactory<ReviewDb> schema) {
      this.schema = schema;
    }

    @Override
    public AccountIdSet createEntry(Email key) throws Exception {
      final ReviewDb db = schema.open();
      try {
        Set<Account.Id> res = Sets.newHashSet();
        for (AccountExternalId extId : db.accountExternalIds().byEmailAddress(
            key.email)) {
          res.add(extId.getAccountId());
        }
        return new AccountIdSet(Collections.unmodifiableSet(res));
      } finally {
        db.close();
      }
    }

    @Override
    public AccountIdSet missing(Email key) {
      Set<Account.Id> res = Collections.emptySet();
      return new AccountIdSet(res);
    }
  }
}
