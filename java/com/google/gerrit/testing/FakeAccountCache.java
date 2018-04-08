// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.testing;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.config.AllUsersNameProvider;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Fake implementation of {@link AccountCache} for testing. */
public class FakeAccountCache implements AccountCache {
  private final Map<Account.Id, AccountState> byId;

  public FakeAccountCache() {
    byId = new HashMap<>();
  }

  @Override
  public synchronized AccountState getEvenIfMissing(Account.Id accountId) {
    AccountState state = byId.get(accountId);
    if (state != null) {
      return state;
    }
    return newState(new Account(accountId, TimeUtil.nowTs()));
  }

  @Override
  public synchronized Optional<AccountState> get(Account.Id accountId) {
    return Optional.ofNullable(byId.get(accountId));
  }

  @Override
  public synchronized Optional<AccountState> getByUsername(String username) {
    throw new UnsupportedOperationException();
  }

  @Override
  public synchronized void evict(@Nullable Account.Id accountId) {
    if (byId != null) {
      byId.remove(accountId);
    }
  }

  @Override
  public synchronized void evictAll() {
    byId.clear();
  }

  public synchronized void put(Account account) {
    AccountState state = newState(account);
    byId.put(account.getId(), state);
  }

  private static AccountState newState(Account account) {
    return AccountState.forAccount(new AllUsersName(AllUsersNameProvider.DEFAULT), account);
  }
}
