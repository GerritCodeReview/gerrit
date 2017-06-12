// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;

/** Result from {@link AccountManager#authenticate(AuthRequest)}. */
public class AuthResult {
  private final Account.Id accountId;
  private final AccountExternalId.Key externalId;
  private final boolean isNew;

  public AuthResult(
      final Account.Id accountId, final AccountExternalId.Key externalId, final boolean isNew) {
    this.accountId = accountId;
    this.externalId = externalId;
    this.isNew = isNew;
  }

  /** Identity of the user account that was authenticated into. */
  public Account.Id getAccountId() {
    return accountId;
  }

  /** External identity used to authenticate the user. */
  public AccountExternalId.Key getExternalId() {
    return externalId;
  }

  /**
   * True if this account was recently created for the user.
   *
   * <p>New users should be redirected to the registration screen, so they can configure their new
   * user account.
   */
  public boolean isNew() {
    return isNew;
  }
}
