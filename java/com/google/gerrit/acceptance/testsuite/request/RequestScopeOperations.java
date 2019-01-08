// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.request;

import static java.util.Objects.requireNonNull;

import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.testsuite.account.TestAccount;
import com.google.gerrit.reviewdb.client.Account;

/**
 * An aggregation of operations on Guice request scopes for test purposes.
 *
 * <p>To execute the operations, no Gerrit permissions are necessary.
 */
public interface RequestScopeOperations {
  /**
   * Sets the Guice request scope to the given account.
   *
   * <p>The resulting scope has no SSH session attached.
   *
   * @param accountId account ID. Must exist; throws an unchecked exception otherwise.
   * @return the previous request scope.
   */
  AcceptanceTestRequestScope.Context setApiUser(Account.Id accountId);

  /**
   * Sets the Guice request scope to the given account.
   *
   * <p>The resulting scope has no SSH session attached.
   *
   * @param testAccount test account from {@code AccountOperations}.
   * @return the previous request scope.
   */
  default AcceptanceTestRequestScope.Context setApiUser(TestAccount testAccount) {
    return setApiUser(requireNonNull(testAccount).accountId());
  }
}
