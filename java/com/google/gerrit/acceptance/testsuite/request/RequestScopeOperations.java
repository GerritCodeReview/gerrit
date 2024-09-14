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

import com.google.gerrit.acceptance.testsuite.account.TestAccount;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;

/**
 * An aggregation of operations on Guice request scopes for test purposes.
 *
 * <p>To execute the operations, no Gerrit permissions are necessary.
 */
public interface RequestScopeOperations {
  /**
   * Sets the Guice request scope to the given account without closing the existing context.
   *
   * <p>Returns newly created context. To restore previous context call {@link
   * ManualRequestContext#close()} method of the returned context.
   *
   * <p>In order to create and use the SSH session for the newly set context, SSH must be enabled in
   * the test and the account must have a username set.
   *
   * <p>The session associated with the returned context can be obtained by calling {@link
   * com.google.gerrit.acceptance.ServerTestRule#getOrCreateSshSessionForContext}.
   *
   * @param accountId account ID. Must exist; throws an unchecked exception otherwise.
   */
  ManualRequestContext setNestedApiUser(Account.Id accountId) throws Exception;

  /**
   * Sets the Guice request scope to the given account.
   *
   * <p>After calling this method, the new context can be obtained using the {@link
   * ThreadLocalRequestContext#getContext()} method.
   *
   * <p>If the previous context is a {@link ManualRequestContext}. the method closes it before
   * setting the new context. This prevents stacking of contexts.
   *
   * <p>In order to create and use the SSH session for the new context, SSH must be enabled in the
   * test and the account must have a username set. To get the session associated with the newly set
   * context use the {@link
   * com.google.gerrit.acceptance.ServerTestRule#getOrCreateSshSessionForContext} method.
   *
   * @param accountId account ID. Must exist; throws an unchecked exception otherwise.
   */
  void setApiUser(Account.Id accountId) throws Exception;

  /**
   * Sets the Guice request scope to the given account.
   *
   * <p>See {@link #setApiUser(Account.Id)} for details.
   *
   * @param testAccount test account from {@code AccountOperations}.
   */
  void setApiUser(TestAccount testAccount) throws Exception;

  /**
   * Enforces a new request context for the current API user.
   *
   * <p>See {@link #setApiUser(Account.Id)} for details.
   *
   * <p>The current user (i.e. a user set before calling this method) must be an identified user.
   */
  void resetCurrentApiUser() throws Exception;

  /**
   * Sets the Guice request scope to the anonymous user.
   *
   * <p>See {@link #setApiUser(Account.Id)} for details.
   */
  void setApiUserAnonymous() throws Exception;

  /**
   * Sets the Guice request scope to the internal server user.
   *
   * <p>See {@link #setApiUser(Account.Id)} for details.
   */
  void setApiUserInternal() throws Exception;
}
