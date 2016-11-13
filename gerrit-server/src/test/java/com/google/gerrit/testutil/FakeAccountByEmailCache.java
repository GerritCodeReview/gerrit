// Copyright (C) 2015 The Android Open Source Project
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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountByEmailCache;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Fake implementation of {@link AccountByEmailCache} for testing. */
public class FakeAccountByEmailCache implements AccountByEmailCache {
  private final SetMultimap<String, Account.Id> byEmail;
  private final Set<Account.Id> anyEmail;

  public FakeAccountByEmailCache() {
    byEmail = HashMultimap.create();
    anyEmail = new HashSet<>();
  }

  @Override
  public synchronized Set<Account.Id> get(String email) {
    return Collections.unmodifiableSet(Sets.union(byEmail.get(email), anyEmail));
  }

  @Override
  public synchronized void evict(String email) {
    // Do nothing.
  }

  public synchronized void put(String email, Account.Id id) {
    byEmail.put(email, id);
  }

  public synchronized void putAny(Account.Id id) {
    anyEmail.add(id);
  }
}
