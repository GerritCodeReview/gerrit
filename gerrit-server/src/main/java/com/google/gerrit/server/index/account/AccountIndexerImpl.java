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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.index.Index;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

public class AccountIndexerImpl implements AccountIndexer {
  public interface Factory {
    AccountIndexerImpl create(AccountIndexCollection indexes);

    AccountIndexerImpl create(@Nullable AccountIndex index);
  }

  private final AccountCache byIdCache;
  private final AccountIndexCollection indexes;
  private final AccountIndex index;

  @AssistedInject
  AccountIndexerImpl(AccountCache byIdCache, @Assisted AccountIndexCollection indexes) {
    this.byIdCache = byIdCache;
    this.indexes = indexes;
    this.index = null;
  }

  @AssistedInject
  AccountIndexerImpl(AccountCache byIdCache, @Assisted AccountIndex index) {
    this.byIdCache = byIdCache;
    this.indexes = null;
    this.index = index;
  }

  @Override
  public void index(Account.Id id) throws IOException {
    for (Index<?, AccountState> i : getWriteIndexes()) {
      i.replace(byIdCache.get(id));
    }
  }

  private Collection<AccountIndex> getWriteIndexes() {
    if (indexes != null) {
      return indexes.getWriteIndexes();
    }

    return index != null ? Collections.singleton(index) : ImmutableSet.<AccountIndex>of();
  }
}
