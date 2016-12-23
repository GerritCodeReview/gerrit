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

package com.google.gerrit.extensions.common;

import java.util.List;
import java.util.Objects;

public class AccountInfo {
  public Integer _accountId;
  public String name;
  public String email;
  public List<String> secondaryEmails;
  public String username;
  public List<AvatarInfo> avatars;
  public Boolean _moreAccounts;

  public AccountInfo(Integer id) {
    this._accountId = id;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }

    AccountInfo accountInfo = (AccountInfo) o;

    if (Objects.equals(this._accountId, accountInfo._accountId)
        && Objects.equals(this.name, accountInfo.name)
        && Objects.equals(this.email, accountInfo.email)
        && Objects.equals(this.secondaryEmails, accountInfo.secondaryEmails)
        && Objects.equals(this.username, accountInfo.username)
        && Objects.equals(this._moreAccounts, accountInfo._moreAccounts)
        && Objects.equals(this.avatars, accountInfo.avatars)
        && Objects.equals(this.secondaryEmails, accountInfo.secondaryEmails)) {
      return true;
    } else {
      return false;
    }
  }
}
