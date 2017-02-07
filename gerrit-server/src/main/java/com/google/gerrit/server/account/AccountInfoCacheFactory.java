// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.reviewdb.client.Account;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Efficiently builds an {@link AccountInfoCache}. */
public class AccountInfoCacheFactory {
  public interface Factory {
    AccountInfoCacheFactory create();
  }

  private final AccountCache accountCache;
  private final Map<Account.Id, Account> out;

  @Inject
  AccountInfoCacheFactory(final AccountCache accountCache) {
    this.accountCache = accountCache;
    this.out = new HashMap<>();
  }

  /**
   * Indicate an account will be needed later on.
   *
   * @param id identity that will be needed in the future; may be null.
   */
  public void want(final Account.Id id) {
    if (id != null && !out.containsKey(id)) {
      out.put(id, accountCache.get(id).getAccount());
    }
  }

  /** Indicate one or more accounts will be needed later on. */
  public void want(final Iterable<Account.Id> ids) {
    for (final Account.Id id : ids) {
      want(id);
    }
  }

  public Account get(final Account.Id id) {
    want(id);
    return out.get(id);
  }

  /** Create an AccountInfoCache with the currently loaded Account entities. */
  public AccountInfoCache create() {
    final List<AccountInfo> r = new ArrayList<>(out.size());
    for (final Account a : out.values()) {
      r.add(new AccountInfo(a));
    }
    return new AccountInfoCache(r);
  }
}
