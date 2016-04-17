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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.index.Index;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

public class AccountIndexer {

  public interface Factory {
    AccountIndexer create(AccountIndex index);
    AccountIndexer create(AccountIndexCollection indexes);
  }

  private final AccountIndexCollection indexes;
  private final AccountCache byIdCache;
  private final AccountIndex index;

  @AssistedInject
  AccountIndexer(AccountCache byIdCache,
      @Assisted AccountIndex index) {
    this.indexes = null;
    this.byIdCache = byIdCache;
    this.index = index;
  }

  @AssistedInject
  AccountIndexer(AccountCache byIdCache,
      @Assisted AccountIndexCollection indexes) {
    this.byIdCache = byIdCache;
    this.indexes = indexes;
    this.index = null;
  }

  /**
   * Synchronously index an account.
   *
   * @param id account id to index.
   */
  public void index(Account.Id id) throws IOException {
    for (Index<Account.Id, AccountState> i : getWriteIndexes()) {
      i.replace(byIdCache.get(id));
    }
  }

  private Collection<AccountIndex> getWriteIndexes() {
    return indexes != null
        ? indexes.getWriteIndexes()
        : Collections.singleton(index);
  }
}
