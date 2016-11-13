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

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.WatchConfig.ProjectWatchKey;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.account.AccountIndexCollection;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
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
        cache(BYID_NAME, Account.Id.class, AccountState.class).loader(ByIdLoader.class);

        cache(BYUSER_NAME, String.class, new TypeLiteral<Optional<Account.Id>>() {})
            .loader(ByNameLoader.class);

        bind(AccountCacheImpl.class);
        bind(AccountCache.class).to(AccountCacheImpl.class);
      }
    };
  }

  private final LoadingCache<Account.Id, AccountState> byId;
  private final LoadingCache<String, Optional<Account.Id>> byName;
  private final Provider<AccountIndexer> indexer;

  @Inject
  AccountCacheImpl(
      @Named(BYID_NAME) LoadingCache<Account.Id, AccountState> byId,
      @Named(BYUSER_NAME) LoadingCache<String, Optional<Account.Id>> byUsername,
      Provider<AccountIndexer> indexer) {
    this.byId = byId;
    this.byName = byUsername;
    this.indexer = indexer;
  }

  @Override
  public AccountState get(Account.Id accountId) {
    try {
      return byId.get(accountId);
    } catch (ExecutionException e) {
      log.warn("Cannot load AccountState for " + accountId, e);
      return missing(accountId);
    }
  }

  @Override
  public AccountState getIfPresent(Account.Id accountId) {
    return byId.getIfPresent(accountId);
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

  @Override
  public void evict(Account.Id accountId) throws IOException {
    if (accountId != null) {
      byId.invalidate(accountId);
      indexer.get().index(accountId);
    }
  }

  @Override
  public void evictAll() throws IOException {
    byId.invalidateAll();
    for (Account.Id accountId : byId.asMap().keySet()) {
      indexer.get().index(accountId);
    }
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
    Collection<AccountExternalId> ids = Collections.emptySet();
    Set<AccountGroup.UUID> anon = ImmutableSet.of();
    return new AccountState(account, anon, ids, new HashMap<ProjectWatchKey, Set<NotifyType>>());
  }

  static class ByIdLoader extends CacheLoader<Account.Id, AccountState> {
    private final SchemaFactory<ReviewDb> schema;
    private final GroupCache groupCache;
    private final GeneralPreferencesLoader loader;
    private final LoadingCache<String, Optional<Account.Id>> byName;
    private final boolean readFromGit;
    private final Provider<WatchConfig.Accessor> watchConfig;

    @Inject
    ByIdLoader(
        SchemaFactory<ReviewDb> sf,
        GroupCache groupCache,
        GeneralPreferencesLoader loader,
        @Named(BYUSER_NAME) LoadingCache<String, Optional<Account.Id>> byUsername,
        @GerritServerConfig Config cfg,
        Provider<WatchConfig.Accessor> watchConfig) {
      this.schema = sf;
      this.groupCache = groupCache;
      this.loader = loader;
      this.byName = byUsername;
      this.readFromGit = cfg.getBoolean("user", null, "readProjectWatchesFromGit", false);
      this.watchConfig = watchConfig;
    }

    @Override
    public AccountState load(Account.Id key) throws Exception {
      try (ReviewDb db = schema.open()) {
        final AccountState state = load(db, key);
        String user = state.getUserName();
        if (user != null) {
          byName.put(user, Optional.of(state.getAccount().getId()));
        }
        return state;
      }
    }

    private AccountState load(final ReviewDb db, final Account.Id who)
        throws OrmException, IOException, ConfigInvalidException {
      Account account = db.accounts().get(who);
      if (account == null) {
        // Account no longer exists? They are anonymous.
        return missing(who);
      }

      Collection<AccountExternalId> externalIds =
          Collections.unmodifiableCollection(db.accountExternalIds().byAccount(who).toList());

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

      Map<ProjectWatchKey, Set<NotifyType>> projectWatches =
          readFromGit
              ? watchConfig.get().getProjectWatches(who)
              : GetWatchedProjects.readProjectWatchesFromDb(db, who);

      return new AccountState(account, internalGroups, externalIds, projectWatches);
    }
  }

  static class ByNameLoader extends CacheLoader<String, Optional<Account.Id>> {
    private final SchemaFactory<ReviewDb> schema;
    private final AccountIndexCollection accountIndexes;
    private final Provider<InternalAccountQuery> accountQueryProvider;

    @Inject
    ByNameLoader(
        SchemaFactory<ReviewDb> sf,
        AccountIndexCollection accountIndexes,
        Provider<InternalAccountQuery> accountQueryProvider) {
      this.schema = sf;
      this.accountIndexes = accountIndexes;
      this.accountQueryProvider = accountQueryProvider;
    }

    @Override
    public Optional<Account.Id> load(String username) throws Exception {
      AccountExternalId.Key key =
          new AccountExternalId.Key( //
              AccountExternalId.SCHEME_USERNAME, //
              username);
      if (accountIndexes.getSearchIndex() != null) {
        AccountState accountState = accountQueryProvider.get().oneByExternalId(key.get());
        return Optional.ofNullable(accountState).map(s -> s.getAccount().getId());
      }

      try (ReviewDb db = schema.open()) {
        return Optional.ofNullable(db.accountExternalIds().get(key))
            .map(AccountExternalId::getAccountId);
      }
    }
  }
}
