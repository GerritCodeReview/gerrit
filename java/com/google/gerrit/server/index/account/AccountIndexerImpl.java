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

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.events.AccountIndexedListener;
import com.google.gerrit.index.Index;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

public class AccountIndexerImpl implements AccountIndexer {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    AccountIndexerImpl create(AccountIndexCollection indexes);

    AccountIndexerImpl create(@Nullable AccountIndex index);
  }

  private final AccountCache byIdCache;
  private final PluginSetContext<AccountIndexedListener> indexedListener;
  private final StalenessChecker stalenessChecker;
  @Nullable private final AccountIndexCollection indexes;
  @Nullable private final AccountIndex index;

  @AssistedInject
  AccountIndexerImpl(
      AccountCache byIdCache,
      PluginSetContext<AccountIndexedListener> indexedListener,
      StalenessChecker stalenessChecker,
      @Assisted AccountIndexCollection indexes) {
    this.byIdCache = byIdCache;
    this.indexedListener = indexedListener;
    this.stalenessChecker = stalenessChecker;
    this.indexes = indexes;
    this.index = null;
  }

  @AssistedInject
  AccountIndexerImpl(
      AccountCache byIdCache,
      PluginSetContext<AccountIndexedListener> indexedListener,
      StalenessChecker stalenessChecker,
      @Assisted @Nullable AccountIndex index) {
    this.byIdCache = byIdCache;
    this.indexedListener = indexedListener;
    this.stalenessChecker = stalenessChecker;
    this.indexes = null;
    this.index = index;
  }

  @Override
  public void index(Account.Id id) throws IOException {
    byIdCache.evict(id);
    Optional<AccountState> accountState = byIdCache.get(id);

    if (accountState.isPresent()) {
      logger.atFine().log("Replace account %d in index", id.get());
    } else {
      logger.atFine().log("Delete account %d from index", id.get());
    }

    for (Index<Account.Id, AccountState> i : getWriteIndexes()) {
      // Evict the cache to get an up-to-date value for sure.
      if (accountState.isPresent()) {
        try (TraceTimer traceTimer =
            TraceContext.newTimer(
                "Replacing account %d in index version %d", id.get(), i.getSchema().getVersion())) {
          i.replace(accountState.get());
        }
      } else {
        try (TraceTimer traceTimer =
            TraceContext.newTimer(
                "Deleteing account %d in index version %d", id.get(), i.getSchema().getVersion())) {
          i.delete(id);
        }
      }
    }
    fireAccountIndexedEvent(id.get());
  }

  @Override
  public boolean reindexIfStale(Account.Id id) throws IOException {
    if (stalenessChecker.isStale(id)) {
      index(id);
      return true;
    }
    return false;
  }

  private void fireAccountIndexedEvent(int id) {
    indexedListener.runEach(l -> l.onAccountIndexed(id));
  }

  private Collection<AccountIndex> getWriteIndexes() {
    if (indexes != null) {
      return indexes.getWriteIndexes();
    }

    return index != null ? Collections.singleton(index) : ImmutableSet.<AccountIndex>of();
  }
}
