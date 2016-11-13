// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.query.account;

import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountControl;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.query.IsVisibleToPredicate;
import com.google.gerrit.server.query.change.SingleGroupUser;
import com.google.gwtorm.server.OrmException;

public class AccountIsVisibleToPredicate extends IsVisibleToPredicate<AccountState> {
  private static String describe(CurrentUser user) {
    if (user.isIdentifiedUser()) {
      return user.getAccountId().toString();
    }
    if (user instanceof SingleGroupUser) {
      return "group:"
          + user.getEffectiveGroups()
              .getKnownGroups() //
              .iterator()
              .next()
              .toString();
    }
    return user.toString();
  }

  private final AccountControl accountControl;

  AccountIsVisibleToPredicate(AccountControl accountControl) {
    super(AccountQueryBuilder.FIELD_VISIBLETO, describe(accountControl.getUser()));
    this.accountControl = accountControl;
  }

  @Override
  public boolean match(AccountState accountState) throws OrmException {
    return accountControl.canSee(accountState);
  }

  @Override
  public int getCost() {
    return 1;
  }
}
