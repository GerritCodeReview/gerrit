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

package com.google.gerrit.server.account;

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.reviewdb.client.Account;

public class AccountJson {

  public static AccountInfo toAccountInfo(Account account) {
    if (account == null || account.getId() == null) {
      return null;
    }
    AccountInfo accountInfo = new AccountInfo(account.getId().get());
    accountInfo.email = account.getPreferredEmail();
    accountInfo.name = account.getFullName();
    accountInfo.username = account.getUserName();
    return accountInfo;
  }
}
