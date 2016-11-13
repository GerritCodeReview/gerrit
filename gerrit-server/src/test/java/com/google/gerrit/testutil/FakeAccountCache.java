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

package com.google.gerrit.testutil;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.WatchConfig.ProjectWatchKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Fake implementation of {@link AccountCache} for testing. */
public class FakeAccountCache implements AccountCache {
  private final Map<Account.Id, AccountState> byId;
  private final Map<String, AccountState> byUsername;

  public FakeAccountCache() {
    byId = new HashMap<>();
    byUsername = new HashMap<>();
  }

  @Override
  public synchronized AccountState get(Account.Id accountId) {
    AccountState state = getIfPresent(accountId);
    if (state != null) {
      return state;
    }
    return newState(new Account(accountId, TimeUtil.nowTs()));
  }

  @Override
  public synchronized AccountState getIfPresent(Account.Id accountId) {
    return byId.get(accountId);
  }

  @Override
  public synchronized AccountState getByUsername(String username) {
    return byUsername.get(username);
  }

  @Override
  public synchronized void evict(Account.Id accountId) {
    byId.remove(accountId);
  }

  @Override
  public synchronized void evictByUsername(String username) {
    byUsername.remove(username);
  }

  @Override
  public synchronized void evictAll() {
    byId.clear();
    byUsername.clear();
  }

  public synchronized void put(Account account) {
    AccountState state = newState(account);
    byId.put(account.getId(), state);
    if (account.getUserName() != null) {
      byUsername.put(account.getUserName(), state);
    }
  }

  private static AccountState newState(Account account) {
    return new AccountState(
        account,
        ImmutableSet.<AccountGroup.UUID>of(),
        ImmutableSet.<AccountExternalId>of(),
        new HashMap<ProjectWatchKey, Set<NotifyType>>());
  }
}
