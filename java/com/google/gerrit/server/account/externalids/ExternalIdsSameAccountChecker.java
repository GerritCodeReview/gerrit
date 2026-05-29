// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.server.account.externalids;

import static com.google.common.base.Preconditions.checkState;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;

/** Static utilities for checking that all specified external IDs belong to the same account. */
public final class ExternalIdsSameAccountChecker {
  private ExternalIdsSameAccountChecker() {}

  /**
   * Checks that all specified external IDs belong to the same account.
   *
   * @return the ID of the account to which all specified external IDs belong.
   */
  public static Account.Id checkSameAccount(Iterable<ExternalId> extIds) {
    return checkSameAccount(extIds, null);
  }

  /**
   * Checks that all specified external IDs belong to specified account. If no account is specified
   * it is checked that all specified external IDs belong to the same account.
   *
   * @return the ID of the account to which all specified external IDs belong.
   */
  @CanIgnoreReturnValue
  public static Account.Id checkSameAccount(
      Iterable<ExternalId> extIds, @Nullable Account.Id accountId) {
    for (ExternalId extId : extIds) {
      if (accountId == null) {
        accountId = extId.accountId();
        continue;
      }
      checkState(
          accountId.equals(extId.accountId()),
          "external id %s belongs to account %s, but expected account %s",
          extId.key().get(),
          extId.accountId().get(),
          accountId.get());
    }
    return accountId;
  }
}
