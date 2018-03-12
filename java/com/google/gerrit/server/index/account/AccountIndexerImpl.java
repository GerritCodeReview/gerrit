// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.index.account;

import static com.google.gerrit.server.git.QueueProvider.QueueType.BATCH;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.events.AccountIndexedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.index.Index;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.IndexUtils;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Future;
import org.eclipse.jgit.lib.Config;

public class AccountIndexerImpl implements AccountIndexer {
  public interface Factory {
    AccountIndexerImpl create(AccountIndexCollection indexes);

    AccountIndexerImpl create(@Nullable AccountIndex index);
  }

  private final AccountCache byIdCache;
  private final DynamicSet<AccountIndexedListener> indexedListener;
  private final StalenessChecker stalenessChecker;
  private final ListeningExecutorService batchExecutor;
  private final boolean autoReindexIfStale;
  @Nullable private final AccountIndexCollection indexes;
  @Nullable private final AccountIndex index;

  @AssistedInject
  AccountIndexerImpl(
      AccountCache byIdCache,
      DynamicSet<AccountIndexedListener> indexedListener,
      StalenessChecker stalenessChecker,
      @IndexExecutor(BATCH) ListeningExecutorService batchExecutor,
      @GerritServerConfig Config config,
      @Assisted AccountIndexCollection indexes) {
    this.byIdCache = byIdCache;
    this.indexedListener = indexedListener;
    this.stalenessChecker = stalenessChecker;
    this.batchExecutor = batchExecutor;
    this.autoReindexIfStale = autoReindexIfStale(config);
    this.indexes = indexes;
    this.index = null;
  }

  @AssistedInject
  AccountIndexerImpl(
      AccountCache byIdCache,
      DynamicSet<AccountIndexedListener> indexedListener,
      StalenessChecker stalenessChecker,
      @IndexExecutor(BATCH) ListeningExecutorService batchExecutor,
      @GerritServerConfig Config config,
      @Assisted @Nullable AccountIndex index) {
    this.byIdCache = byIdCache;
    this.indexedListener = indexedListener;
    this.stalenessChecker = stalenessChecker;
    this.batchExecutor = batchExecutor;
    this.autoReindexIfStale = autoReindexIfStale(config);
    this.indexes = null;
    this.index = index;
  }

  @Override
  public void index(Account.Id id) throws IOException {
    for (Index<Account.Id, AccountState> i : getWriteIndexes()) {
      // Evict the cache to get an up-to-date value for sure.
      byIdCache.evict(id);
      Optional<AccountState> accountState = byIdCache.get(id);
      if (accountState.isPresent()) {
        i.replace(accountState.get());
      } else {
        i.delete(id);
      }
    }
    fireAccountIndexedEvent(id.get());
    autoReindexIfStale(id);
  }

  @Override
  public boolean reindexIfStale(Account.Id id) throws IOException {
    if (stalenessChecker.isStale(id)) {
      index(id);
      return true;
    }
    return false;
  }

  private static boolean autoReindexIfStale(Config cfg) {
    return cfg.getBoolean("index", null, "autoReindexIfStale", true);
  }

  private void autoReindexIfStale(Account.Id id) {
    if (autoReindexIfStale) {
      // Don't retry indefinitely; if this fails the account will be stale.
      @SuppressWarnings("unused")
      Future<?> possiblyIgnoredError = reindexIfStaleAsync(id);
    }
  }

  /**
   * Asynchronously check if a account is stale, and reindex if it is.
   *
   * <p>Always run on the batch executor, even if this indexer instance is configured to use a
   * different executor.
   *
   * @param id the ID of the account.
   * @return future for reindexing the account; returns true if the account was stale.
   */
  @SuppressWarnings("deprecation")
  private com.google.common.util.concurrent.CheckedFuture<Boolean, IOException> reindexIfStaleAsync(
      Account.Id id) {
    return Futures.makeChecked(
        Futures.nonCancellationPropagating(batchExecutor.submit(() -> reindexIfStale(id))),
        IndexUtils.MAPPER);
  }

  private void fireAccountIndexedEvent(int id) {
    for (AccountIndexedListener listener : indexedListener) {
      listener.onAccountIndexed(id);
    }
  }

  private Collection<AccountIndex> getWriteIndexes() {
    if (indexes != null) {
      return indexes.getWriteIndexes();
    }

    return index != null ? Collections.singleton(index) : ImmutableSet.<AccountIndex>of();
  }
}
