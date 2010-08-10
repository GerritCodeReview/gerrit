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
import com.google.gerrit.reviewdb.Account;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

/** Efficiently builds an {@link AccountInfoCache}. */
public class AccountInfoCacheFactory {
  public interface Factory {
    AccountInfoCacheFactory create();
  }

  private final AccountCache accountCache;
  private final Map<Account.Id, Account> out;
  private final Set<Account.Id> wants;

  @Inject
  AccountInfoCacheFactory(final AccountCache accountCache) {
    this.accountCache = accountCache;
    this.out = new HashMap<Account.Id, Account>();
    this.wants = new HashSet<Account.Id>();
  }

  /**
   * Indicate an account will be needed later on.
   *
   * @param id identity that will be needed in the future; may be null.
   */
  public void want(final Account.Id id) {
    if (id != null) {
      wants.add(id);
    }
  }

  /** Indicate one or more accounts will be needed later on. */
  public void want(final Collection<Account.Id> ids) {
    wants.addAll(ids);
  }

  private void fetchWants() {
    Set<Account.Id> missing = new HashSet<Account.Id>(wants);
    missing.removeAll(out.keySet());

    for (Entry<Account.Id, AccountState> e : accountCache.getAll(missing)
        .entrySet()) {
      out.put(e.getKey(), e.getValue().getAccount());
    }
  }

  public Account get(final Account.Id id) {
    if (!out.containsKey(id)) {
      wants.add(id);
      fetchWants();
    }
    return out.get(id);
  }

  /**
   * Create an AccountInfoCache with the currently loaded Account entities.
   * */
  public AccountInfoCache create() {
    fetchWants();
    final List<AccountInfo> r = new ArrayList<AccountInfo>(out.size());
    for (final Account a : out.values()) {
      r.add(new AccountInfo(a));
    }
    return new AccountInfoCache(r);
  }
}
