// Copyright (C) 2022 The Android Open Source Project
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

import com.google.gerrit.index.query.PostFilterPredicate;
import com.google.gerrit.server.account.AccountState;

/**
 * Predicate to filter accounts based on {@code Account#hidden} property.
 *
 * <p>TODO(mariasavtchouk): move this predicate to index
 */
public class HiddenPredicate extends PostFilterPredicate<AccountState> {

  private final boolean hidden;

  HiddenPredicate(boolean hidden) {
    super(AccountQueryBuilder.FIELD_HIDDEN, hidden ? "1" : "0");
    this.hidden = hidden;
  }

  @Override
  public boolean match(AccountState accountState) {
    return accountState.account().isHidden().orElse(false).equals(hidden);
  }

  @Override
  public int getCost() {
    return 1;
  }
}
