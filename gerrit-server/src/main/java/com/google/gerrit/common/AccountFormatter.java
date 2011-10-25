// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.common;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.inject.Inject;

public class AccountFormatter {

  public interface Factory {
    AccountFormatter create();
  }

  private final AccountCache accountCache;

  @Inject
  public AccountFormatter(final AccountCache accountCache) {
    this.accountCache = accountCache;
  }

  public String format(final Account.Id who) {
    final AccountState state = accountCache.get(who);
    if (state != null) {
      return format(state.getAccount());
    } else {
      return who.toString();
    }
  }

  public String format(final Account a) {
    return format(a, a.getId());
  }

  public String format(final Account a, final Object fallback) {
    if (a.getFullName() != null && !a.getFullName().isEmpty()) {
      return a.getFullName();
    }

    if (a.getPreferredEmail() != null && !a.getPreferredEmail().isEmpty()) {
      return a.getPreferredEmail();
    }

    if (a.getUserName() != null && a.getUserName().isEmpty()) {
      return a.getUserName();
    }

    return fallback.toString();
  }
}
