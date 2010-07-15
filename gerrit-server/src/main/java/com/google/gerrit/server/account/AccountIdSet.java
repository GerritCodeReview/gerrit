// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.reviewdb.Account;

import java.util.Collections;
import java.util.Set;

/** Wrapper around a Set<Account.Id> */
public class AccountIdSet {
  public static final AccountIdSet EMPTY_SET = new AccountIdSet();
  private final Set<Account.Id> ids;

  private AccountIdSet() {
    this.ids = Collections.emptySet();
  }

  public AccountIdSet(Set<Account.Id> ids) {
    this.ids = Collections.unmodifiableSet(ids);
  }

  public AccountIdSet(Account.Id id) {
    this.ids = Collections.singleton(id);
  }

  public Set<Account.Id> getIds() {
    return ids;
  }
}
