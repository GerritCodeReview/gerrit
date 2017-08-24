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
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.reviewdb.client.Account;
import java.util.List;

public class AccountAssert {

  public static void assertAccountInfo(TestAccount a, AccountInfo ai) {
    assertThat(a.id.get()).isEqualTo(ai._accountId);
    assertThat(a.fullName).isEqualTo(ai.name);
    assertThat(a.email).isEqualTo(ai.email);
  }

  public static void assertAccountInfos(List<TestAccount> expected, List<AccountInfo> actual) {
    Iterable<Account.Id> expectedIds = TestAccount.ids(expected);
    Iterable<Account.Id> actualIds = Iterables.transform(actual, a -> new Account.Id(a._accountId));
    assertThat(actualIds).containsExactlyElementsIn(expectedIds).inOrder();
    for (int i = 0; i < expected.size(); i++) {
      AccountAssert.assertAccountInfo(expected.get(i), actual.get(i));
    }
  }
}
