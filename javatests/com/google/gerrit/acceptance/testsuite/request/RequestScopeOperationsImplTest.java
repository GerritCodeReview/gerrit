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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.account.TestAccount;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.Sequences;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.junit.Test;

public class RequestScopeOperationsImplTest extends AbstractDaemonTest {
  @Inject private AccountOperations accountOperations;
  @Inject private Provider<IdentifiedUser> userProvider;
  @Inject private RequestScopeOperationsImpl requestScopeOperations;
  @Inject private Sequences sequences;

  @Test
  public void setApiUserToExistingUserById() throws Exception {
    checkApiUser(admin.getId());
    AcceptanceTestRequestScope.Context oldCtx = requestScopeOperations.setApiUser(user.getId());
    assertThat(oldCtx.getUser().getAccountId()).isEqualTo(admin.getId());
    checkApiUser(user.getId());
  }

  @Test
  public void setApiUserToExistingUserByTestAccount() throws Exception {
    checkApiUser(admin.getId());
    TestAccount testAccount =
        accountOperations.account(accountOperations.newAccount().create()).get();
    AcceptanceTestRequestScope.Context oldCtx = requestScopeOperations.setApiUser(testAccount);
    assertThat(oldCtx.getUser().getAccountId()).isEqualTo(admin.getId());
    checkApiUser(testAccount.accountId());
  }

  @Test
  public void setApiUserToNonExistingUser() throws Exception {
    checkApiUser(admin.getId());
    try {
      requestScopeOperations.setApiUser(new Account.Id(sequences.nextAccountId()));
      assert_().fail("expected RuntimeException");
    } catch (RuntimeException e) {
      // Expected.
    }
    checkApiUser(admin.getId());
  }

  private void checkApiUser(Account.Id expected) throws Exception {
    // Test all supported ways that an acceptance test might query the active user.
    assertThat(gApi.accounts().self().get()._accountId)
        .named("user from GerritApi")
        .isEqualTo(expected.get());
    assertThat(userProvider.get().isIdentifiedUser())
        .named("user from provider is an IdentifiedUser")
        .isTrue();
    assertThat(userProvider.get().getAccountId()).named("user from provider").isEqualTo(expected);
    AcceptanceTestRequestScope.Context ctx = atrScope.get();
    assertThat(ctx.getUser().isIdentifiedUser())
        .named("user from AcceptanceTestRequestScope.Context is an IdentifiedUser")
        .isTrue();
    assertThat(ctx.getUser().getAccountId())
        .named("user from AcceptanceTestRequestScope.Context")
        .isEqualTo(expected);
  }
}
