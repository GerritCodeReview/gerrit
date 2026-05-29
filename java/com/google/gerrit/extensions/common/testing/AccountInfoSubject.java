// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.extensions.common.testing;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.ComparableSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.common.AccountInfo;

/** A Truth subject for {@link AccountInfo} instances. */
public class AccountInfoSubject extends Subject {

  private final AccountInfo accountInfo;

  public static AccountInfoSubject assertThat(AccountInfo accountInfo) {
    return assertAbout(accounts()).that(accountInfo);
  }

  public static Factory<AccountInfoSubject, AccountInfo> accounts() {
    return AccountInfoSubject::new;
  }

  private AccountInfoSubject(FailureMetadata metadata, AccountInfo accountInfo) {
    super(metadata, accountInfo);
    this.accountInfo = accountInfo;
  }

  public IntegerSubject hasIdThat() {
    return check("id()").that(accountInfo()._accountId);
  }

  public ComparableSubject<Account.Id> hasAccountIdThat() {
    return check("accountId()").that(Account.id(accountInfo()._accountId));
  }

  private AccountInfo accountInfo() {
    isNotNull();
    return accountInfo;
  }
}
