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

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.WatchConfig.NotifyType;
import com.google.gerrit.server.account.WatchConfig.ProjectWatchKey;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Caches important (but small) account state to avoid database hits. */
@Singleton
public class AccountCacheImpl implements AccountCache {
  private static final Logger log = LoggerFactory.getLogger(AccountCacheImpl.class);

  private static final String BYID_NAME = "accounts";
  private static final String BYUSER_NAME = "accounts_byname";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(BYID_NAME, Account.Id.class, new TypeLiteral<Optional<AccountState>>() {})
            .loader(ByIdLoader.class);

        cache(BYUSER_NAME, String.class, new TypeLiteral<Optional<Account.Id>>() {})
            .loader(ByNameLoader.class);

        bind(AccountCacheImpl.class);
        bind(AccountCache.class).to(AccountCacheImpl.class);
      }
    };
  }

  private final LoadingCache<Account.Id, Optional<AccountState>> byId;
  private final LoadingCache<String, Optional<Account.Id>> byName;
  private final Provider<AccountIndexer> indexer;

  @Inject
  AccountCacheImpl(
      @Named(BYID_NAME) LoadingCache<Account.Id, Optional<AccountState>> byId,
      @Named(BYUSER_NAME) LoadingCache<String, Optional<Account.Id>> byUsername,
      Provider<AccountIndexer> indexer) {
    this.byId = byId;
    this.byName = byUsername;
    this.indexer = indexer;
  }

  @Override
  public AccountState get(Account.Id accountId) {
    try {
      return byId.get(accountId).orElse(missing(accountId));
    } catch (ExecutionException e) {
      log.warn("Cannot load AccountState for " + accountId, e);
      return missing(accountId);
    }
  }

  @Override
  @Nullable
  public AccountState getOrNull(Account.Id accountId) {
    try {
      return byId.get(accountId).orElse(null);
    } catch (ExecutionException e) {
      log.warn("Cannot load AccountState for " + accountId, e);
      return null;
    }
  }

  @Override
  public AccountState getByUsername(String username) {
    try {
      Optional<Account.Id> id = byName.get(username);
      return id != null && id.isPresent() ? getOrNull(id.get()) : null;
    } catch (ExecutionException e) {
      log.warn("Cannot load AccountState for " + username, e);
      return null;
    }
  }

  @Override
  public void evict(Account.Id accountId) throws IOException {
    if (accountId != null) {
      byId.invalidate(accountId);
      indexer.get().index(accountId);
    }
  }

  @Override
  public void evictAllNoReindex() {
    byId.invalidateAll();
  }

  @Override
  public void evictByUsername(String username) {
    if (username != null) {
      byName.invalidate(username);
    }
  }

  private static AccountState missing(Account.Id accountId) {
    Account account = new Account(accountId, TimeUtil.nowTs());
    account.setActive(false);
    Set<AccountGroup.UUID> anon = ImmutableSet.of();
    return new AccountState(
        account, anon, Collections.emptySet(), new HashMap<ProjectWatchKey, Set<NotifyType>>());
  }

  static class ByIdLoader extends CacheLoader<Account.Id, Optional<AccountState>> {
    private final SchemaFactory<ReviewDb> schema;
    private final Accounts accounts;
    private final GroupCache groupCache;
    private final GeneralPreferencesLoader loader;
    private final LoadingCache<String, Optional<Account.Id>> byName;
    private final Provider<WatchConfig.Accessor> watchConfig;
    private final ExternalIds externalIds;

    @Inject
    ByIdLoader(
        SchemaFactory<ReviewDb> sf,
        Accounts accounts,
        GroupCache groupCache,
        GeneralPreferencesLoader loader,
        @Named(BYUSER_NAME) LoadingCache<String, Optional<Account.Id>> byUsername,
        Provider<WatchConfig.Accessor> watchConfig,
        ExternalIds externalIds) {
      this.accounts = accounts;
      this.schema = sf;
      this.groupCache = groupCache;
      this.loader = loader;
      this.byName = byUsername;
      this.watchConfig = watchConfig;
      this.externalIds = externalIds;
    }

    @Override
    public Optional<AccountState> load(Account.Id key) throws Exception {
      try (ReviewDb db = schema.open()) {
        Optional<AccountState> state = load(db, key);
        if (!state.isPresent()) {
          return state;
        }
        String user = state.get().getUserName();
        if (user != null) {
          byName.put(user, Optional.of(state.get().getAccount().getId()));
        }
        return state;
      }
    }

    private Optional<AccountState> load(ReviewDb db, Account.Id who)
        throws OrmException, IOException, ConfigInvalidException {
      Account account = accounts.get(db, who);
      if (account == null) {
        return Optional.empty();
      }

      Set<AccountGroup.UUID> internalGroups = new HashSet<>();
      for (AccountGroupMember g : db.accountGroupMembers().byAccount(who)) {
        final AccountGroup.Id groupId = g.getAccountGroupId();
        final AccountGroup group = groupCache.get(groupId);
        if (group != null && group.getGroupUUID() != null) {
          internalGroups.add(group.getGroupUUID());
        }
      }
      internalGroups = Collections.unmodifiableSet(internalGroups);

      try {
        account.setGeneralPreferences(loader.load(who));
      } catch (IOException | ConfigInvalidException e) {
        log.warn("Cannot load GeneralPreferences for " + who + " (using default)", e);
        account.setGeneralPreferences(GeneralPreferencesInfo.defaults());
      }

      return Optional.of(
          new AccountState(
              account,
              internalGroups,
              externalIds.byAccount(who),
              watchConfig.get().getProjectWatches(who)));
    }
  }

  static class ByNameLoader extends CacheLoader<String, Optional<Account.Id>> {
    private final Provider<InternalAccountQuery> accountQueryProvider;

    @Inject
    ByNameLoader(Provider<InternalAccountQuery> accountQueryProvider) {
      this.accountQueryProvider = accountQueryProvider;
    }

    @Override
    public Optional<Account.Id> load(String username) throws Exception {
      AccountState accountState =
          accountQueryProvider.get().oneByExternalId(SCHEME_USERNAME, username);
      return Optional.ofNullable(accountState).map(s -> s.getAccount().getId());
    }
  }
}
