// Copyright (C) 2025 The Android Open Source Project
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
import static com.google.gerrit.server.git.QueueProvider.QueueType.BATCH;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.entities.Account;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.account.storage.notedb.AccountsNoteDbRepoReader;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.account.AccountField;
import com.google.gerrit.server.index.account.AccountIndexCollection;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.eclipse.jgit.lib.Repository;

/**
 * Runnable task that periodically reindexes accounts.
 *
 * <p>This job scans the {@code All-Users} repository to find all existing accounts and ensures that
 * the account index is consistent with the Git state:
 *
 * <ul>
 *   <li>Accounts present in NoteDb but missing or stale in the index are reindexed.
 *   <li>Accounts present in the index but no longer existing in NoteDb are deleted.
 * </ul>
 *
 * <p>The reindexing work is scheduled on the batch indexing executor and can run concurrently for
 * multiple accounts. The number of reindexed accounts is logged at the end of each run.
 *
 * <p>The interval between runs is configurable with {@code index.scheduledIndexer.interval} in
 * {@code gerrit.config}. By default the reindexing is executed every 5 minutes.
 */
public class PeriodicAccountIndexer implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AllUsersName allUsersName;
  private final GitRepositoryManager repoManager;
  private final ListeningExecutorService executor;
  private final AccountIndexCollection indexes;
  private final Provider<AccountIndexer> accountIndexerProvider;
  private final IndexConfig indexConfig;

  @Inject
  PeriodicAccountIndexer(
      AllUsersName allUsersName,
      GitRepositoryManager repoManager,
      @IndexExecutor(BATCH) ListeningExecutorService executor,
      AccountIndexCollection indexes,
      IndexConfig indexConfig,
      Provider<AccountIndexer> accountIndexerProvider) {
    this.allUsersName = allUsersName;
    this.repoManager = repoManager;
    this.executor = executor;
    this.indexes = indexes;
    this.indexConfig = indexConfig;
    this.accountIndexerProvider = accountIndexerProvider;
  }

  @Override
  public synchronized void run() {
    try (Repository allUsers = repoManager.openRepository(allUsersName)) {
      ImmutableSet<Account.Id> allAccounts =
          AccountsNoteDbRepoReader.readAllIds(allUsers).collect(toImmutableSet());
      AccountIndexer accountsIndexer = accountIndexerProvider.get();
      AtomicInteger reindexCounter = new AtomicInteger();
      List<ListenableFuture<?>> indexingTasks = new ArrayList<>();
      for (Account.Id accountId : allAccounts) {
        indexingTasks.add(
            executor.submit(
                () -> {
                  if (accountsIndexer.reindexIfStale(accountId)) {
                    reindexCounter.incrementAndGet();
                  }
                }));
      }

      // Handle account existing in the index, but no longer in NoteDB.
      // This can happen because of missing events in a multi-primary setup.
      Set<Account.Id> accountsInIndex = queryAllAccountsFromIndex();
      for (Account.Id accountId : Sets.difference(accountsInIndex, allAccounts)) {
        indexingTasks.add(
            executor.submit(
                () -> {
                  accountsIndexer.index(accountId);
                  reindexCounter.incrementAndGet();
                }));
      }
      Futures.successfulAsList(indexingTasks).get();
      logger.atInfo().log("Run account indexer, %s accounts reindexed", reindexCounter);
    } catch (Exception t) {
      logger.atSevere().withCause(t).log("Failed to reindex accounts");
    }
  }

  private Set<Account.Id> queryAllAccountsFromIndex() {
    try {
      DataSource<AccountState> result =
          indexes
              .getSearchIndex()
              .getSource(
                  Predicate.any(),
                  QueryOptions.create(
                      indexConfig, 0, Integer.MAX_VALUE, Set.of(AccountField.ID_FIELD.name())));
      return StreamSupport.stream(result.readRaw().spliterator(), false)
          .map(PeriodicAccountIndexer::fromAccountIdField)
          .flatMap(Optional::stream)
          .collect(Collectors.toUnmodifiableSet());
    } catch (QueryParseException e) {
      throw new RuntimeException(e);
    }
  }

  private static Optional<Account.Id> fromAccountIdField(FieldBundle f) {
    return Account.Id.tryParse(f.<String>getValue(AccountField.ID_STR_FIELD_SPEC));
  }
}
