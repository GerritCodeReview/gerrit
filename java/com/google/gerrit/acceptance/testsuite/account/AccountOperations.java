// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.account;

import com.google.gerrit.reviewdb.client.Account;

/**
 * An aggregation of operations on accounts for test purposes.
 *
 * <p>To execute the operations, no Gerrit permissions are necessary.
 *
 * <p><strong>Note:</strong> This interface is not implemented using the REST or extension API.
 * Hence, it cannot be used for testing those APIs.
 */
public interface AccountOperations {

  /**
   * Starts the fluent chain for a querying or modifying an account. Please see the methods of
   * {@link MoreAccountOperations} for details on possible operations.
   *
   * @return an aggregation of operations on a specific account
   */
  MoreAccountOperations account(Account.Id accountId);

  /**
   * Starts the fluent chain to create an account. The returned builder can be used to specify the
   * attributes of the new account. To create the account for real, {@link
   * TestAccountCreation.Builder#create()} must be called.
   *
   * <p>Example:
   *
   * <pre>
   * TestAccount createdAccount = accountOperations
   *     .newAccount()
   *     .username("janedoe")
   *     .preferredEmail("janedoe@example.com")
   *     .fullname("Jane Doe")
   *     .create();
   * </pre>
   *
   * <p><strong>Note:</strong> If another account with the provided user name or preferred email
   * address already exists, the creation of the account will fail.
   *
   * @return a builder to create the new account
   */
  TestAccountCreation.Builder newAccount();

  /** An aggregation of methods on a specific account. */
  interface MoreAccountOperations {

    /**
     * Checks whether the account exists.
     *
     * @return {@code true} if the account exists
     */
    boolean exists() throws Exception;

    /**
     * Retrieves the account.
     *
     * <p><strong>Note:</strong> This call will fail with an exception if the requested account
     * doesn't exist. If you want to check for the existence of an account, use {@link #exists()}
     * instead.
     *
     * @return the corresponding {@code TestAccount}
     */
    TestAccount get() throws Exception;

    /**
     * Starts the fluent chain to update an account. The returned builder can be used to specify how
     * the attributes of the account should be modified. To update the account for real, {@link
     * TestAccountUpdate.Builder#update()} must be called.
     *
     * <p>Example:
     *
     * <pre>
     * TestAccount updatedAccount = accountOperations.forUpdate().status("on vacation").update();
     * </pre>
     *
     * <p><strong>Note:</strong> The update will fail with an exception if the account to update
     * doesn't exist. If you want to check for the existence of an account, use {@link #exists()}.
     *
     * @return a builder to update the account
     */
    TestAccountUpdate.Builder forUpdate();
  }
}
