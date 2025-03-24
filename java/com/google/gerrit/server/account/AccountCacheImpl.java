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

import static com.google.gerrit.server.account.AccountCacheImpl.AccountCacheModule.ACCOUNT_CACHE_MODULE;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;
import static com.google.inject.Scopes.SINGLETON;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.ModuleImpl;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdsNoteDbImpl;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.CachedPreferences;
import com.google.gerrit.server.config.DefaultPreferencesCache;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * Caches important (but small) account state to avoid database hits.
 *
 * <p>This class should be bounded as a Singleton. However, due to internal limitations in Google,
 * it cannot be marked as a singleton. The common installation pattern should therefore be:
 *
 * <pre>{@code
 * install(AccountCacheImpl.module());
 * install(AccountCacheImpl.bindingModule());
 * }</pre>
 */
public class AccountCacheImpl implements AccountCache {
  @ModuleImpl(name = ACCOUNT_CACHE_MODULE)
  public static class AccountCacheModule extends CacheModule {
    public static final String ACCOUNT_CACHE_MODULE = "account-cache-module";

    @Override
    protected void configure() {
      persist(BYID_AND_REV_NAME, CachedAccountDetails.Key.class, CachedAccountDetails.class)
          .version(2)
          .keySerializer(CachedAccountDetails.Key.Serializer.INSTANCE)
          .valueSerializer(CachedAccountDetails.Serializer.INSTANCE)
          .loader(Loader.class);
    }
  }

  public static class AccountCacheBindingModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(AccountCacheImpl.class).in(SINGLETON);
      bind(AccountCache.class).to(AccountCacheImpl.class).in(SINGLETON);
    }
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String BYID_AND_REV_NAME = "accounts";

  public static Module module() {
    return new AccountCacheModule();
  }

  public static Module bindingModule() {
    return new AccountCacheBindingModule();
  }

  private final ExternalIdsNoteDbImpl externalIds;
  private final LoadingCache<CachedAccountDetails.Key, CachedAccountDetails> accountDetailsCache;
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final DefaultPreferencesCache defaultPreferenceCache;
  private final ExternalIdKeyFactory externalIdKeyFactory;

  @Inject
  AccountCacheImpl(
      ExternalIdsNoteDbImpl externalIds,
      @Named(BYID_AND_REV_NAME)
          LoadingCache<CachedAccountDetails.Key, CachedAccountDetails> accountDetailsCache,
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      DefaultPreferencesCache defaultPreferenceCache,
      ExternalIdKeyFactory externalIdKeyFactory) {
    this.externalIds = externalIds;
    this.accountDetailsCache = accountDetailsCache;
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.defaultPreferenceCache = defaultPreferenceCache;
    this.externalIdKeyFactory = externalIdKeyFactory;
  }

  @Override
  public AccountState getEvenIfMissing(Account.Id accountId) {
    return get(accountId).orElseGet(() -> missing(accountId));
  }

  @Override
  public Optional<AccountState> get(Account.Id accountId) {
    return Optional.ofNullable(get(Collections.singleton(accountId)).getOrDefault(accountId, null));
  }

  @Override
  public AccountState getFromMetaId(Account.Id id, ObjectId metaId) {
    try {
      CachedAccountDetails.Key key = CachedAccountDetails.Key.create(id, metaId);

      CachedAccountDetails accountDetails = accountDetailsCache.get(key);
      return AccountState.forCachedAccount(accountDetails, CachedPreferences.EMPTY, externalIds);
    } catch (IOException | ExecutionException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public Map<Account.Id, AccountState> get(Set<Account.Id> accountIds) {
    try (TraceTimer ignored =
        TraceContext.newTimer(
            "Loading accounts", Metadata.builder().resourceCount(accountIds.size()).build())) {
      try (Repository allUsers = repoManager.openRepository(allUsersName)) {
        Set<CachedAccountDetails.Key> keys =
            Sets.newLinkedHashSetWithExpectedSize(accountIds.size());
        for (Account.Id id : accountIds) {
          Ref userRef = allUsers.exactRef(RefNames.refsUsers(id));
          if (userRef == null) {
            continue;
          }
          keys.add(CachedAccountDetails.Key.create(id, userRef.getObjectId()));
        }
        CachedPreferences defaultPreferences = defaultPreferenceCache.get();
        ImmutableMap.Builder<Account.Id, AccountState> result = ImmutableMap.builder();
        for (Map.Entry<CachedAccountDetails.Key, CachedAccountDetails> account :
            accountDetailsCache.getAll(keys).entrySet()) {
          result.put(
              account.getKey().accountId(),
              AccountState.forCachedAccount(account.getValue(), defaultPreferences, externalIds));
        }
        return result.build();
      }
    } catch (IOException | ExecutionException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public Optional<AccountState> getByUsername(String username) {
    try {
      return externalIds
          .get(externalIdKeyFactory.create(SCHEME_USERNAME, username))
          .map(e -> get(e.accountId()))
          .orElseGet(Optional::empty);
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Cannot load AccountState for username %s", username);
      return Optional.empty();
    }
  }

  private AccountState missing(Account.Id accountId) {
    Account.Builder account = Account.builder(accountId, TimeUtil.now());
    account.setActive(false);
    return AccountState.forAccount(account.build());
  }

  @Singleton
  static class Loader extends CacheLoader<CachedAccountDetails.Key, CachedAccountDetails> {
    private final GitRepositoryManager repoManager;
    private final AllUsersName allUsersName;

    @Inject
    Loader(GitRepositoryManager repoManager, AllUsersName allUsersName) {
      this.repoManager = repoManager;
      this.allUsersName = allUsersName;
    }

    @Override
    public CachedAccountDetails load(CachedAccountDetails.Key key) throws Exception {
      try (TraceTimer ignored =
              TraceContext.newTimer(
                  "Loading account", Metadata.builder().accountId(key.accountId().get()).build());
          Repository repo = repoManager.openRepository(allUsersName)) {
        AccountConfig cfg = new AccountConfig(key.accountId(), allUsersName, repo).load(key.id());
        Account account =
            cfg.getLoadedAccount()
                .orElseThrow(() -> new AccountNotFoundException(key.accountId() + " not found"));
        return CachedAccountDetails.create(
            account, cfg.getProjectWatches(), cfg.asCachedPreferences());
      }
    }
  }

  /** Signals that the account was not found in the primary storage. */
  private static class AccountNotFoundException extends Exception {
    private static final long serialVersionUID = 1L;

    public AccountNotFoundException(String message) {
      super(message);
    }
  }
}
