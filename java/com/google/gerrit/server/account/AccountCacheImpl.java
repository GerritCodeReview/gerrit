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
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.FanOutExecutor;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Caches important (but small) account state to avoid database hits. */
@Singleton
public class AccountCacheImpl implements AccountCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String BYID_NAME = "accounts";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(BYID_NAME, Account.Id.class, new TypeLiteral<AccountState>() {})
            .loader(ByIdLoader.class);

        bind(AccountCacheImpl.class);
        bind(AccountCache.class).to(AccountCacheImpl.class);
      }
    };
  }

  private final ExternalIds externalIds;
  private final LoadingCache<Account.Id, AccountState> byId;
  private final ExecutorService executor;

  @Inject
  AccountCacheImpl(
      ExternalIds externalIds,
      @Named(BYID_NAME) LoadingCache<Account.Id, AccountState> byId,
      @FanOutExecutor ExecutorService executor) {
    this.externalIds = externalIds;
    this.byId = byId;
    this.executor = executor;
  }

  @Override
  public AccountState getEvenIfMissing(Account.Id accountId) {
    try {
      return byId.get(accountId);
    } catch (ExecutionException e) {
      if (!(e.getCause() instanceof AccountNotFoundException)) {
        logger.atWarning().withCause(e).log("Cannot load AccountState for %s", accountId);
      }
      return missing(accountId);
    }
  }

  @Override
  public Optional<AccountState> get(Account.Id accountId) {
    try {
      return Optional.ofNullable(byId.get(accountId));
    } catch (ExecutionException e) {
      if (!(e.getCause() instanceof AccountNotFoundException)) {
        logger.atWarning().withCause(e).log("Cannot load AccountState for %s", accountId);
      }
      return Optional.empty();
    }
  }

  @Override
  public Map<Account.Id, AccountState> get(Set<Account.Id> accountIds) {
    Map<Account.Id, AccountState> accountStates = new HashMap<>(accountIds.size());
    List<Callable<Optional<AccountState>>> callables = new ArrayList<>();
    for (Account.Id accountId : accountIds) {
      AccountState state = byId.getIfPresent(accountId);
      if (state != null) {
        // The value is in-memory, so we just get the state
        accountStates.put(accountId, state);
      } else {
        // Queue up a callable so that we can load accounts in parallel
        callables.add(() -> get(accountId));
      }
    }
    if (callables.isEmpty()) {
      return accountStates;
    }

    List<Future<Optional<AccountState>>> futures;
    try {
      futures = executor.invokeAll(callables);
    } catch (InterruptedException e) {
      logger.atSevere().withCause(e).log("Cannot load AccountStates");
      return ImmutableMap.of();
    }
    for (Future<Optional<AccountState>> f : futures) {
      try {
        f.get().ifPresent(s -> accountStates.put(s.account().id(), s));
      } catch (InterruptedException | ExecutionException e) {
        logger.atSevere().withCause(e).log("Cannot load AccountState");
      }
    }
    return accountStates;
  }

  @Override
  public Optional<AccountState> getByUsername(String username) {
    try {
      return externalIds
          .get(ExternalId.Key.create(SCHEME_USERNAME, username))
          .map(e -> get(e.accountId()))
          .orElseGet(Optional::empty);
    } catch (IOException | ConfigInvalidException e) {
      logger.atWarning().withCause(e).log("Cannot load AccountState for username %s", username);
      return Optional.empty();
    }
  }

  @Override
  public void evict(@Nullable Account.Id accountId) {
    if (accountId != null) {
      logger.atFine().log("Evict account %d", accountId.get());
      byId.invalidate(accountId);
    }
  }

  @Override
  public void evictAll() {
    logger.atFine().log("Evict all accounts");
    byId.invalidateAll();
  }

  private AccountState missing(Account.Id accountId) {
    Account.Builder account = Account.builder(accountId, TimeUtil.nowTs());
    account.setActive(false);
    return AccountState.forAccount(account.build());
  }

  static class ByIdLoader extends CacheLoader<Account.Id, AccountState> {
    private final Accounts accounts;

    @Inject
    ByIdLoader(Accounts accounts) {
      this.accounts = accounts;
    }

    @Override
    public AccountState load(Account.Id who) throws Exception {
      try (TraceTimer timer =
          TraceContext.newTimer(
              "Loading account", Metadata.builder().accountId(who.get()).build())) {
        return accounts
            .get(who)
            .orElseThrow(() -> new AccountNotFoundException(who + " not found"));
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
