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

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.SiteIndexer;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AllAccountsIndexer extends SiteIndexer<Account.Id, AccountState, AccountIndex> {
  private static final Logger log = LoggerFactory.getLogger(AllAccountsIndexer.class);

  private final SchemaFactory<ReviewDb> schemaFactory;
  private final ListeningExecutorService executor;
  private final AccountCache accountCache;

  @Inject
  AllAccountsIndexer(
      SchemaFactory<ReviewDb> schemaFactory,
      @IndexExecutor(BATCH) ListeningExecutorService executor,
      AccountCache accountCache) {
    this.schemaFactory = schemaFactory;
    this.executor = executor;
    this.accountCache = accountCache;
  }

  @Override
  public SiteIndexer.Result indexAll(final AccountIndex index) {
    ProgressMonitor progress = new TextProgressMonitor(new PrintWriter(progressOut));
    progress.start(2);
    Stopwatch sw = Stopwatch.createStarted();
    List<Account.Id> ids;
    try {
      ids = collectAccounts(progress);
    } catch (OrmException e) {
      log.error("Error collecting accounts", e);
      return new Result(sw, false, 0, 0);
    }
    return reindexAccounts(index, ids, progress);
  }

  private SiteIndexer.Result reindexAccounts(
      final AccountIndex index, List<Account.Id> ids, ProgressMonitor progress) {
    progress.beginTask("Reindexing accounts", ids.size());
    List<ListenableFuture<?>> futures = new ArrayList<>(ids.size());
    AtomicBoolean ok = new AtomicBoolean(true);
    final AtomicInteger done = new AtomicInteger();
    final AtomicInteger failed = new AtomicInteger();
    Stopwatch sw = Stopwatch.createStarted();
    for (final Account.Id id : ids) {
      final String desc = "account " + id;
      ListenableFuture<?> future =
          executor.submit(
              new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                  try {
                    accountCache.evict(id);
                    index.replace(accountCache.get(id));
                    if (verboseWriter != null) {
                      verboseWriter.println("Reindexed " + desc);
                    }
                    done.incrementAndGet();
                  } catch (Exception e) {
                    failed.incrementAndGet();
                    throw e;
                  }
                  return null;
                }
              });
      addErrorListener(future, desc, progress, ok);
      futures.add(future);
    }

    try {
      Futures.successfulAsList(futures).get();
    } catch (ExecutionException | InterruptedException e) {
      log.error("Error waiting on account futures", e);
      return new Result(sw, false, 0, 0);
    }

    progress.endTask();
    return new Result(sw, ok.get(), done.get(), failed.get());
  }

  private List<Account.Id> collectAccounts(ProgressMonitor progress) throws OrmException {
    progress.beginTask("Collecting accounts", ProgressMonitor.UNKNOWN);
    List<Account.Id> ids = new ArrayList<>();
    try (ReviewDb db = schemaFactory.open()) {
      for (Account account : db.accounts().all()) {
        ids.add(account.getId());
      }
    }
    progress.endTask();
    return ids;
  }
}
