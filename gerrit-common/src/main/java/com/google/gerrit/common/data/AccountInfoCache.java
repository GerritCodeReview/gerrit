// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.client.Account;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** In-memory table of {@link AccountInfo}, indexed by {@code Account.Id}. */
public class AccountInfoCache {
  private static final AccountInfoCache EMPTY;

  static {
    EMPTY = new AccountInfoCache();
    EMPTY.accounts = Collections.emptyMap();
  }

  /** Obtain an empty cache singleton. */
  public static AccountInfoCache empty() {
    return EMPTY;
  }

  protected Map<Account.Id, AccountInfo> accounts;

  protected AccountInfoCache() {}

  public AccountInfoCache(final Iterable<AccountInfo> list) {
    accounts = new HashMap<>();
    for (final AccountInfo ai : list) {
      accounts.put(ai.getId(), ai);
    }
  }

  /**
   * Lookup the account summary
   *
   * <p>The return value can take on one of three forms:
   *
   * <ul>
   *   <li>{@code null}, if {@code id == null}.
   *   <li>a valid info block, if {@code id} was loaded.
   *   <li>an anonymous info block, if {@code id} was not loaded.
   * </ul>
   *
   * @param id the id desired.
   * @return info block for the account.
   */
  public AccountInfo get(final Account.Id id) {
    if (id == null) {
      return null;
    }

    AccountInfo r = accounts.get(id);
    if (r == null) {
      r = new AccountInfo(id);
      accounts.put(id, r);
    }
    return r;
  }

  /** Merge the information from another cache into this one. */
  public void merge(final AccountInfoCache other) {
    assert this != EMPTY;
    accounts.putAll(other.accounts);
  }
}
