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

import com.google.common.base.MoreObjects;
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
  public String status;
  public Boolean inactive;

  public AccountInfo(Integer id) {
    this._accountId = id;
  }

  /** To be used ONLY in connection with unregistered reviewers and CCs. */
  public AccountInfo(String name, String email) {
    this.name = name;
    this.email = email;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof AccountInfo) {
      AccountInfo accountInfo = (AccountInfo) o;
      return Objects.equals(_accountId, accountInfo._accountId)
          && Objects.equals(name, accountInfo.name)
          && Objects.equals(email, accountInfo.email)
          && Objects.equals(secondaryEmails, accountInfo.secondaryEmails)
          && Objects.equals(username, accountInfo.username)
          && Objects.equals(avatars, accountInfo.avatars)
          && Objects.equals(_moreAccounts, accountInfo._moreAccounts)
          && Objects.equals(status, accountInfo.status);
    }
    return false;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("id", _accountId)
        .add("name", name)
        .add("email", email)
        .add("username", username)
        .toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        _accountId, name, email, secondaryEmails, username, avatars, _moreAccounts, status);
  }

  protected AccountInfo() {}
}
