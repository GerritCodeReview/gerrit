// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.extensions.events;

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountCache;
import com.google.inject.Inject;

public class EventUtil {

  private final AccountCache accountCache;

  @Inject
  EventUtil(AccountCache accountCache) {
    this.accountCache = accountCache;
  }

  public AccountInfo accountInfo(Account a) {
    if (a == null || a.getId() == null) {
      return null;
    }
    AccountInfo ai = new AccountInfo(a.getId().get());
    ai.email = a.getPreferredEmail();
    ai.name = a.getFullName();
    ai.username = a.getUserName();
    return ai;
  }

  public AccountInfo accountInfo(Account.Id accountId) {
    return accountInfo(accountCache.get(accountId).getAccount());
  }
}
