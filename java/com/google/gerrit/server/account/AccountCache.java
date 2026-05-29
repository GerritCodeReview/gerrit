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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.common.UsedAt.Project;
import com.google.gerrit.entities.Account;
import java.util.Collection;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;

/** Caches important (but small) account state to avoid database hits. */
public interface AccountCache {
  /**
   * Returns an {@code AccountState} instance for the given account ID. If not cached yet the
   * account is loaded. Returns {@link Optional#empty()} if the account is missing.
   *
   * @param accountId ID of the account that should be retrieved
   * @return {@code AccountState} instance for the given account ID, if no account with this ID
   *     exists {@link Optional#empty()} is returned
   */
  Optional<AccountState> get(Account.Id accountId);

  /**
   * Returns a {@code ImmutableMap} of {@code Account.Id} to {@code AccountState} for the given
   * account IDs. If not cached yet the accounts are loaded. If an account can't be loaded (e.g.
   * because it is missing), the entry will be missing from the result. The order of the provided
   * account IDs is preserved in the result.
   *
   * <p>Loads accounts in parallel if applicable.
   *
   * @param accountIds IDs of the account that should be retrieved
   * @return {@code Map} of {@code Account.Id} to {@code AccountState} instances for the given
   *     account IDs, if an account can't be loaded (e.g. because it is missing), the entry will be
   *     missing from the result. The order of the provided * account IDs is preserved in the
   *     result.
   */
  ImmutableMap<Account.Id, AccountState> get(Collection<Account.Id> accountIds);

  /**
   * Returns an {@code AccountState} instance for the given account ID. If not cached yet the
   * account is loaded. Returns an empty {@code AccountState} instance to represent a missing
   * account.
   *
   * <p>This method should only be used in exceptional cases where it is required to get an account
   * state even if the account is missing. Callers should leave a comment with the method invocation
   * explaining why this method is used. Most callers of {@link AccountCache} should use {@link
   * #get(Account.Id)} instead and handle the missing account case explicitly.
   *
   * @param accountId ID of the account that should be retrieved
   * @return {@code AccountState} instance for the given account ID, if no account with this ID
   *     exists an empty {@code AccountState} instance is returned to represent the missing account
   */
  AccountState getEvenIfMissing(Account.Id accountId);

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
   * Returns an {@code AccountState} instance for the given account ID at the given {@code metaId}
   * of {@link com.google.gerrit.entities.RefNames#refsUsers} ref.
   *
   * <p>The caller is responsible to ensure the presence of {@code metaId} and the corresponding
   * meta ref. The method does not populate {@link AccountState#defaultPreferences}.
   *
   * @param accountId ID of the account that should be retrieved.
   * @param metaId the sha1 of commit in {@link com.google.gerrit.entities.RefNames#refsUsers} ref.
   * @return {@code AccountState} instance for the given account ID at specific sha1 {@code metaId}.
   */
  @UsedAt(Project.GOOGLE)
  AccountState getFromMetaId(Account.Id accountId, ObjectId metaId);
}
