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
import com.google.gerrit.acceptance.AcceptanceTestRequestScope.Context;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * The implementation of {@code RequestScopeOperations}.
 *
 * <p>There is only one implementation of {@code RequestScopeOperations}. Nevertheless, we keep the
 * separation between interface and implementation to enhance clarity.
 */
@Singleton
public class RequestScopeOperationsImpl implements RequestScopeOperations {
  private final AcceptanceTestRequestScope atrScope;
  private final AccountCache accountCache;
  private final IdentifiedUser.GenericFactory userFactory;

  @Inject
  RequestScopeOperationsImpl(
      AcceptanceTestRequestScope atrScope,
      AccountCache accountCache,
      IdentifiedUser.GenericFactory userFactory) {
    this.atrScope = atrScope;
    this.accountCache = accountCache;
    this.userFactory = userFactory;
  }

  @Override
  public Context setApiUser(Account.Id accountId) {
    AccountState accountState =
        accountCache
            .get(requireNonNull(accountId))
            .orElseThrow(
                () -> new IllegalArgumentException("account does not exist: " + accountId));
    return atrScope.set(atrScope.newContext(null, userFactory.create(accountState)));
  }
}
