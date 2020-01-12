// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.util.concurrent.AtomicLongMap;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.events.AccountIndexedListener;

/** Checks if an account is indexed the correct number of times. */
public class AccountIndexedCounter implements AccountIndexedListener {
  private final AtomicLongMap<Integer> countsByAccount = AtomicLongMap.create();

  @Override
  public void onAccountIndexed(int id) {
    countsByAccount.incrementAndGet(id);
  }

  public void clear() {
    countsByAccount.clear();
  }

  public void assertReindexOf(TestAccount testAccount) {
    assertReindexOf(testAccount, 1);
  }

  public void assertReindexOf(AccountInfo accountInfo) {
    assertReindexOf(Account.id(accountInfo._accountId), 1);
  }

  public void assertReindexOf(TestAccount testAccount, long expectedCount) {
    assertThat(countsByAccount.asMap()).containsExactly(testAccount.id().get(), expectedCount);
    clear();
  }

  public void assertReindexOf(Account.Id accountId, long expectedCount) {
    assertThat(countsByAccount.asMap()).containsEntry(accountId.get(), expectedCount);
    countsByAccount.remove(accountId.get());
  }

  public void assertNoReindex() {
    assertThat(countsByAccount.asMap()).isEmpty();
  }
}
