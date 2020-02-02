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

/**
 * Representation of an account in the REST API.
 *
 * <p>This class determines the JSON format of accounts in the REST API.
 *
 * <p>This class defines fields for account properties that are frequently used. Additional fields
 * are defined in {@link AccountDetailInfo}.
 */
public class AccountInfo {
  /** The numeric ID of the account. */
  public Integer _accountId;

  /** The full name of the user. */
  public String name;

  /** The display name of the user. */
  public String displayName;

  /** The preferred email address of the user. */
  public String email;

  /** List of the secondary email addresses of the user. */
  public List<String> secondaryEmails;

  /** The username of the user. */
  public String username;

  /** List of avatars of the user. */
  public List<AvatarInfo> avatars;

  /**
   * Whether the query would deliver more results if not limited. Only set on the last account that
   * is returned as a query result.
   */
  public Boolean _moreAccounts;

  /** Status message of the account (e.g. 'OOO' for out-of-office). */
  public String status;

  /** Whether the account is inactive. */
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
          && Objects.equals(displayName, accountInfo.displayName)
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
        .add("displayname", displayName)
        .add("email", email)
        .add("username", username)
        .toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        _accountId,
        name,
        displayName,
        email,
        secondaryEmails,
        username,
        avatars,
        _moreAccounts,
        status);
  }

  protected AccountInfo() {}
}
