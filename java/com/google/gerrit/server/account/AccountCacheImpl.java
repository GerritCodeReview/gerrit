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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
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

  @Inject
  AccountCacheImpl(
      AllUsersName allUsersName,
      ExternalIds externalIds,
      @Named(BYID_NAME) LoadingCache<Account.Id, Optional<AccountState>> byId) {
    this.allUsersName = allUsersName;
    this.externalIds = externalIds;
    this.byId = byId;
  }

  @Override
  public AccountState getEvenIfMissing(Account.Id accountId) {
    try {
      return byId.get(accountId).orElse(missing(accountId));
    } catch (ExecutionException e) {
      log.warn("Cannot load AccountState for " + accountId, e);
      return missing(accountId);
    }
  }

  @Override
  public Optional<AccountState> get(Account.Id accountId) {
    try {
      return byId.get(accountId);
    } catch (ExecutionException e) {
      log.warn("Cannot load AccountState for ID " + accountId, e);
      return null;
    }
  }

  @Override
  public Optional<AccountState> getByUsername(String username) {
    try {
      ExternalId extId = externalIds.get(ExternalId.Key.create(SCHEME_USERNAME, username));
      if (extId == null) {
        return Optional.empty();
      }
      return get(extId.accountId());
    } catch (IOException | ConfigInvalidException e) {
      log.warn("Cannot load AccountState for username " + username, e);
      return null;
    }
  }

  @Override
  public void evict(@Nullable Account.Id accountId) {
    if (accountId != null) {
      byId.invalidate(accountId);
    }
  }

  @Override
  public void evictAll() {
    byId.invalidateAll();
  }

  private AccountState missing(Account.Id accountId) {
    Account account = new Account(accountId, TimeUtil.nowTs());
    account.setActive(false);
    return AccountState.forAccount(allUsersName, account);
  }

  static class ByIdLoader extends CacheLoader<Account.Id, Optional<AccountState>> {
    private final Accounts accounts;

    @Inject
    ByIdLoader(Accounts accounts) {
      this.accounts = accounts;
    }

    @Override
    public Optional<AccountState> load(Account.Id who) throws Exception {
      return accounts.get(who);
    }
  }
}
