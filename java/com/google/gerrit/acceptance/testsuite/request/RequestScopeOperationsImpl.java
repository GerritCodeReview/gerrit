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

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.account.TestAccount;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.inject.Inject;
import com.google.inject.Provider;
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
  private final AccountOperations accountOperations;
  private final IdentifiedUser.GenericFactory userFactory;
  private final Provider<AnonymousUser> anonymousUserProvider;
  private final InternalUser.Factory internalUserFactory;

  @Inject
  RequestScopeOperationsImpl(
      AcceptanceTestRequestScope atrScope,
      AccountCache accountCache,
      AccountOperations accountOperations,
      GenericFactory userFactory,
      Provider<AnonymousUser> anonymousUserProvider,
      InternalUser.Factory internalUserFactory) {
    this.atrScope = atrScope;
    this.accountCache = accountCache;
    this.accountOperations = accountOperations;
    this.userFactory = userFactory;
    this.anonymousUserProvider = anonymousUserProvider;
    this.internalUserFactory = internalUserFactory;
  }

  @Override
  @CanIgnoreReturnValue
  public AcceptanceTestRequestScope.Context setApiUser(Account.Id accountId) {
    return setApiUser(accountOperations.account(accountId).get());
  }

  @Override
  @CanIgnoreReturnValue
  public AcceptanceTestRequestScope.Context setApiUser(TestAccount testAccount) {
    return atrScope.set(atrScope.newContext(createIdentifiedUser(testAccount.accountId())));
  }

  @Override
  @CanIgnoreReturnValue
  public AcceptanceTestRequestScope.Context resetCurrentApiUser() {
    CurrentUser user = atrScope.get().getUser();
    // More special cases for anonymous users etc. can be added as needed.
    checkState(user.isIdentifiedUser(), "can only reset IdentifiedUser, not %s", user);
    return setApiUser(user.getAccountId());
  }

  @Override
  @CanIgnoreReturnValue
  public AcceptanceTestRequestScope.Context setApiUserAnonymous() {
    return atrScope.set(atrScope.newContext(anonymousUserProvider.get()));
  }

  @Override
  @CanIgnoreReturnValue
  public AcceptanceTestRequestScope.Context setApiUserInternal() {
    return atrScope.set(atrScope.newContext(internalUserFactory.create()));
  }

  private IdentifiedUser createIdentifiedUser(Account.Id accountId) {
    return userFactory.create(
        accountCache
            .get(requireNonNull(accountId))
            .orElseThrow(
                () -> new IllegalArgumentException("account does not exist: " + accountId)));
  }
}
