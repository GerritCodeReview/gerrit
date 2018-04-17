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
import java.util.function.Consumer;

/**
 * An aggregation of operations on accounts for test purposes.
 *
 * <p>To execute the operations, no Gerrit permissions are necessary.
 *
 * <p><strong>Note:</strong> The methods of this class may only be used for creating/adjusting the
 * desired test context. For testing a REST endpoint or a method of the Java API, directly call that
 * endpoint/method.
 */
public interface AccountOperations {

  /**
   * Checks whether an account exists.
   *
   * @param accountId the ID of the account
   * @return {@code true} if the account exists
   */
  boolean exists(Account.Id accountId) throws Exception;

  /**
   * Retrieves an account.
   *
   * <p><strong>Note:</strong> This call will fail with an exception if the requested account
   * doesn't exist. If you want to check for the existence of an account, use {@link
   * #exists(Account.Id)} instead.
   *
   * @param accountId the ID of the account
   * @return the corresponding {@code TestAccount}
   */
  TestAccount get(Account.Id accountId) throws Exception;

  /**
   * Creates a new account with arbitrary attributes.
   *
   * <p>This method is a more readable variant for {@code create(creation -> {})}. See {@link
   * #create(Consumer)} for more details.
   *
   * @return the created {@code TestAccount}
   */
  default TestAccount createArbitrary() throws Exception {
    return create(creation -> {});
  }

  /**
   * Creates a new account.
   *
   * <p>Any parameters not set on the {@code TestAccountUpdate.Builder} will have arbitrary values.
   *
   * <p><strong>Note:</strong> If another account with the provided user name or preferred email
   * address already exists, this call will fail.
   *
   * @param creation a {@code Consumer} to initialize the attributes of a new account
   * @return the created {@code TestAccount}
   */
  TestAccount create(Consumer<TestAccountUpdate.Builder> creation) throws Exception;

  /**
   * Updates an existing account.
   *
   * <p><strong>Note:</strong> This call will fail with an exception if the account to update
   * doesn't exist. If you want to check for the existence of an account, use {@link
   * #exists(Account.Id)}.
   *
   * @param accountId the ID of the account
   * @param update a {@code Consumer} to update the attributes of an account
   * @return the updated {@code TestAccount}
   */
  TestAccount update(Account.Id accountId, Consumer<TestAccountUpdate.Builder> update)
      throws Exception;
}
