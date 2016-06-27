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

import com.google.common.base.Strings;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.account.AccountField;

public class UsernamePredicate extends IndexPredicate<AccountState> {
  UsernamePredicate(String username) {
    super(AccountField.USERNAME, AccountQueryBuilder.FIELD_USERNAME, username);
  }

  @Override
  public boolean match(AccountState accountState) {
    return getValue().toLowerCase()
        .equals(Strings.nullToEmpty(accountState.getUserName()).toLowerCase());
  }

  @Override
  public int getCost() {
    return 1;
  }
}
