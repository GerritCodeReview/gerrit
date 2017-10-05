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
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.WatchConfig.NotifyType;
import com.google.gerrit.server.account.WatchConfig.ProjectWatchKey;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.group.Groups;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.gerrit.server.index.group.GroupField;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.gerrit.server.query.group.InternalGroupQuery;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Caches important (but small) account state to avoid database hits. */
@Singleton
public class AccountCacheImpl implements AccountCache {
  private static final Logger log = LoggerFactory.getLogger(AccountCacheImpl.class);

  private static final String BYID_NAME = "accounts";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(BYID_NAME, Account.Id.class, new TypeLiteral<Optional<AccountState>>() {})
            .loader(ByIdLoader.class);

        bind(AccountCacheImpl.class);
        bind(AccountCache.class).to(AccountCacheImpl.class);
      }
    };
  }

  private final AllUsersName allUsersName;
  private final ExternalIds externalIds;
  private final LoadingCache<Account.Id, Optional<AccountState>> byId;
  private final Provider<AccountIndexer> indexer;

  @Inject
  AccountCacheImpl(
      AllUsersName allUsersName,
      ExternalIds externalIds,
      @Named(BYID_NAME) LoadingCache<Account.Id, Optional<AccountState>> byId,
      Provider<AccountIndexer> indexer) {
    this.allUsersName = allUsersName;
    this.externalIds = externalIds;
    this.byId = byId;
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
      log.warn("Cannot load AccountState for ID " + accountId, e);
      return null;
    }
  }

  @Override
  public AccountState getByUsername(String username) {
    try {
      ExternalId extId = externalIds.get(ExternalId.Key.create(SCHEME_USERNAME, username));
      if (extId == null) {
        return null;
      }
      return getOrNull(extId.accountId());
    } catch (IOException | ConfigInvalidException e) {
      log.warn("Cannot load AccountState for username " + username, e);
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

  private AccountState missing(Account.Id accountId) {
    Account account = new Account(accountId, TimeUtil.nowTs());
    account.setActive(false);
    Set<AccountGroup.UUID> anon = ImmutableSet.of();
    return new AccountState(
        allUsersName,
        account,
        anon,
        Collections.emptySet(),
        new HashMap<ProjectWatchKey, Set<NotifyType>>());
  }

  static class ByIdLoader extends CacheLoader<Account.Id, Optional<AccountState>> {
    private final SchemaFactory<ReviewDb> schema;
    private final AllUsersName allUsersName;
    private final Accounts accounts;
    private final Provider<GroupIndex> groupIndexProvider;
    private final Provider<InternalGroupQuery> groupQueryProvider;
    private final GroupCache groupCache;
    private final GeneralPreferencesLoader loader;
    private final Provider<WatchConfig.Accessor> watchConfig;
    private final ExternalIds externalIds;

    @Inject
    ByIdLoader(
        SchemaFactory<ReviewDb> sf,
        AllUsersName allUsersName,
        Accounts accounts,
        GroupIndexCollection groupIndexCollection,
        Provider<InternalGroupQuery> groupQueryProvider,
        GroupCache groupCache,
        GeneralPreferencesLoader loader,
        Provider<WatchConfig.Accessor> watchConfig,
        ExternalIds externalIds) {
      this.schema = sf;
      this.allUsersName = allUsersName;
      this.accounts = accounts;
      this.groupIndexProvider = groupIndexCollection::getSearchIndex;
      this.groupQueryProvider = groupQueryProvider;
      this.groupCache = groupCache;
      this.loader = loader;
      this.watchConfig = watchConfig;
      this.externalIds = externalIds;
    }

    @Override
    public Optional<AccountState> load(Account.Id key) throws Exception {
      try (ReviewDb db = schema.open()) {
        return load(db, key);
      }
    }

    private Optional<AccountState> load(ReviewDb db, Account.Id who)
        throws OrmException, IOException, ConfigInvalidException {
      Account account = accounts.get(who);
      if (account == null) {
        return Optional.empty();
      }

      Set<AccountGroup.UUID> internalGroups = getGroupsWithMember(db, who);

      try {
        account.setGeneralPreferences(loader.load(who));
      } catch (IOException | ConfigInvalidException e) {
        log.warn("Cannot load GeneralPreferences for " + who + " (using default)", e);
        account.setGeneralPreferences(GeneralPreferencesInfo.defaults());
      }

      return Optional.of(
          new AccountState(
              allUsersName,
              account,
              internalGroups,
              externalIds.byAccount(who),
              watchConfig.get().getProjectWatches(who)));
    }

    private ImmutableSet<AccountGroup.UUID> getGroupsWithMember(ReviewDb db, Account.Id memberId)
        throws OrmException {
      Stream<InternalGroup> internalGroupStream;
      if (groupIndexProvider.get() != null
          && groupIndexProvider.get().getSchema().hasField(GroupField.MEMBER)) {
        internalGroupStream = groupQueryProvider.get().byMember(memberId).stream();
      } else {
        internalGroupStream =
            Groups.getGroupsWithMemberFromReviewDb(db, memberId)
                .map(groupCache::get)
                .flatMap(Streams::stream);
      }

      return internalGroupStream.map(InternalGroup::getGroupUUID).collect(toImmutableSet());
    }
  }
}
