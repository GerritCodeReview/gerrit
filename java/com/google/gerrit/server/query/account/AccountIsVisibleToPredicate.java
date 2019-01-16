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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.index.query.IsVisibleToPredicate;
import com.google.gerrit.server.account.AccountControl;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.index.IndexUtils;

public class AccountIsVisibleToPredicate extends IsVisibleToPredicate<AccountState> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  protected final AccountControl accountControl;

  public AccountIsVisibleToPredicate(AccountControl accountControl) {
    super(AccountQueryBuilder.FIELD_VISIBLETO, IndexUtils.describe(accountControl.getUser()));
    this.accountControl = accountControl;
  }

  @Override
  public boolean match(AccountState accountState) {
    boolean canSee = accountControl.canSee(accountState);
    if (!canSee) {
      logger.atFine().log("Filter out non-visisble account: %s", accountState);
    }
    return canSee;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
