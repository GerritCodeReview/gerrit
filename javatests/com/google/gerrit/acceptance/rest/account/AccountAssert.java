// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.account;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.common.AccountInfo;
import java.util.List;

public class AccountAssert {
  /**
   * Asserts an AccountInfo for an active account.
   *
   * @param testAccount the TestAccount which the provided AccountInfo is expected to match
   * @param accountInfo the AccountInfo that should be asserted
   */
  public static void assertAccountInfo(TestAccount testAccount, AccountInfo accountInfo) {
    assertThat(accountInfo._accountId).isEqualTo(testAccount.id().get());
    assertThat(accountInfo.name).isEqualTo(testAccount.fullName());
    assertThat(accountInfo.displayName).isEqualTo(testAccount.displayName());
    assertThat(accountInfo.email).isEqualTo(testAccount.email());
    assertThat(accountInfo.inactive).isNull();
  }

  /**
   * Asserts an AccountInfos for active accounts.
   *
   * @param expected the TestAccounts which the provided AccountInfos are expected to match
   * @param actual the AccountInfos that should be asserted
   */
  public static void assertAccountInfos(List<TestAccount> expected, List<AccountInfo> actual) {
    Iterable<Account.Id> expectedIds = TestAccount.ids(expected);
    Iterable<Account.Id> actualIds = Iterables.transform(actual, a -> Account.id(a._accountId));
    assertThat(actualIds).containsExactlyElementsIn(expectedIds).inOrder();
    for (int i = 0; i < expected.size(); i++) {
      AccountAssert.assertAccountInfo(expected.get(i), actual.get(i));
    }
  }
}
