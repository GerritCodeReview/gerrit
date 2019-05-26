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

import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.testsuite.account.TestAccount;
import com.google.gerrit.entities.Account;

/**
 * An aggregation of operations on Guice request scopes for test purposes.
 *
 * <p>To execute the operations, no Gerrit permissions are necessary.
 */
public interface RequestScopeOperations {
  /**
   * Sets the Guice request scope to the given account.
   *
   * <p>The resulting context has an SSH session attached. In order to use the SSH session returned
   * by {@link com.google.gerrit.acceptance.AcceptanceTestRequestScope.Context#getSession()}, SSH
   * must be enabled in the test and the account must have a username set. However, these are not
   * requirements simply to call this method.
   *
   * @param accountId account ID. Must exist; throws an unchecked exception otherwise.
   * @return the previous request scope.
   */
  AcceptanceTestRequestScope.Context setApiUser(Account.Id accountId);

  /**
   * Sets the Guice request scope to the given account.
   *
   * <p>The resulting context has an SSH session attached. In order to use the SSH session returned
   * by {@link com.google.gerrit.acceptance.AcceptanceTestRequestScope.Context#getSession()}, SSH
   * must be enabled in the test and the account must have a username set. However, these are not
   * requirements simply to call this method.
   *
   * @param testAccount test account from {@code AccountOperations}.
   * @return the previous request scope.
   */
  AcceptanceTestRequestScope.Context setApiUser(TestAccount testAccount);

  /**
   * Enforces a new request context for the current API user.
   *
   * <p>This recreates the {@code IdentifiedUser}, hence everything which is cached in the {@code
   * IdentifiedUser} is reloaded (e.g. the email addresses of the user).
   *
   * <p>The current user must be an identified user.
   *
   * @return the previous request scope.
   */
  AcceptanceTestRequestScope.Context resetCurrentApiUser();

  /**
   * Sets the Guice request scope to the anonymous user.
   *
   * @return the previous request scope.
   */
  AcceptanceTestRequestScope.Context setApiUserAnonymous();

  /**
   * Sets the Guice request scope to the internal server user.
   *
   * @return the previous request scope.
   */
  AcceptanceTestRequestScope.Context setApiUserInternal();
}
