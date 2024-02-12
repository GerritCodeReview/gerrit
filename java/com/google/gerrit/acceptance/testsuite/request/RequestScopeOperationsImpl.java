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

import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.account.TestAccount;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
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
  private final ThreadLocalRequestContext localContext;
  private final AccountCache accountCache;
  private final AccountOperations accountOperations;
  private final IdentifiedUser.GenericFactory userFactory;
  private final Provider<AnonymousUser> anonymousUserProvider;
  private final InternalUser.Factory internalUserFactory;

  @Inject
  RequestScopeOperationsImpl(
      ThreadLocalRequestContext localContext,
      AccountCache accountCache,
      AccountOperations accountOperations,
      GenericFactory userFactory,
      Provider<AnonymousUser> anonymousUserProvider,
      InternalUser.Factory internalUserFactory) {
    this.localContext = localContext;
    this.accountCache = accountCache;
    this.accountOperations = accountOperations;
    this.userFactory = userFactory;
    this.anonymousUserProvider = anonymousUserProvider;
    this.internalUserFactory = internalUserFactory;
  }

  @Override
  public ManualRequestContext setNestedApiUser(Account.Id accountId) {
    return new ManualRequestContext(createIdentifiedUser(accountId), localContext);
  }

  @Override
  public void setApiUser(Account.Id accountId) {
    setApiUser(accountOperations.account(accountId).get());
  }

  @Override
  public void setApiUser(TestAccount testAccount) {
    setApiUser(createIdentifiedUser(testAccount.accountId()));
  }

  @Override
  public void resetCurrentApiUser() {
    RequestContext currentContext = localContext.getContext();
    checkState(
        currentContext != null, "can only reset IdentifiedUser, but the RequestContext is null");
    CurrentUser user = localContext.getContext().getUser();
    // More special cases for anonymous users etc. can be added as needed.
    checkState(user.isIdentifiedUser(), "can only reset IdentifiedUser, not %s", user);
    setApiUser(user.getAccountId());
  }

  @Override
  public void setApiUserAnonymous() {
    setApiUser(anonymousUserProvider.get());
  }

  @Override
  public void setApiUserInternal() {
    setApiUser(internalUserFactory.create());
  }

  private void setApiUser(CurrentUser newUser) {
    RequestContext oldContext = localContext.getContext();
    if (oldContext instanceof ManualRequestContext) {
      ((ManualRequestContext) oldContext).close();
    }
    var unused = new ManualRequestContext(newUser, localContext);
  }

  private IdentifiedUser createIdentifiedUser(Account.Id accountId) {
    return userFactory.create(
        accountCache
            .get(requireNonNull(accountId))
            .orElseThrow(
                () -> new IllegalArgumentException("account does not exist: " + accountId)));
  }
}
