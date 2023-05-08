// Copyright (C) 2017 The Android Open Source Project
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

import com.google.gerrit.entities.Account;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Class to access accounts. */
public interface Accounts {
  /**
   * Gets the account state for the given ID.
   *
   * @return the account state if found, {@code Optional.empty} otherwise.
   */
  Optional<AccountState> get(Account.Id accountId) throws IOException, ConfigInvalidException;

  /**
   * Gets the account states for all the given IDs.
   *
   * @return the account states.
   */
  List<AccountState> get(Collection<Account.Id> accountIds)
      throws IOException, ConfigInvalidException;

  /**
   * Returns all accounts.
   *
   * @return all accounts
   */
  List<AccountState> all() throws IOException;

  /**
   * Returns all account IDs.
   *
   * @return all account IDs
   */
  Set<Account.Id> allIds() throws IOException;

  /**
   * Returns the first n account IDs.
   *
   * @param n the number of account IDs that should be returned
   * @return first n account IDs
   */
  List<Account.Id> firstNIds(int n) throws IOException;

  /**
   * Checks if any account exists.
   *
   * @return {@code true} if at least one account exists, otherwise {@code false}.
   */
  boolean hasAnyAccount() throws IOException;
}
