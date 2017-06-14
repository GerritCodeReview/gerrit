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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import java.io.IOException;

/** Caches important (but small) account state to avoid database hits. */
public interface AccountCache {
  /**
   * Returns an {@code AccountState} instance for the given account ID. If not cached yet the
   * account is loaded. Returns an empty {@code AccountState} instance to represent a missing
   * account.
   *
   * @param accountId ID of the account that should be retrieved
   * @return {@code AccountState} instance for the given account ID, if no account with this ID
   *     exists an empty {@code AccountState} instance is returned to represent the missing account
   */
  AccountState get(Account.Id accountId);

  /**
   * Returns an {@code AccountState} instance for the given account ID. If not cached yet the
   * account is loaded. Returns {@code null} if the account is missing.
   *
   * @param accountId ID of the account that should be retrieved
   * @return {@code AccountState} instance for the given account ID, if no account with this ID
   *     exists {@code null} is returned
   */
  @Nullable
  AccountState getOrNull(Account.Id accountId);

  AccountState getByUsername(String username);

  void evict(Account.Id accountId) throws IOException;

  void evictByUsername(String username);
}
