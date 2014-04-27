// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.api.accounts;

import com.google.gerrit.extensions.common.AccountInfo;

public class AccountInfoMapper {
  public static AccountInfo fromAcountInfo(
      com.google.gerrit.server.account.AccountInfo i) {
    if (i == null) {
      return null;
    }
    AccountInfo ai = new AccountInfo();
    fromAccount(i, ai);
    return ai;
  }

  public static void fromAccount(
      com.google.gerrit.server.account.AccountInfo i, AccountInfo ai) {
    ai._accountId = i._accountId;
    ai.email = i.email;
    ai.name = i.name;
    ai.username = i.username;
  }
}
