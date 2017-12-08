// Copyright (C) 2017 The Android Open Source Project
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

import com.google.auto.value.AutoValue;
import com.google.gerrit.reviewdb.client.Account;

/**
 * Update for an account.
 *
 * <p>Allows to read the current state of an account and to prepare updates to it.
 */
@AutoValue
public abstract class AccountUpdate {
  static AccountUpdate create(Account account) {
    return new AutoValue_AccountUpdate(account, InternalAccountUpdate.builder());
  }

  /**
   * Returns the account that is being updated.
   *
   * <p>Use the returned account only to read the current state of the account. Don't do updates to
   * the returned account. For updates use {@link #update()}.
   *
   * @return the account that is being updated
   */
  public abstract Account account();

  /**
   * Returns a builder to prepare updates to the account.
   *
   * @return account update builder
   */
  public abstract InternalAccountUpdate.Builder update();

  InternalAccountUpdate buildUpdate() {
    return update().build();
  }
}
