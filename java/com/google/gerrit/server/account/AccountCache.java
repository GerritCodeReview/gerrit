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
import java.util.Optional;

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

  /**
   * Returns an {@code AccountState} instance for the given username.
   *
   * <p>This method first loads the external ID for the username and then uses the account ID of the
   * external ID to lookup the account from the cache.
   *
   * @param username username of the account that should be retrieved
   * @return {@code AccountState} instance for the given username, if no account with this username
   *     exists or if loading the external ID fails {@link Optional#empty()} is returned
   */
  Optional<AccountState> getByUsername(String username);

  /**
   * Evicts the account from the cache and triggers a reindex for it.
   *
   * @param accountId account ID of the account that should be evicted
   * @throws IOException thrown if reindexing fails
   */
  void evict(@Nullable Account.Id accountId) throws IOException;

  /** Evict all accounts from the cache, but doesn't trigger reindex of all accounts. */
  void evictAllNoReindex();
}
